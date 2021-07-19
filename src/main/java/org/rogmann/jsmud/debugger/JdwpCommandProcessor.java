package org.rogmann.jsmud.debugger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.Type;
import org.rogmann.jsmud.ClassRegistry;
import org.rogmann.jsmud.MethodFrame;
import org.rogmann.jsmud.VM;
import org.rogmann.jsmud.datatypes.VMArrayID;
import org.rogmann.jsmud.datatypes.VMArrayRegion;
import org.rogmann.jsmud.datatypes.VMBoolean;
import org.rogmann.jsmud.datatypes.VMByte;
import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMClassLoaderID;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMFrameID;
import org.rogmann.jsmud.datatypes.VMInt;
import org.rogmann.jsmud.datatypes.VMInterfaceID;
import org.rogmann.jsmud.datatypes.VMLong;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMObjectID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;
import org.rogmann.jsmud.datatypes.VMShort;
import org.rogmann.jsmud.datatypes.VMString;
import org.rogmann.jsmud.datatypes.VMStringID;
import org.rogmann.jsmud.datatypes.VMTaggedObjectId;
import org.rogmann.jsmud.datatypes.VMThreadGroupID;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.datatypes.VMVoid;
import org.rogmann.jsmud.events.JdwpEventRequest;
import org.rogmann.jsmud.events.JdwpModifierClassMatch;
import org.rogmann.jsmud.events.JdwpModifierClassOnly;
import org.rogmann.jsmud.events.JdwpModifierCount;
import org.rogmann.jsmud.events.JdwpModifierLocationOnly;
import org.rogmann.jsmud.events.JdwpModifierStep;
import org.rogmann.jsmud.events.JdwpModifierThreadOnly;
import org.rogmann.jsmud.events.ModKind;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.replydata.RefFieldBean;
import org.rogmann.jsmud.replydata.RefFrameBean;
import org.rogmann.jsmud.replydata.RefMethodBean;
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.replydata.VariableSlot;

/**
 * Processor for receiving and sending jwdp-commands.
 */
public class JdwpCommandProcessor implements DebuggerInterface {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JdwpCommandProcessor.class);
	
	/** Buffer containing Handshake-eyecatcher "JDWP-Handshake" */
	private static final byte[] BUF_HANDSHAKE = "JDWP-Handshake".getBytes(StandardCharsets.US_ASCII);
	
	/** length of command-header or reply-header */
	private final int HEADER_LEN = 11;
	
	/** incoming commands */
	private final InputStream is;
	
	/** outgoing commands */
	private final OutputStream os;

	/** VM */
	private final VM vm;

	/** incoming buffer */
	private final byte[] fBufIn = new byte[1024];

	/** outgoing buffer */
	private final byte[] fBufOut = new byte[1024];
	
	/** <code>true</code> if the debugging should stop */
	private final AtomicBoolean SHOULD_STOP = new AtomicBoolean();
	
	/** id-counter for outgoing commands */
	private int idOutCounter = 0;

	/** id-counter for event-requests */
	private int eventRequestCounter = 0;

	/** debugger-visitor */
	private final DebuggerJvmVisitor visitor;

	
	/**
	 * Constructor
	 * @param is Inputstream
	 * @param os Outputstream
	 * @param visitor Debugger-Visitor
	 * @param classLoader class-loader
	 * @throws IOException in case of an IO-error
	 */
	public JdwpCommandProcessor(final InputStream is, final OutputStream os,
			final VM vm, final DebuggerJvmVisitor visitor) throws IOException {
		this.is = is;
		this.os = os;
		this.vm = vm;
		this.visitor = visitor;
		visitor.setDebugger(this);
		read(fBufIn, 0, BUF_HANDSHAKE.length);
		for (int i = 0; i < BUF_HANDSHAKE.length; i++) {
			if (fBufIn[i] != BUF_HANDSHAKE[i]) {
				throw new IllegalArgumentException("Unexpected jwdp-Eyecatcher: "
						+ Arrays.toString(Arrays.copyOfRange(fBufIn, 0, BUF_HANDSHAKE.length)));
			}
		}
		os.write(BUF_HANDSHAKE, 0, BUF_HANDSHAKE.length);
		final VMThreadID threadId = vm.getCurrentThreadId();
		if (threadId == null) {
			throw new IllegalStateException("The current thread isn't registered.");
		}
		sendVMEvent(JdwpSuspendPolicy.ALL, VMEventType.VM_START, new VMInt(0), threadId);
		LOG.info(JdwpCommandProcessor.class + ": initialized");
	}

	/** {@inheritDoc} */
	@Override
	public void processPackets() throws IOException {
		LOG.debug(JdwpCommandProcessor.class + ": processPackets");
		final VMThreadID threadId = vm.getCurrentThreadId();
		if (threadId == null) {
			throw new IllegalStateException("The current thread isn't registered.");
		}
		boolean showTimeout = true;
		while (!SHOULD_STOP.get()) {
			final int packetLen;
			try {
				packetLen = readPacket();
			}
			catch (SocketTimeoutException e) {
				// The debugger didn't respond in time.
				// Resume?
				final Integer suspendCount = vm.getSuspendCount(threadId);
				if (suspendCount != null && suspendCount.intValue() <= 0) {
					// We can resume the thread.
					LOG.debug("Back to the thread " + threadId);
					break;
				}
				if (showTimeout) {
					LOG.debug(String.format("Debugger timeout ... (suspendCount=%d)", suspendCount));
					showTimeout = false;
				}
				continue;
			}
			final CommandBuffer cmdBuf = new CommandBuffer(fBufIn, 0, packetLen);
			LOG.debug("RangeIn: " + printHexBinary(fBufIn, 0, packetLen));
			try {
				visitor.setIsProcessingPackets(true);
				processPacket(threadId, cmdBuf);
			}
			finally {
				visitor.setIsProcessingPackets(false);
			}
			showTimeout = true;
		}
	}

	/**
	 * Processes a jdwp-packet (request or response).
	 * @param threadId current thread
	 * @param cmdBuf command-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void processPacket(final VMThreadID threadId, final CommandBuffer cmdBuf) throws IOException {
		final int len = cmdBuf.readInt();
		final int id = cmdBuf.readInt();
		final byte packetType = cmdBuf.readByte();
		if (packetType == 0) {
			// command-packet
			// 0000   00 00 00 0b 00 00 02 54 00 01 07
			final byte bCs = cmdBuf.readByte();
			final byte bCmd = cmdBuf.readByte();
			final JdwpCommandSet cs = JdwpCommandSet.lookupByKind(bCs);
			final JdwpCommand cmd = JdwpCommand.lookupByKind(cs, bCmd);
			LOG.debug(String.format("Command %d: %d/%d, %s/%s, len=%d",
					Integer.valueOf(id),
					Byte.valueOf(bCs), Byte.valueOf(bCmd),
					cs, cmd,
					Integer.valueOf(len)));
			if (cs == JdwpCommandSet.VIRTUAL_MACHINE) {
				processCommandVirtualMachine(id, cmd, cmdBuf, threadId);
			}
			else if (cs == JdwpCommandSet.REFERENCE_TYPE) {
				processCommandReferenceType(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.CLASS_TYPE) {
				processCommandClassType(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.METHOD) {
				processCommandMethod(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.OBJECT_REFERENCE) {
				processCommandObjectReference(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.STRING_REFERENCE) {
				processCommandStringReference(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.THREAD_REFERENCE) {
				processCommandThreadReference(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.THREAD_GROUP_REFERENCE) {
				processCommandThreadGroupReference(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.ARRAY_REFERENCE) {
				processCommandArrayReference(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.EVENT_REQUEST) {
				processCommandEventRequest(id, cmd, cmdBuf);
			}
			else if (cs == JdwpCommandSet.STACK_FRAME) {
				processCommandStackFrame(id, cmd, cmdBuf, threadId);
			}
			else {
				sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
			}
		}
		else if (packetType == 0x80) {
			final short errorCode = cmdBuf.readShort();
			LOG.debug(String.format("Reply %d: error=%d",
					Integer.valueOf(id),
					Short.valueOf(errorCode), Integer.valueOf(len)));
			// reply-packet
		}
	}

	/**
	 * Processes a command in the command-set VirtualMachine.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @param threadId VM-thread-id of the current thread
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandVirtualMachine(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf,
			final VMThreadID threadId) throws IOException {
		if (cmd == JdwpCommand.VERSION) {
			String description = ClassRegistry.VERSION;
			int jdwpMajor = 1;
			int jdwpMinor = 8;
			String vmVersion = System.getProperty("java.version");
			String vmName = System.getProperty("java.vm.name");
			sendReplyData(id, new VMString(description),
					new VMInt(jdwpMajor), new VMInt(jdwpMinor),
					new VMString(vmVersion), new VMString(vmName));
		}
		else if (cmd == JdwpCommand.CLASSES_BY_SIGNATURE) {
			final int signLen = cmdBuf.readInt();
			final String signature = cmdBuf.readString(signLen);
			searchClassesBySignature(id, signature);
		}
		else if (cmd == JdwpCommand.ALL_THREADS) {
			final VMInt numThreads = new VMInt(1);
			sendReplyData(id, numThreads, threadId);
		}
		else if (cmd == JdwpCommand.TOP_LEVEL_THREAD_GROUPS) {
			// We send the current thread-group only.
			final VMThreadGroupID groupId = vm.getCurrentThreadGroupId(Thread.currentThread());
			sendReplyData(id, new VMInt(1), groupId);
		}
		else if (cmd == JdwpCommand.DISPOSE) {
			LOG.info("Dispose: Close communication channel");
			visitor.cancelAllEvents();
			SHOULD_STOP.set(true);
			return;
		}
		else if (cmd == JdwpCommand.IDSIZES) {
			sendReplyData(id, new VMInt(8), new VMInt(8), new VMInt(8), new VMInt(8), new VMInt(8));
		}
		else if (cmd == JdwpCommand.SUSPEND) {
			LOG.debug("VM-Suspend ...");
			vm.suspend();
			sendReplyData(id);
		}
		else if (cmd == JdwpCommand.RESUME) {
			LOG.debug("VM-Resume ...");
			vm.resume();
			sendReplyData(id);
		}
		else if (cmd == JdwpCommand.CREATE_STRING) {
			final int len = cmdBuf.readInt();
			final String utf8 = cmdBuf.readString(len);
			final VMStringID stringId = vm.createString(utf8);
			sendReplyData(id, stringId);
		}
		else if (cmd == JdwpCommand.CLASS_PATHS) {
			// We send neither classpaths nor bootclasspaths.
			final String baseDir = "/";
			final int classpaths = 0;
			final int bootclasspaths = 0;
			sendReplyData(id, new VMString(baseDir), new VMInt(classpaths), new VMInt(bootclasspaths));
		}
		else if (cmd == JdwpCommand.CAPABILITIES_NEW) {
			sendCapabilitiesNew(id);
		}
		else if (cmd == JdwpCommand.ALL_CLASSES_WITH_GENERIC) {
			sendAllClassesWithGeneric(id);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set ReferenceType.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandReferenceType(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf)
			throws IOException {
		final VMReferenceTypeID refType = new VMReferenceTypeID(cmdBuf.readLong());
		if (cmd == JdwpCommand.SIGNATURE) {
			sendReferenceTypeSignature(id, refType);
		}
		else if (cmd == JdwpCommand.CLASS_LOADER) {
			sendReferenceTypeClassLoader(id, refType);
		}
		else if (cmd == JdwpCommand.MODIFIERS) {
			sendReferenceTypeModifiers(id, refType);
		}
		else if (cmd == JdwpCommand.SOURCE_FILE) {
			sendReferenceTypeSourceFile(id, refType);
		}
		else if (cmd == JdwpCommand.INTERFACES) {
			sendReferenceTypeInterfaces(id, refType);
		}
		else if (cmd == JdwpCommand.SIGNATURE_WITH_GENERIC) {
			sendSignatureWithGeneric(id, refType);
		}
		else if (cmd == JdwpCommand.FIELDS_WITH_GENERIC) {
			sendFieldsWithGeneric(id, refType);
		}
		else if (cmd == JdwpCommand.METHODS_WITH_GENERIC) {
			sendMethodsWithGeneric(id, refType);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set ClassType.
	 * @param id request-id
	 * @param cmd command
	 * @param bufCmd request-buffer
	 * @param offset offset of out-data in buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandClassType(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf)
			throws IOException {
		if (cmd == JdwpCommand.SUPERCLASS) {
			final VMClassID classId = new VMClassID(cmdBuf.readLong());
			final VMClassID superClassId = vm.getSuperClass(classId);
			if (superClassId != null) {
				sendReplyData(id, superClassId);
			}
			else {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set Method.
	 * @param id request-id
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandMethod(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf)
			throws IOException {
		if (cmd == JdwpCommand.LINE_TABLE) {
			final VMReferenceTypeID refType = new VMReferenceTypeID(cmdBuf.readLong());
			final VMMethodID methodID = new VMMethodID(cmdBuf.readLong());
			sendLineTable(id, refType, methodID);
		}
		else if (cmd == JdwpCommand.VARIABLE_TABLE_WITH_GENERICS) {
			final VMReferenceTypeID refType = new VMReferenceTypeID(cmdBuf.readLong());
			final VMMethodID methodID = new VMMethodID(cmdBuf.readLong());
			sendVariableTableWithGenerics(id, refType, methodID);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set ObjectReference.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandObjectReference(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf)
			throws IOException {
		if (cmd == JdwpCommand.REFERENCE_TYPE) {
			final VMObjectID cObjectId = new VMObjectID(cmdBuf.readLong());
			LOG.debug("ObjectId in RT: " + cObjectId);
			final Object vmObject = vm.getVMObject(cObjectId);
			if (vmObject == null) {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
			else {
				final Class<? extends Object> objClass = vmObject.getClass();
				// FIXME Get ref-types of JRE-objects
				final RefTypeBean refType = vm.getClassRefTypeBean(objClass);
				sendReplyData(id, new VMByte(refType.getTypeTag().getTag()), refType.getTypeID());
			}
		}
		else if (cmd == JdwpCommand.GET_VALUES) {
			final VMObjectID cObjectId = new VMObjectID(cmdBuf.readLong());
			LOG.debug("ObjectId in RT/get: " + cObjectId);
			final Object vmObject = vm.getVMObject(cObjectId);
			if (vmObject == null) {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
			else {
				final int numFields = cmdBuf.readInt();
				final List<VMFieldID> listFields = new ArrayList<>(numFields);
				for (int i = 0; i < numFields; i++) {
					listFields.add(new VMFieldID(cmdBuf.readLong()));
				}
				final List<VMValue> listValues = vm.readObjectFieldValues(vmObject, listFields);
				if (listValues.size() < numFields) {
					sendError(id, JdwpErrorCode.INVALID_FIELDID);
				}
				else {
					final VMDataField[] dfFields = new VMDataField[1 + numFields];
					dfFields[0] = new VMInt(numFields);
					for (int i = 0; i < numFields; i++) {
						dfFields[1 + i] = listValues.get(i);
					}
					sendReplyData(id, dfFields);
				}
			}
		}
		else if (cmd == JdwpCommand.INVOKE_METHOD) {
			final VMObjectID cObjectId = new VMObjectID(cmdBuf.readLong());
			final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
			final VMClassID classId = new VMClassID(cmdBuf.readLong());
			final VMMethodID methodId = new VMMethodID(cmdBuf.readLong());
			
			final Object oObject = vm.getVMObject(cObjectId);
			final Object oThread = vm.getVMObject(threadId);
			final Object oClass = vm.getVMObject(classId);
			final Object oMethod = vm.getVMObject(methodId);
			if (oObject == null) {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
			else if (!(oThread instanceof Thread)) {
				sendError(id, JdwpErrorCode.INVALID_THREAD);
			}
			else if (!threadId.equals(vm.getCurrentThreadId())) {
				// We support the current thread only.
				sendError(id, JdwpErrorCode.THREAD_NOT_SUSPENDED);
			}
			else if (!(oClass instanceof Class)) {
				sendError(id, JdwpErrorCode.INVALID_CLASS);
			}
			else if (!(oMethod instanceof Method)) {
				sendError(id, JdwpErrorCode.INVALID_METHODID);
			}
			else {
				final int nArgs = cmdBuf.readInt();
				final List<SlotValue> values = new ArrayList<>(nArgs);
				readValues(cmdBuf, nArgs, values);
				final int options = cmdBuf.readInt();
				LOG.debug(String.format("executeMethod: method=%s, options=%d, args=%s",
						((Method) oMethod).getName(), Integer.valueOf(options),
						values));
				final VMDataField[] listValueAndException = vm.executeMethod(cObjectId, classId, methodId, values);
				sendReplyData(id, listValueAndException);
			}
		}
		else if (cmd == JdwpCommand.IS_COLLECTED) {
			final VMObjectID cObjectId = new VMObjectID(cmdBuf.readLong());
			final Object vmObject = vm.getVMObject(cObjectId);
			if (vmObject == null) {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
			else {
				// The objects known by us are not collected.
				final boolean isCollected = false;
				sendReplyData(id, new VMBoolean(isCollected));
			}
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set StringReference.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandStringReference(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf)
			throws IOException {
		if (cmd == JdwpCommand.STRING_REFERENCE_VALUE) {
			final VMObjectID cObjectId = new VMObjectID(cmdBuf.readLong());
			final Object vmObject = vm.getVMObject(cObjectId);
			if (vmObject == null) {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
			else if (!(vmObject instanceof String)) {
				sendError(id, JdwpErrorCode.INVALID_STRING);
			}
			else {
				final String sValue = (String) vmObject;
				sendReplyData(id, new VMString(sValue));
			}
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set ThreadReference.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandThreadReference(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf) throws IOException {
		final VMThreadID cThreadId = new VMThreadID(cmdBuf.readLong());
		final Object oThread = vm.getVMObject(cThreadId);
		if (oThread == null) {
			sendError(id, JdwpErrorCode.INVALID_THREAD);
		}
		else if (!(oThread instanceof Thread)) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else {
			final Thread thread = (Thread) oThread;
			if (cmd == JdwpCommand.THREAD_NAME) {
				sendReplyData(id, new VMString(thread.getName()));
			}
			else if (cmd == JdwpCommand.THREAD_SUSPEND) {
				final boolean isKnown = vm.suspendThread(cThreadId);
				if (isKnown) {
					sendReplyData(id);	
				}
				else {
					sendError(id, JdwpErrorCode.INVALID_THREAD);
				}
			}
			else if (cmd == JdwpCommand.THREAD_RESUME) {
				final boolean isKnown = vm.resumeThread(cThreadId);
				if (isKnown) {
					sendReplyData(id);	
				}
				else {
					sendError(id, JdwpErrorCode.INVALID_THREAD);	
				}
			}
			else if (cmd == JdwpCommand.THREAD_STATUS) {
				// We have a running suspended thread.
				final VMTaggedObjectId taggedMonitorId = vm.getCurrentContentedMonitor(thread);
				final VMDataField valMonitorId = taggedMonitorId.getValue();
				final int running;
				if (!(valMonitorId instanceof VMObjectID)) {
					running = 0; // Strange object-id. We choose zombie ;-).
				}
				else {
					VMObjectID vmObjId = (VMObjectID) valMonitorId;
					if (vmObjId.getValue() != 0L) {
						// There is a monitor we are waiting for.
						running = 3;
					}
					else {
						running = 1;
					}
				}
				final int suspended = 1;
				sendReplyData(id, new VMInt(running), new VMInt(suspended));
			}
			else if (cmd == JdwpCommand.THREAD_THREAD_GROUP) {
				final VMThreadGroupID threadGroupId = vm.getCurrentThreadGroupId(thread);
				sendReplyData(id, threadGroupId);
			}
			else if (cmd == JdwpCommand.THREAD_FRAMES) {
				final int startFrame = cmdBuf.readInt();
				final int length = cmdBuf.readInt();	
				sendThreadFrames(id, cThreadId, startFrame, length);
			}
			else if (cmd == JdwpCommand.THREAD_FRAME_COUNT) {
				List<RefFrameBean> threadFrames = vm.getThreadFrames(cThreadId, 0, -1);
				sendReplyData(id, new VMInt(threadFrames.size()));
			}
			else if (cmd == JdwpCommand.THREAD_OWNED_MONITORS) {
				final List<VMTaggedObjectId> taggedObjectIds = vm.getOwnedMonitors(thread);
				final int num = taggedObjectIds.size();
				final VMInt owned = new VMInt(num);
				final VMDataField[] fields = new VMDataField[1 + num];
				fields[0] = owned;
				for (int i = 0; i < num; i++) {
					fields[1 + i] = taggedObjectIds.get(i);
				}
				sendReplyData(id, fields);
			}
			else if (cmd == JdwpCommand.THREAD_CURRENT_CONTENDED_MONITOR) {
				final VMTaggedObjectId taggedObjectId = vm.getCurrentContentedMonitor(thread);
				sendReplyData(id, taggedObjectId);
			}
			else if (cmd == JdwpCommand.THREAD_INTERRUPT) {
				vm.interrupt(thread);
				sendReplyData(id);
			}
			else if (cmd == JdwpCommand.THREAD_SUSPEND_COUNT) {
				final Integer suspendCount = vm.getSuspendCount(cThreadId);
				if (suspendCount != null) {
					sendReplyData(id, new VMInt(suspendCount.intValue()));
				}
				else {
					sendError(id, JdwpErrorCode.INVALID_THREAD);
				}
			}
			else {
				sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
			}
		}
	}

	/**
	 * Processes a command in the command-set ThreadGroup.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandThreadGroupReference(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf) throws IOException {
		final VMThreadGroupID threadGroupId = vm.getCurrentThreadGroupId(Thread.currentThread());
		if (cmd == JdwpCommand.THREAD_GROUP_NAME) {
			final VMThreadGroupID cThreadGroupId = new VMThreadGroupID(cmdBuf.readLong());
			if (cThreadGroupId.getValue() == threadGroupId.getValue()) {
				final String tgName = Thread.currentThread().getThreadGroup().getName();
				sendReplyData(id, new VMString(tgName));
			}
			else {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
		}
		else if (cmd == JdwpCommand.THREAD_GROUP_PARENT) {
			final VMThreadGroupID cThreadGroupId = new VMThreadGroupID(cmdBuf.readLong());
			if (cThreadGroupId.getValue() == threadGroupId.getValue()) {
				// We are not interested in the thread-group-parent.
				sendReplyData(id, new VMThreadGroupID(0L));
			}
			else {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
		}
		else if (cmd == JdwpCommand.THREAD_GROUP_CHILDREN) {
			final VMThreadGroupID cThreadGroupId = new VMThreadGroupID(cmdBuf.readLong());
			if (cThreadGroupId.getValue() == threadGroupId.getValue()) {
				final int childThreads = 1;
				final int childGroups = 0;
				final VMThreadID curThreadId = vm.getCurrentThreadId();
				sendReplyData(id, new VMInt(childThreads), curThreadId, new VMInt(childGroups));
			}
			else {
				// We accept the thread-group of the current thread only.
				sendError(id, JdwpErrorCode.INVALID_THREAD_GROUP);
			}
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set ArrayReference.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandArrayReference(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf) throws IOException {
		final VMArrayID vmArrayID = new VMArrayID(cmdBuf.readLong());
		LOG.debug("ArrayID: " + vmArrayID);
		final Object objArray = vm.getVMObject(vmArrayID);
		if (objArray == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!objArray.getClass().isArray()) {
			sendError(id, JdwpErrorCode.INVALID_ARRAY);
		}
		else if (cmd == JdwpCommand.ARRAY_LENGTH) {
			final int arrayLength = Array.getLength(objArray);
			sendReplyData(id, new VMInt(arrayLength));
		}
		else if (cmd == JdwpCommand.ARRAY_GET_VALUES) {
			final int firstIndex = cmdBuf.readInt();
			final int length = cmdBuf.readInt();
			final VMArrayRegion values = vm.readArrayValues(objArray, firstIndex, length);
			sendReplyData(id, values);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set EventReques.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandEventRequest(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf) throws IOException {
		if (cmd == JdwpCommand.SET) {
			// 00000019  00 00 00 11 00 00 04 03  00 0f 01 09 00 00 00 00 00
			//           00 00 00 1C 00 00 00 40  00 0F 01 04 01 00 00 00 01 08 000000000000000
			addEventRequest(id, cmdBuf);
		}
		else if (cmd == JdwpCommand.CLEAR) {
			clearEventRequest(id, cmdBuf);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Processes a command in the command-set StackFrame.
	 * @param id request-id
	 * @param cmd command
	 * @param cmdBuf request-buffer
	 * @param threadId VM-thread-id of the current thread
	 * @throws IOException in case of an IO-error while sending a reply
	 */
	private void processCommandStackFrame(final int id, final JdwpCommand cmd, final CommandBuffer cmdBuf,
			final VMThreadID threadId) throws IOException {
		if (cmd == JdwpCommand.STACK_FRAME_GET_VALUES) {
			sendVariableValues(id, threadId, cmdBuf);
		}
		else if (cmd == JdwpCommand.STACK_FRAME_SET_VALUES) {
			setVariableValues(id, threadId, cmdBuf);
		}
		else if (cmd == JdwpCommand.STACK_FRAME_THIS_OBJECT) {
			sendStackFrameThisObject(id, threadId, cmdBuf);
		}
		else {
			sendError(id, JdwpErrorCode.NOT_IMPLEMENTED);
		}
	}

	/**
	 * Send the debuggers capabilities.
	 * @param id id
	 * @throws IOException in case of an iIO-error
	 */
	private void sendCapabilitiesNew(final int id) throws IOException {
		boolean	canWatchFieldModification = false;
		boolean	canWatchFieldAccess	= false;
		boolean	canGetBytecodes	= true;
		boolean	canGetSyntheticAttribute = true;
		boolean	canGetOwnedMonitorInfo = true;
		boolean	canGetCurrentContendedMonitor = true;
		boolean	canGetMonitorInfo = true;
		boolean	canRedefineClasses = false; 
		boolean	canAddMethod = false; 
		boolean	canUnrestrictedlyRedefineClasses = false; 
		boolean	canPopFrames = false; 
		boolean	canUseInstanceFilters = false; 
		boolean	canGetSourceDebugExtension = false; 
		boolean	canRequestVMDeathEvent = false; 
		boolean	canSetDefaultStratum = false; 
		boolean	canGetInstanceInfo = true; 
		boolean	canRequestMonitorEvents = true;
		boolean	canGetMonitorFrameInfo = true; 
		boolean	canUseSourceNameFilters = false; 
		boolean	canGetConstantPool = false; 
		boolean	canForceEarlyReturn = false;
		boolean res22 = false;
		boolean res23 = false;
		boolean res24 = false;
		boolean res25 = false;
		boolean res26 = false;
		boolean res27 = false;
		boolean res28 = false;
		boolean res29 = false;
		boolean res30 = false;
		boolean res31 = false;
		boolean res32 = false;
		sendReplyData(id,
			new VMBoolean(canWatchFieldModification),  
			new VMBoolean(canWatchFieldAccess), 
			new VMBoolean(canGetBytecodes),  
			new VMBoolean(canGetSyntheticAttribute),   
			new VMBoolean(canGetOwnedMonitorInfo),  
			new VMBoolean(canGetCurrentContendedMonitor),  
			new VMBoolean(canGetMonitorInfo),   
			new VMBoolean(canRedefineClasses),  
			new VMBoolean(canAddMethod),  
			new VMBoolean(canUnrestrictedlyRedefineClasses),  
			new VMBoolean(canPopFrames),  
			new VMBoolean(canUseInstanceFilters),  
			new VMBoolean(canGetSourceDebugExtension),  
			new VMBoolean(canRequestVMDeathEvent),  
			new VMBoolean(canSetDefaultStratum),  
			new VMBoolean(canGetInstanceInfo),  
			new VMBoolean(canRequestMonitorEvents),  
			new VMBoolean(canGetMonitorFrameInfo),  
			new VMBoolean(canUseSourceNameFilters),  
			new VMBoolean(canGetConstantPool),  
			new VMBoolean(canForceEarlyReturn),
			new VMBoolean(res22),
			new VMBoolean(res23),
			new VMBoolean(res24),
			new VMBoolean(res25),
			new VMBoolean(res26),
			new VMBoolean(res27),
			new VMBoolean(res28),
			new VMBoolean(res29),
			new VMBoolean(res30),
			new VMBoolean(res31),
			new VMBoolean(res32));
	}

	/**
	 * Sends all loaded classes with generics.
	 * @param id current id
	 * @throws IOException in case of an IO-error
	 */
	private void sendAllClassesWithGeneric(int id) throws IOException {
		final List<RefTypeBean> allClasses = vm.getAllClassesWithGeneric();
		final int numClasses = allClasses.size();
		LOG.debug("SendAllClassesWithGeneric: #classes=" + numClasses);
		final VMDataField[] aReplyData = new VMDataField[1 + numClasses * 5];
		aReplyData[0] = new VMInt(numClasses);
		for (int i = 0; i < numClasses; i++) {
			final RefTypeBean refTypeBean = allClasses.get(i);
			aReplyData[1 + 5 * i] = new VMByte(refTypeBean.getTypeTag().getTag());
			aReplyData[1 + 5 * i + 1] = refTypeBean.getTypeID();
			aReplyData[1 + 5 * i + 2] = new VMString(refTypeBean.getSignature());
			aReplyData[1 + 5 * i + 3] = new VMString(""); // genericSignature
			aReplyData[1 + 5 * i + 4] = new VMInt(refTypeBean.getStatus());
		}
		sendReplyData(id, aReplyData);
	}

	/**
	 * Searches classes by signature
	 * @param id current id
	 * @param signature signature of class
	 * @throws IOException in case of an IO-error
	 */
	private void searchClassesBySignature(final int id, final String signature) throws IOException {
		LOG.debug("ClassesBySignature: " + signature);
	
		final List<RefTypeBean> listRefBeans = vm.getClassesBySignature(signature);
		int numRefBeans = listRefBeans.size();
		if (numRefBeans == 0) {
			sendReplyData(id, new VMInt(0));
		}
		else {
			final VMDataField[] fields = new VMDataField[1 + numRefBeans * 3];
			fields[0] = new VMInt(numRefBeans);
			for (int i = 0; i < numRefBeans; i++) {
				final RefTypeBean refBean = listRefBeans.get(i);
				fields[1 + 3 * i] = new VMByte(refBean.getTypeTag().getTag());
				fields[1 + 3 * i + 1] = refBean.getTypeID();
				fields[1 + 3 * i + 2] = new VMInt(refBean.getStatus());
			}
			sendReplyData(id, fields);
		}
	}

	/**
	 * Sends the modifiers of a reference-type.
	 * @param id request-id
	 * @param refType reference-type-id
	 * @throws IOException in case of an IO-error
	 */
	private void sendReferenceTypeSignature(final int id, final VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final String signature = Type.getDescriptor(classRef);
			LOG.debug("  Signature: " + signature);
			sendReplyData(id, new VMString(signature));
		}
	}

	/**
	 * Sends the class-loader of a reference-type.
	 * @param id request-id
	 * @param refType reference-type-id
	 * @throws IOException in case of an IO-error
	 */
	private void sendReferenceTypeClassLoader(int id, VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final VMClassLoaderID classLoaderId = vm.getClassLoader(classRef);
			sendReplyData(id, classLoaderId);
		}
	}

	/**
	 * Sends the modifiers of a reference-type.
	 * @param id request-id
	 * @param refType reference-type-id
	 * @throws IOException in case of an IO-error
	 */
	private void sendReferenceTypeModifiers(int id, VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final int modbits = classRef.getModifiers();
			sendReplyData(id, new VMInt(modbits));
		}
	}

	/**
	 * Sends the name of a source-file of a reference-type.
	 * @param id request-id
	 * @param refType reference-type-id
	 * @throws IOException in case of an IO-error
	 */
	private void sendReferenceTypeSourceFile(int id, VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final String className = classRef.getName().replaceFirst(".*[.]", "");
			final String classNameOuter = className.replaceFirst("[$].*", "");
			final String sourceFileGuessed = String.format("%s.java", classNameOuter);
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("sendReferenceTypeSourceFile: %s -> (%s)",
						classRef, sourceFileGuessed)); 
			}
			sendReplyData(id, new VMString(sourceFileGuessed));
		}
	}

	/**
	 * Sends the interfaces of a class.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @throws IOException in case of an IO-error
	 */
	private void sendReferenceTypeInterfaces(final int id, final VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final List<VMInterfaceID> listInterfaces = vm.getClassInterfaces(classRef);
			int fnr = 0;
			VMDataField[] fields = new VMDataField[1 + listInterfaces.size()];
			fields[fnr++] = new VMInt(listInterfaces.size());
			for (final VMInterfaceID interfaceId : listInterfaces) {
				fields[fnr++] = interfaceId;
			}
			sendReplyData(id, fields);
		}
	}

	/**
	 * Sends a signature of the given class.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @throws IOException in case of an IO-error
	 */
	private void sendSignatureWithGeneric(final int id, final VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final String signature = Type.getDescriptor(classRef);
			// TODO compute generic
			final String signatureWithGenerics = "";
			sendReplyData(id, new VMString(signature), new VMString(signatureWithGenerics));
		}
	}

	/**
	 * Sends a list of fields of the given class.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @throws IOException in case of an IO-error
	 */
	private void sendFieldsWithGeneric(final int id, final VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			final List<RefFieldBean> listRefFieldBeans = vm.getFieldsWithGeneric(classRef);
			int fnr = 0;
			VMDataField[] fields = new VMDataField[1 + listRefFieldBeans.size() * 5];
			fields[fnr++] = new VMInt(listRefFieldBeans.size());
			for (RefFieldBean refField : listRefFieldBeans) {
				fields[fnr++] = refField.getFieldID();
				fields[fnr++] = new VMString(refField.getName());
				fields[fnr++] = new VMString(refField.getSignature());
				fields[fnr++] = new VMString(refField.getGenericSignature());
				fields[fnr++] = new VMInt(refField.getModBits());
			}
			sendReplyData(id, fields);
		}
	}

	/**
	 * Sends a list of methods of the given class.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @throws IOException in case of an IO-error
	 */
	private void sendMethodsWithGeneric(final int id, final VMReferenceTypeID refType) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else {
			final Class<?> classRef = (Class<?>) oClassRef;
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("sendMethodsWithGeneric: classRef=%s", classRef));
			}
			final List<RefMethodBean> listRefMethodBeans = vm.getMethodsWithGeneric(classRef);
			final int declared = listRefMethodBeans.size();
			VMDataField[] fields = new VMDataField[1 + declared * 5];
			int fnr = 0;
			fields[fnr++] = new VMInt(declared);
			for (RefMethodBean refMethod : listRefMethodBeans) {
				fields[fnr++] = refMethod.getMethodID();
				fields[fnr++] = new VMString(refMethod.getName());
				fields[fnr++] = new VMString(refMethod.getSignature());
				fields[fnr++] = new VMString(refMethod.getGenericSignature());
				fields[fnr++] = new VMInt(refMethod.getModBits());
			}
			sendReplyData(id, fields);
		}
	}

	/**
	 * Sends a line-table.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @param methodID id of the method
	 * @throws IOException in case of an IO-error
	 */
	private void sendLineTable(int id, VMReferenceTypeID refType, VMMethodID methodID) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		final Object oMethod = vm.getVMObject(methodID);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else if (oMethod == null || !(oMethod instanceof Method)) {
			sendError(id, JdwpErrorCode.INVALID_METHODID);
		}
		else {
			final Class<?> clazz = (Class<?>) oClassRef;
			final Method method = (Method) oMethod;
			final List<LineCodeIndex> lineTable = vm.getLineTable(clazz, method, refType, methodID);
			int numLines = lineTable.size();
			long start = (numLines > 0) ? start = Integer.MAX_VALUE : 0;
			long end = 0;
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("sendLineTable: refType=%s, class=%s, method=%s, numLines=%d, start=%d, end=%d",
						refType, clazz, method, Integer.valueOf(numLines),
						Long.valueOf(start), Long.valueOf(end)));
			}
			final VMDataField[] fields = new VMDataField[3 + numLines * 2];
			for (int i = 0; i < numLines; i++) {
				long lci = lineTable.get(i).getLineCodeIndex();
				start = Math.min(lci, start);
				end = Math.max(lci, end);
				fields[3 + 2 * i] = new VMLong(lci);
				fields[4 + 2 * i] = new VMInt(lineTable.get(i).getLineNumber());
			}
			fields[0] = new VMLong(start);
			fields[1] = new VMLong(end);
			fields[2] = new VMInt(numLines);
			sendReplyData(id, fields);
		}
	}

	/**
	 * Sends a variable-table with generics.
	 * @param id request-id
	 * @param refType ref-type-id of class
	 * @param methodID id of the method
	 * @throws IOException in case of an IO-error
	 */
	private void sendVariableTableWithGenerics(int id, VMReferenceTypeID refType, VMMethodID methodID) throws IOException {
		final Object oClassRef = vm.getVMObject(refType);
		final Object oMethod = vm.getVMObject(methodID);
		if (oClassRef == null) {
			sendError(id, JdwpErrorCode.INVALID_OBJECT);
		}
		else if (!(oClassRef instanceof Class)) {
			sendError(id, JdwpErrorCode.INVALID_CLASS);
		}
		else if (oMethod == null || !(oMethod instanceof Method)) {
			sendError(id, JdwpErrorCode.INVALID_METHODID);
		}
		else {
			final Method method = (Method) oMethod;
			int argCnt = 0;
			for (Class<?> paramType : method.getParameterTypes()) {
				if (Long.class.equals(paramType) || Double.class.equals(paramType)) {
					argCnt += 2;
				}
				else {
					argCnt++;
				}
			}
			final List<VariableSlot> slots = vm.getVariableSlots(method);
			int idx = 0;
			final VMDataField[] fields = new VMDataField[2 + slots.size() * 6];
			fields[idx++] = new VMInt(argCnt);
			fields[idx++] = new VMInt(slots.size());
			for (VariableSlot slot : slots) {
				fields[idx++] = new VMLong(slot.getCodeIndex());
				fields[idx++] = new VMString(slot.getName());
				fields[idx++] = new VMString(slot.getSignature());
				fields[idx++] = new VMString(slot.getGenericSignature());
				fields[idx++] = new VMInt(slot.getLength());
				fields[idx++] = new VMInt(slot.getSlot());
			}
			sendReplyData(id, fields);
		}
	}

	/**
	 * Send the values of variables in a method-frame.
	 * @param id request-id
	 * @param threadId id of current thread
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void sendVariableValues(final int id, final VMThreadID threadId, final CommandBuffer cmdBuf)
			throws IOException {
		final VMThreadID cThreadId = new VMThreadID(cmdBuf.readLong());
		final VMFrameID frameId = new VMFrameID(cmdBuf.readLong());
		final int slots = cmdBuf.readInt();
		if (cThreadId.getValue() != threadId.getValue()) {
			sendError(id, JdwpErrorCode.INVALID_THREAD);	
		}
		else {
			final Object oFrame = vm.getVMObject(frameId);
			if (oFrame == null) {
				sendError(id, JdwpErrorCode.INVALID_FRAMEID);
			}
			else if (oFrame instanceof MethodFrame) {
				final MethodFrame methodFrame = (MethodFrame) oFrame;
				final List<SlotRequest> slotRequests = new ArrayList<>(slots);
				for (int i = 0; i < slots; i++) {
					final int slot = cmdBuf.readInt();
					final byte tag = cmdBuf.readByte();
					slotRequests.add(new SlotRequest(slot, tag));
				}
				final List<VMValue> aValues = vm.getVariableValues(methodFrame, slotRequests);
				if (aValues.size() < slots) {
					sendError(id, JdwpErrorCode.INVALID_SLOT);
				}
				else {
					VMDataField[] fields = new VMDataField[1 + slots];
					fields[0] = new VMInt(slots);
					for (int i = 0; i < slots; i++) {
						fields[1 + i] = aValues.get(i);
					}
					sendReplyData(id, fields);
				}
			}
			else {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
		}
	}

	/**
	 * Sends the call-stack of a suspended thread.
	 * @param id request-id
	 * @param cThreadId thread-id
	 * @param startFrame index of first frame
	 * @param length count of frames or -1 (means all remaining)
	 * @throws IOException in case of an IO-error
	 */
	private void sendThreadFrames(int id, VMThreadID cThreadId, int startFrame, int length) throws IOException {
		final List<RefFrameBean> frames = vm.getThreadFrames(cThreadId, startFrame, length);
		final VMDataField[] fields = new VMDataField[1 + frames.size() * 5];
		int fnr = 0;
		fields[fnr++] = new VMInt(frames.size());
		LOG.debug(String.format("sendThreadFrames: threadId=%s, startFrame=%d, length=%d",
				cThreadId, Integer.valueOf(startFrame), Integer.valueOf(length)));
		for (final RefFrameBean refFrameBean : frames) {
			fields[fnr++] = refFrameBean.getFrameId();
			fields[fnr++] = new VMByte(refFrameBean.getTypeTag().getTag());
			fields[fnr++] = refFrameBean.getTypeId();
			fields[fnr++] = refFrameBean.getMethodId();
			fields[fnr++] = new VMLong(refFrameBean.getIndex());
			LOG.debug(String.format("  frame=%s, tag=%s, type=%s, method=%s, idx=%s",
					refFrameBean.getFrameId(), refFrameBean.getTypeTag(),
					refFrameBean.getTypeId(), refFrameBean.getMethodId(),
					Long.valueOf(refFrameBean.getIndex())));
		}
		sendReplyData(id, fields);
	}

	/**
	 * Sets the values of variables in a method-frame.
	 * @param id request-id
	 * @param threadId id of current thread
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void setVariableValues(final int id, final VMThreadID threadId, final CommandBuffer cmdBuf)
			throws IOException {
		final VMThreadID cThreadId = new VMThreadID(cmdBuf.readLong());
		final VMFrameID frameId = new VMFrameID(cmdBuf.readLong());
		final int slots = cmdBuf.readInt();
		if (cThreadId.getValue() != threadId.getValue()) {
			sendError(id, JdwpErrorCode.INVALID_THREAD);	
		}
		else {
			final Object oFrame = vm.getVMObject(frameId);
			if (oFrame == null) {
				sendError(id, JdwpErrorCode.INVALID_FRAMEID);
			}
			else if (oFrame instanceof MethodFrame) {
				final MethodFrame methodFrame = (MethodFrame) oFrame;
				final List<SlotValue> slotVariables = new ArrayList<>(slots);
				readValues(cmdBuf, slots, slotVariables);
				boolean isOk = vm.setVariableValues(methodFrame, slotVariables);
				if (isOk) {
					sendReplyData(id);
				}
				else {
					sendError(id, JdwpErrorCode.INVALID_SLOT);
				}
			}
			else {
				sendError(id, JdwpErrorCode.INVALID_OBJECT);
			}
		}
	}

	/**
	 * Sends the value of the this-reference of the current frame.
	 * @param id request-id
	 * @param threadId id of current thread
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void sendStackFrameThisObject(final int id, final VMThreadID threadId, final CommandBuffer cmdBuf)
			throws IOException {
		final VMThreadID cThreadId = new VMThreadID(cmdBuf.readLong());
		final VMFrameID frameId = new VMFrameID(cmdBuf.readLong());
		if (cThreadId.getValue() != threadId.getValue()) {
			sendError(id, JdwpErrorCode.INVALID_THREAD);	
		}
		else {
			final Object oFrame = vm.getVMObject(frameId);
			if (oFrame == null || !(oFrame instanceof MethodFrame)) {
				sendError(id, JdwpErrorCode.INVALID_FRAMEID);
			}
			else {
				final MethodFrame methodFrame = (MethodFrame) oFrame;
				final VMTaggedObjectId objectId = vm.getThisObject(methodFrame);
				sendReplyData(id, objectId);
			}
		}
	}

	/**
	 * Reads values in a command-buffer.
	 * @param cmdBuf request-buffer
	 * @param slots number of values to be read
	 * @param slotVariables list to be filled
	 */
	static void readValues(final CommandBuffer cmdBuf, final int slots, final List<SlotValue> slotVariables) {
		for (int i = 0; i < slots; i++) {
			final int slot = cmdBuf.readInt();
			final byte tag = cmdBuf.readByte();
			final VMDataField dfValue;
			switch(tag) {
			case 'B':
				dfValue = new VMByte(cmdBuf.readByte());
				break;
			case 'Z':
				final byte b = cmdBuf.readByte();
				dfValue = new VMBoolean(b != (byte) 0);
				break;
			case 'C':
			case 'S':
				final short s = cmdBuf.readShort();
				dfValue = new VMShort(s);
				break;
			case 'I':
			case 'F':
				dfValue = new VMInt(cmdBuf.readInt());
				break;
			case 'J':
			case 'D':
				dfValue = new VMLong(cmdBuf.readLong());
				break;
			case 'V':
				dfValue = new VMVoid();
				break;
			default:
				VMObjectID vmObjectID = new VMObjectID(cmdBuf.readLong());
				dfValue = vmObjectID;
				break;
			}
			slotVariables.add(new SlotValue(slot, new VMValue(tag, dfValue)));
		}
	}

	/**
	 * Processes the setting of an event-request.
	 * @param id id of packet
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void addEventRequest(final int id, final CommandBuffer cmdBuf) throws IOException {
		final byte eventKind = cmdBuf.readByte();
		final VMEventType eventType = VMEventType.lookupByKind(eventKind);
		final byte bSuspendPolicy = cmdBuf.readByte();
		final JdwpSuspendPolicy suspendPolicy = JdwpSuspendPolicy.lookupBySuspendPolicy(bSuspendPolicy);
		if (suspendPolicy == null) {
			throw new DebuggerException(String.format("Unexpected suspend-policy (%d) in event of type (%s)",
					Integer.valueOf(bSuspendPolicy & 0xff), eventType));
		}
		final int modifiers = cmdBuf.readInt();
		if (eventType == VMEventType.SINGLE_STEP
				|| eventType == VMEventType.BREAKPOINT
				|| eventType == VMEventType.EXCEPTION
				|| eventType == VMEventType.CLASS_UNLOAD
				|| eventType == VMEventType.THREAD_START
				|| eventType == VMEventType.THREAD_DEATH
				|| eventType == VMEventType.CLASS_PREPARE
				|| eventType == VMEventType.METHOD_ENTRY
				|| eventType == VMEventType.METHOD_EXIT_WITH_RETURN_VALUE) {
			// e.g. BREAKPOINT: 0000002B000000BE000F0102010000000107010000000000000006000000
			final int requestId = eventRequestCounter++;
			final JdwpEventRequest evReq = new JdwpEventRequest(requestId, eventType, suspendPolicy, modifiers);
			LOG.debug("eventReq: " + eventType + ", mod: " + modifiers);
			for (int i = 0; i < modifiers; i++) {
				final byte bModKind = cmdBuf.readByte();
				final ModKind modKind = ModKind.lookupByKind(bModKind);
				if (modKind == null) {
					throw new DebuggerException(String.format("Unexpected modKind (%d) in event of type (%s)",
							Integer.valueOf(bModKind & 0xff), eventType));
				}
				switch (modKind) {
				case COUNT: // Case Count
					final int count = cmdBuf.readInt();
					LOG.debug("  Case Count: " + count);
					final JdwpModifierCount modCount = new JdwpModifierCount(count);
					evReq.addModifier(modCount);
					break;
				case THREAD_ONLY: // Case ThreadOnly
				{
					final VMThreadID threadID = new VMThreadID(cmdBuf.readLong());
					final Object vmObject = vm.getVMObject(threadID);
					if (vmObject instanceof Thread) {
						final Thread thread = (Thread) vmObject;
						LOG.debug("  Case ThreadOnly: thread=" + thread);
						final JdwpModifierThreadOnly modThread = new JdwpModifierThreadOnly(threadID);
						evReq.addModifier(modThread);
					}
					break;
				}
				case CLASS_ONLY: // Case ClassOnly
				{
					final VMReferenceTypeID classID = new VMReferenceTypeID(cmdBuf.readLong());
					final Object vmObject = vm.getVMObject(classID);
					if (vmObject instanceof Class) {
						final Class<?> clazz = (Class<?>) vmObject;
						LOG.debug("  Case ClassOnly: clazz=" + clazz);
						final JdwpModifierClassOnly modClass = new JdwpModifierClassOnly(classID);
						evReq.addModifier(modClass);
					}
					break;
				}
				case CLASS_MATCH: // Case ClassMatch
				case CLASS_EXCLUDE: // Case ClassExclude
					final int strLen = cmdBuf.readInt();
					final String pattern = cmdBuf.readString(strLen);
					LOG.debug(String.format("  Case %s: %s", modKind, pattern));
					final JdwpModifierClassMatch modMatch = new JdwpModifierClassMatch(modKind, pattern);
					evReq.addModifier(modMatch);
					break;
				case LOCATION_ONLY: // Case Location
					final byte typeTag = cmdBuf.readByte();
					final VMClassID classID = new VMClassID(cmdBuf.readLong());
					final VMMethodID methodId = new VMMethodID(cmdBuf.readLong());
					final long index = cmdBuf.readLong();
					final Object oClass = vm.getVMObject(classID);
					final Object oMethod = vm.getVMObject(methodId);
					LOG.debug(String.format("  Case Location: typeTag=%d, class=%s, method=%s, index=%d",
							Integer.valueOf(typeTag), oClass, oMethod, Long.valueOf(index)));
					final JdwpModifierLocationOnly modLocOnly = new JdwpModifierLocationOnly(typeTag, classID, methodId, index);
					evReq.addModifier(modLocOnly);
					break;
				case EXCEPTION_ONLY: // Case ExceptionOnly
					final VMReferenceTypeID refTypeId = new VMReferenceTypeID(cmdBuf.readLong());
					final boolean caught = (cmdBuf.readByte() != 0);
					final boolean uncaught = (cmdBuf.readByte() != 0);
					LOG.debug(String.format("  Case ExceptionOnly: refTypeId=%s, caught=%s, uncaught=%s",
							refTypeId, Boolean.toString(caught), Boolean.toString(uncaught)));
					break;
				case STEP: // Case Step
					final VMThreadID threadID = new VMThreadID(cmdBuf.readLong());
					final int stepSize = cmdBuf.readInt();
					final int stepDepth = cmdBuf.readInt();
					LOG.debug("  Case Step: ss " + stepSize + ", sd " + stepDepth);
					final JdwpModifierStep modStep = new JdwpModifierStep(threadID, stepSize, stepDepth);
					evReq.addModifier(modStep);
					break;
				default:
					throw new RuntimeException("Unknown modifier kind: " + bModKind);
				}
			}
			visitor.addEventRequest(evReq);
			sendReplyData(id, new VMInt(requestId));
		}
		else {
			sendError(id, JdwpErrorCode.INVALID_EVENT_TYPE);
		}
	}

	/**
	 * Processes the clearing of an event-request.
	 * @param id id of packet
	 * @param cmdBuf request-buffer
	 * @throws IOException in case of an IO-error
	 */
	private void clearEventRequest(final int id, final CommandBuffer cmdBuf) throws IOException {
		final byte eventKind = cmdBuf.readByte();
		final VMEventType eventType = VMEventType.lookupByKind(eventKind);
		if (eventType == VMEventType.SINGLE_STEP
				|| eventType == VMEventType.BREAKPOINT
				|| eventType == VMEventType.EXCEPTION
				|| eventType == VMEventType.CLASS_UNLOAD
				|| eventType == VMEventType.THREAD_START
				|| eventType == VMEventType.THREAD_DEATH
				|| eventType == VMEventType.CLASS_PREPARE
				|| eventType == VMEventType.METHOD_ENTRY
				|| eventType == VMEventType.METHOD_EXIT_WITH_RETURN_VALUE) {
			final int requestId = cmdBuf.readInt();
			visitor.clearEventRequest(eventType, requestId);
			sendReplyData(id);
		}
		else {
			sendError(id, JdwpErrorCode.INVALID_EVENT_TYPE);
		}
	}
	
	/**
	 * Reads a number of bytes into the given buffer.
	 * @param buf buffer
	 * @param offset offset in incoming-buffer
	 * @param length number of bytes to be read
	 */
	private void read(final byte[] buf, final int offset, final int length) throws IOException {
		int currOffset = offset;
		int remaining = length;
		while (remaining > 0) {
			int len;
			try {
				len = is.read(buf, currOffset, remaining);
			}
			catch (SocketTimeoutException e) {
				throw e;
			}
			catch (IOException e) {
				throw new IOException(String.format("IO-Error while reading %d bytes from debugger", Integer.valueOf(length)), e);
			}
			if (len <= 0) {
				break;
			}
			currOffset += len;
			remaining -= len;
		}
	}

	/**
	 * Reads a command-packet or reply-packet.
	 * @return lenght of packet
	 * @throws IOException in case of an IO-error 
	 */
	private int readPacket() throws IOException {
		read(fBufIn, 0, HEADER_LEN);
		final int length = ((fBufIn[0] & 0xff) << 24)
				+ ((fBufIn[1] & 0xff) << 16)
				+ ((fBufIn[2] & 0xff) << 8)
				+ (fBufIn[3] & 0xff);

		LOG.debug("len=" + length);
		final byte[] buf;
		if (length <= fBufIn.length) {
			buf = fBufIn;
		}
		else {
			buf = new byte[length];
			System.arraycopy(fBufIn, 0, buf, 0, HEADER_LEN);
		}
		read(buf, HEADER_LEN, length - HEADER_LEN);
		return length;
	}

	/**
	 * Sends an error-reply.
	 * @param id id of reply
	 * @param errorCode error-code
	 * @throws IOException in case of an IO-error 
	 */
	private void sendError(final int id, final JdwpErrorCode errorCode) throws IOException {
		int length = HEADER_LEN;
		writeInt(fBufOut, 0, length);
		writeInt(fBufOut, 4, id);
		writeByte(fBufOut, 8, (byte) 0x80);
		writeShort(fBufOut, 9, errorCode.getErrorCode());
		os.write(fBufOut, 0, length);
		LOG.debug("sendError " + id + ": " + errorCode);
	}

	/**
	 * Sends a response.
	 * @return id of reply
	 * @param fields fields of the response
	 * @throws IOException in case of an io-error 
	 */
	private int sendReplyData(final int id, VMDataField... fields) throws IOException {
		int length = HEADER_LEN;
		for (final VMDataField field : fields) {
			length += field.length();
		}
		final byte[] buf = (length <= fBufOut.length) ? fBufOut : new byte[length];
		writeInt(buf, 0, length);
		writeInt(buf, 4, id);
		writeByte(buf, 8, (byte) 0x80);
		writeShort(buf, 9, JdwpErrorCode.NONE.getErrorCode());
		int offset = HEADER_LEN;
		for (final VMDataField field : fields) {
			field.write(buf, offset);
			offset += field.length();
		}
		os.write(buf, 0, length);
		LOG.debug("RangeOut: " + printHexBinary(buf, 0, length));
		LOG.debug("RangeOut: '" + new String(buf, 0, length, StandardCharsets.ISO_8859_1).replaceAll("[^\u0020-\u007f\u00a0-\u00ff]", "°") + "'");
		
		return id;
	}

	/** {@inheritDoc} */
	@Override
	public int sendVMEvent(final JdwpSuspendPolicy policy, final VMEventType eventType, final VMDataField... fields) throws IOException {
		final int id = ++idOutCounter;
		int length = HEADER_LEN + 1 + 4 + 1;
		for (final VMDataField field : fields) {
			if (field == null) {
				throw new IllegalStateException(String.format("sendVMEvent: eventType=%s, unexpected null in fields, %s",
						eventType, Arrays.toString(fields)));
			}
			length += field.length();
		}
		final byte[] buf = (length <= fBufOut.length) ? fBufOut : new byte[length];
		writeCommandHeader(buf, length, id, JdwpCommand.COMPOSITE);
		int offset = HEADER_LEN;
		writeByte(buf, offset, policy.getPolicy());
		offset++;
		writeInt(buf, offset, 1); // exactly one event
		offset += 4;
		writeByte(buf, offset, eventType.getEventKind());
		offset++;
		for (final VMDataField field : fields) {
			field.write(buf, offset);
			offset += field.length();
		}
		LOG.debug("EventOut: " + printHexBinary(buf, 0, length));
		LOG.debug("EventOut: '" + new String(buf, 0, length, StandardCharsets.ISO_8859_1).replaceAll("[^\u0020-\u007f\u00a0-\u00ff]", "°") + "'");
		os.write(buf, 0, length);
		
		return id;
	}

	/**
	 * Writes a command-header.
	 * @param buf buffer
	 * @param length total length of command
	 * @param id id of command
	 * @param flags flags
	 * @param jCmd command-set and command
	 */
	private static void writeCommandHeader(final byte[] buf, final int length, final int id,
			final JdwpCommand jCmd) {
		writeInt(buf, 0, length);
		writeInt(buf, 4, id);
		writeByte(buf, 8, (byte) 0);
		writeByte(buf, 9, jCmd.getCommandSet());
		writeByte(buf, 10, jCmd.getCommand());
	}

	/**
	 * Writes an byte into a buffer.
	 * @param buf buffer
	 * @param offset offset
	 * @param value byte
	 */
	private static void writeByte(byte[] buf, int offset, byte value) {
		buf[offset] = value;
	}

	/**
	 * Writes an signed-integer into a buffer.
	 * @param buf buffer
	 * @param offset offset
	 * @param value integer-value
	 */
	private static void writeShort(byte[] buf, int offset, short value) {
		buf[offset] = (byte) (value >> 8);
		buf[offset + 1] = (byte) (value & 0xff);
	}

	/**
	 * Writes an signed-integer into a buffer.
	 * @param buf buffer
	 * @param offset offset
	 * @param value integer-value
	 */
	private static void writeInt(byte[] buf, int offset, int value) {
		buf[offset] = (byte) (value >> 24);
		buf[offset + 1] = (byte) ((value >> 16) & 0xff);
		buf[offset + 2] = (byte) ((value >> 8) & 0xff);
		buf[offset + 3] = (byte) (value & 0xff);
	}

	/**
	 * Prints a part of a byte-array.
	 * @param buf buffer
	 * @param offset offset in buffer
	 * @param len length of the part
	 * @return hex-digit-presentation
	 */
	static String printHexBinary(final byte[] buf, final int offset, final int len) {
		final int lenMax = Math.min(len, 48);
		final StringBuilder sb = new StringBuilder(2 * lenMax + 3);
		for (int i = 0; i < lenMax; i++) {
			final String hh = Integer.toHexString(buf[offset + i] & 0xff);
			if (hh.length() == 1) {
				sb.append('0');
			}
			sb.append(hh);
		}
		if (lenMax < len) {
			sb.append("...");
		}
		return sb.toString();
	}

}
