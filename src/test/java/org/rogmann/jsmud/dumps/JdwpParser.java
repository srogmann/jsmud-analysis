package org.rogmann.jsmud.dumps;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.rogmann.jsmud.datatypes.VMArrayID;
import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMClassObjectID;
import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMObjectID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.debugger.CommandBuffer;
import org.rogmann.jsmud.debugger.JdwpCommand;
import org.rogmann.jsmud.debugger.JdwpCommandSet;
import org.rogmann.jsmud.debugger.VMEventType;
import org.rogmann.jsmud.events.ModKind;
import org.rogmann.jsmud.replydata.TypeTag;

/**
 * Simple jdwp-parser.
 */
public class JdwpParser {
	
	/** JDWP-Handshake */
	private static final String JDWP_HANDSHAKE = "JDWP-Handshake";

	/** Booleans in reply of Capabilities Command */
	private static final String[] CAPABILITIES = {
			"canWatchFieldModification",
			"canWatchFieldAccess",
			"canGetBytecodes",
			"canGetSyntheticAttribute",
			"canGetOwnedMonitorInfo",
			"canGetCurrentContendedMonitor",
			"canGetMonitorInfo"
	};

	/** output-indentation */
	private final String indent;
	/** output-stream */
	private final PrintStream psOut;
	
	/** <code>true</code> after jdwp-handshake */
	private boolean isJdwp = false;

	/** internal buffer */
	private byte[] buf = new byte[4096];

	/** offset of next byte in buffer */
	private int offsetNext = 0;
	
	/** offset in stream */
	private int offsetStream = 0;
	
	/** map from id to command-set */
	private final Map<Integer, JdwpCommandSet> mapReqCmdSet;
	/** map from id to command */
	private final Map<Integer, JdwpCommand> mapReqCmd;

	/**
	 * Constructor
	 * @param indent indentation of output
	 * @param psOut output-stream
	 * @param mapReqCmdSet map from id to command-set
	 * @param mapReqCmd map from id to command
	 */
	public JdwpParser(final String indent, final PrintStream psOut,
			final Map<Integer, JdwpCommandSet> mapReqCmdSet, final Map<Integer, JdwpCommand> mapReqCmd) {
		this.psOut = psOut;
		this.indent = indent;
		this.mapReqCmdSet = mapReqCmdSet;
		this.mapReqCmd = mapReqCmd;
	}

	/**
	 * Reads the next part of the stream
	 * @param buf input-buffer
	 * @param pOffset offset in input-buffer
	 * @param pLen length in input-buffer
	 */
	public void addPart(final byte[] pBuf, int pOffset, int pLen) {
		if (offsetNext + pLen > buf.length) {
			// resize internal buffer
			final byte[] bufNew = new byte[buf.length * 2];
			System.arraycopy(buf, 0, bufNew, 0, buf.length);
			buf = bufNew;
		}
		System.arraycopy(pBuf, pOffset, buf, offsetNext, pLen);
		offsetNext += pLen;
		
		if (!isJdwp) {
			// looking for handshake
			if (offsetNext >= JDWP_HANDSHAKE.length()
					&& new String(buf, 0, JDWP_HANDSHAKE.length(), StandardCharsets.ISO_8859_1).equals(JDWP_HANDSHAKE)) {
				isJdwp = true;
				psOut.println(String.format("%s%08x: %s", indent, Integer.valueOf(offsetStream), JDWP_HANDSHAKE));
				shiftBuffer(JDWP_HANDSHAKE.length());
			}
		}
		if (isJdwp && offsetNext >= 11) {
			final int packetLen = readInt(buf, 0);
			if (offsetNext >= packetLen) {
				final int id = readInt(buf, 4);
				final int flags = buf[8] & 0xff;
				final CommandBuffer cmdBuf = new CommandBuffer(buf, 11, packetLen);
				if ((flags & 0x80) == 0) {
					// request-packet
					final byte cmdSet = buf[9];
					final byte cmd = buf[10];
					final JdwpCommandSet jCmdSet = JdwpCommandSet.lookupByKind(cmdSet);
					final JdwpCommand jCmd = JdwpCommand.lookupByKind(jCmdSet, cmd);
					if (jCmd == null) {
						throw new RuntimeException(String.format("Unknown jdwp-command: %d/%d",
								Integer.valueOf(cmdSet), Integer.valueOf(cmd)));
					}
					mapReqCmdSet.put(Integer.valueOf(id), jCmdSet);
					mapReqCmd.put(Integer.valueOf(id), jCmd);
					psOut.println(String.format("%s%08x: <-request %s/%s, id=0x%04x, len=0x%x", indent, Integer.valueOf(offsetStream),
							jCmdSet, jCmd, Integer.valueOf(id), Integer.valueOf(packetLen)));
					if (jCmdSet == JdwpCommandSet.VIRTUAL_MACHINE
							&& (jCmd == JdwpCommand.CLASSES_BY_SIGNATURE)) {
						final String signature = cmdBuf.readString(cmdBuf.readInt());
						psOut.println(String.format("%s%08x: <- signature=%s", indent, Integer.valueOf(offsetStream),
								signature));
					}
					else if (jCmdSet == JdwpCommandSet.REFERENCE_TYPE
							&& (jCmd == JdwpCommand.SIGNATURE
								|| jCmd == JdwpCommand.SIGNATURE_WITH_GENERIC
								|| jCmd == JdwpCommand.METHODS_WITH_GENERIC)) {
						final VMReferenceTypeID refTypeId = new VMReferenceTypeID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: <- refType=%s", indent, Integer.valueOf(offsetStream),
								refTypeId));
					}
					else if (jCmdSet == JdwpCommandSet.CLASS_TYPE && jCmd == JdwpCommand.CLASS_NEW_INSTANCE) {
						final VMClassID classTypeId = new VMClassID(cmdBuf.readLong());
						final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
						final VMMethodID methodId = new VMMethodID(cmdBuf.readLong());
						final int args = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <- classTypeId=%s, threadId=%s, methodId=%s, args=%d",
								indent, Integer.valueOf(offsetStream),
								classTypeId, threadId, methodId, Integer.valueOf(args)));
					}
					else if (jCmdSet == JdwpCommandSet.OBJECT_REFERENCE && jCmd == JdwpCommand.REFERENCE_TYPE) {
						final VMObjectID objectID = new VMObjectID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: <- objectID=%s", indent, Integer.valueOf(offsetStream),
								objectID));
					}
					else if (jCmdSet == JdwpCommandSet.OBJECT_REFERENCE && jCmd == JdwpCommand.GET_VALUES) {
						final VMObjectID objectID = new VMObjectID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: <- objectID=%s", indent, Integer.valueOf(offsetStream),
								objectID));
						final int numFields = cmdBuf.readInt();
						for (int i = 0; i < numFields; i++) {
							final VMFieldID fieldID = new VMFieldID(cmdBuf.readLong());
							psOut.println(String.format("%s%08x: <-   fieldID=%s", indent, Integer.valueOf(offsetStream),
									fieldID));
						}
					}
					else if (jCmdSet == JdwpCommandSet.THREAD_REFERENCE && jCmd == JdwpCommand.THREAD_FRAMES) {
						final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
						final int startFrame = cmdBuf.readInt();
						final int length = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <- thread=%s, startFrame=%d, length=%d", indent, Integer.valueOf(offsetStream),
								threadId, Integer.valueOf(startFrame), Integer.valueOf(length)));
					}
					else if (jCmdSet == JdwpCommandSet.THREAD_REFERENCE && jCmd == JdwpCommand.THREAD_FRAME_COUNT) {
						final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: <- thread=%s", indent, Integer.valueOf(offsetStream),
								threadId));
					}
					else if (jCmdSet == JdwpCommandSet.ARRAY_REFERENCE && jCmd == JdwpCommand.ARRAY_GET_VALUES) {
						final VMArrayID arrayID = new VMArrayID(cmdBuf.readLong());
						final int firstIndex = cmdBuf.readInt();
						final int length = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <- arrayID=%s, firstIndex=%d, length=%d", indent, Integer.valueOf(offsetStream),
								arrayID, Integer.valueOf(firstIndex), Integer.valueOf(length)));
					}
					else if (jCmdSet == JdwpCommandSet.EVENT_REQUEST && jCmd == JdwpCommand.SET) {
						displayEventRequestSetCmd(cmdBuf);
					}
					else if (jCmdSet == JdwpCommandSet.EVENT_REQUEST && jCmd == JdwpCommand.CLEAR) {
						final byte bKind = cmdBuf.readByte();
						final VMEventType eventType = VMEventType.lookupByKind(bKind);
						final int reqId = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <- eventKind=%s, requestId=0x%x", indent, Integer.valueOf(offsetStream),
								eventType, Integer.valueOf(reqId)));
					}
					else if (jCmdSet == JdwpCommandSet.CLASS_OBJECT_REFERENCE && jCmd == JdwpCommand.REFLECTED_TYPE) {
						final VMClassObjectID classObjectID = new VMClassObjectID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: <- classObjectID=%s", indent, Integer.valueOf(offsetStream),
								classObjectID));
					}
					else if (jCmdSet == JdwpCommandSet.EVENT && jCmd == JdwpCommand.COMPOSITE) {
						displayEventCompositeCmd(cmdBuf);
					}
				}
				else {
					// reply-packet
					final int errorcode = ((buf[9] & 0xff) << 8) + (buf[10] & 0xff);
					final JdwpCommandSet cmdSet = mapReqCmdSet.remove(Integer.valueOf(id));
					final JdwpCommand cmd = mapReqCmd.remove(Integer.valueOf(id));
					psOut.println(String.format("%s%08x: ->reply to %s/%s, errorcode=%d, id=0x%04x, len=0x%x",
							indent, Integer.valueOf(offsetStream),
							cmdSet, cmd,
							Integer.valueOf(errorcode),
							Integer.valueOf(id), Integer.valueOf(packetLen)));
					if (cmdSet == JdwpCommandSet.VIRTUAL_MACHINE && cmd == JdwpCommand.CLASSES_BY_SIGNATURE) {
						final int numClasses = cmdBuf.readInt();
						for (int i = 0; i < numClasses; i++) {
							final byte refTypeTag = cmdBuf.readByte();
							final VMReferenceTypeID refTypeID = new VMReferenceTypeID(cmdBuf.readLong());
							final int status = cmdBuf.readInt();
							psOut.println(String.format("%s%08x: -> tag=%s, refTypeID=%s, status=%d",
									indent, Integer.valueOf(offsetStream),
									TypeTag.lookupByKind(refTypeTag), refTypeID, Integer.valueOf(status)));
						}
					}
					else if (cmdSet == JdwpCommandSet.VIRTUAL_MACHINE && cmd == JdwpCommand.CAPABILITIES) {
						if (errorcode == 0) {
							for (String capName : CAPABILITIES) {
								final boolean bool = (cmdBuf.readByte() != 0x00);
								psOut.println(String.format("%s%08x: -> capability %s=%s",
										indent, Integer.valueOf(offsetStream), capName, Boolean.toString(bool)));
							}
						}
					}
					else if (cmdSet == JdwpCommandSet.REFERENCE_TYPE && cmd == JdwpCommand.SIGNATURE) {
						final String signature = cmdBuf.readString(cmdBuf.readInt());
						psOut.println(String.format("%s%08x: -> signature=%s",
								indent, Integer.valueOf(offsetStream), signature));
					}
					else if (cmdSet == JdwpCommandSet.REFERENCE_TYPE && cmd == JdwpCommand.SIGNATURE_WITH_GENERIC) {
						final String signature = cmdBuf.readString(cmdBuf.readInt());
						final String genericSignature = cmdBuf.readString(cmdBuf.readInt());
						psOut.println(String.format("%s%08x: -> signature=%s, genericSignature=%s",
								indent, Integer.valueOf(offsetStream), signature, genericSignature));
					}
					else if (cmdSet == JdwpCommandSet.REFERENCE_TYPE && cmd == JdwpCommand.METHODS_WITH_GENERIC) {
						final int numDeclared = cmdBuf.readInt();
						for (int i = 0; i < numDeclared; i++) {
							final VMMethodID methodID = new VMMethodID(cmdBuf.readLong());
							final String name = cmdBuf.readString(cmdBuf.readInt());
							final String signature = cmdBuf.readString(cmdBuf.readInt());
							final String genericSignature = cmdBuf.readString(cmdBuf.readInt());
							final int modBits = cmdBuf.readInt();
							psOut.println(String.format("%s%08x: -> methodID=%s, name=%s, signature=%s, genericSignature=%s, modBits=%d",
									indent, Integer.valueOf(offsetStream),
									methodID, name, signature, genericSignature, Integer.valueOf(modBits)));
						}
					}
					else if (cmdSet == JdwpCommandSet.OBJECT_REFERENCE && cmd == JdwpCommand.REFERENCE_TYPE) {
						final TypeTag refTypeTag = TypeTag.lookupByKind(cmdBuf.readByte());
						final VMReferenceTypeID refTypeID = new VMReferenceTypeID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: -> refTypeTag=%s, refTypeID=%s",
								indent, Integer.valueOf(offsetStream), refTypeTag, refTypeID));
					}
					else if (cmdSet == JdwpCommandSet.THREAD_REFERENCE && cmd == JdwpCommand.THREAD_FRAME_COUNT) {
						final int frameCount = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: -> frameCount=%d",
								indent, Integer.valueOf(offsetStream), Integer.valueOf(frameCount)));
					}
					else if (cmdSet == JdwpCommandSet.CLASS_OBJECT_REFERENCE && cmd == JdwpCommand.REFLECTED_TYPE) {
						final byte refTypeTag = cmdBuf.readByte(); 
						final VMReferenceTypeID refTypeID = new VMReferenceTypeID(cmdBuf.readLong());
						psOut.println(String.format("%s%08x: -> typeTag=%s, refTypeID=%s",
								indent, Integer.valueOf(offsetStream), TypeTag.lookupByKind(refTypeTag), refTypeID));
					}
					else if (cmdSet == JdwpCommandSet.EVENT_REQUEST && cmd == JdwpCommand.SET) {
						final int requestId = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: -> requestId=0x%x",
								indent, Integer.valueOf(offsetStream), Integer.valueOf(requestId)));
					}
					
				}
				shiftBuffer(packetLen);
			}
		}
	}

	/**
	 * Displays an event-request/set-command.
	 * @param cmdBuf request-buffer
	 */
	private void displayEventRequestSetCmd(final CommandBuffer cmdBuf) {
		final byte eventKind = cmdBuf.readByte();
		final byte suspendPolicy = cmdBuf.readByte();
		final int modifiers = cmdBuf.readInt();
		final VMEventType eventType = VMEventType.lookupByKind(eventKind);
		psOut.println(String.format("%s%08x: <- eventKind %s, suspendPolicy=%d, #modifiers=%d",
				indent, Integer.valueOf(offsetStream),
				eventType, Integer.valueOf(suspendPolicy), Integer.valueOf(modifiers)));
		for (int i = 0; i < modifiers; i++) {
			final byte bModKind = cmdBuf.readByte();
			final ModKind modKind = ModKind.lookupByKind(bModKind);
			if (modKind == null) {
				psOut.println(String.format("%s%08x: <-  unknown modKind %d",
						indent, Integer.valueOf(offsetStream), Integer.valueOf(bModKind)));
				break;
			}
			if (modKind == ModKind.COUNT) {
				final int count = cmdBuf.readInt();
				psOut.println(String.format("%s%08x: <-  modKind=%s, count=%d",
						indent, Integer.valueOf(offsetStream), modKind, Integer.valueOf(count)));
			}
			else if (modKind == ModKind.THREAD_ONLY) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				psOut.println(String.format("%s%08x: <-  modKind=%s, threadId=%s",
						indent, Integer.valueOf(offsetStream), modKind, threadId));
			}
			else if (modKind == ModKind.CLASS_ONLY) {
				final VMReferenceTypeID clazz = new VMReferenceTypeID(cmdBuf.readLong());
				psOut.println(String.format("%s%08x: <-  modKind=%s, clazz=%s",
						indent, Integer.valueOf(offsetStream), modKind, clazz));
			}
			else if (modKind == ModKind.CLASS_MATCH
					|| modKind == ModKind.CLASS_EXCLUDE) {
				final String classPattern = cmdBuf.readString(cmdBuf.readInt());
				psOut.println(String.format("%s%08x: <-  modKind=%s, classPattern=%s",
						indent, Integer.valueOf(offsetStream), modKind, classPattern));
			}
			else if (modKind == ModKind.LOCATION_ONLY) {
				final byte typeTag = cmdBuf.readByte();
				final VMClassID classId = new VMClassID(cmdBuf.readLong());
				final VMMethodID methodId = new VMMethodID(cmdBuf.readLong());
				final long idx = cmdBuf.readLong();
				psOut.println(String.format("%s%08x: <-  modKind=%s, typeTag=0x%02x, classId=%s, methodId=%s, idx=%d",
						indent, Integer.valueOf(offsetStream), modKind,
						Integer.valueOf(typeTag & 0xff), classId, methodId, Long.valueOf(idx)));
			}
			else if (modKind == ModKind.EXCEPTION_ONLY) {
				final VMReferenceTypeID exceptionOrNull = new VMReferenceTypeID(cmdBuf.readLong());
				final boolean caught = (cmdBuf.readByte() != 0);
				final boolean uncaught = (cmdBuf.readByte() != 0);
				psOut.println(String.format("%s%08x: <-  modKind=%s, exceptionOrNull=%s, caught=%s, uncaught=%s",
						indent, Integer.valueOf(offsetStream), modKind,
						exceptionOrNull, Boolean.toString(caught), Boolean.toString(uncaught)));
			}
			else if (modKind == ModKind.STEP) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				final int size = cmdBuf.readInt();
				final int depth = cmdBuf.readInt();
				psOut.println(String.format("%s%08x: <-  modKind=%s, thread=%s, size=%d, depth=%d",
						indent, Integer.valueOf(offsetStream), modKind, threadId,
						Integer.valueOf(size), Integer.valueOf(depth)));
			}
			else {
				psOut.println(String.format("%s%08x: <-  unexpected modKind %s",
						indent, Integer.valueOf(offsetStream), modKind));
				break;
			}
		}
	}

	/**
	 * Displays an event/composite-command.
	 * @param cmdBuf request-buffer
	 */
	private void displayEventCompositeCmd(final CommandBuffer cmdBuf) {
		final byte suspendPolicy = cmdBuf.readByte();
		final int events = cmdBuf.readInt();
		psOut.println(String.format("%s%08x: <-  suspendPolicy=%d, #events=%d",
				indent, Integer.valueOf(offsetStream),
				Integer.valueOf(suspendPolicy), Integer.valueOf(events)));
		for (int i = 0; i < events; i++) {
			final byte eventKind = cmdBuf.readByte();
			final VMEventType eventType = VMEventType.lookupByKind(eventKind);
			final int evReqId = cmdBuf.readInt();
			if (eventType == VMEventType.VM_START) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				psOut.println(String.format("%s%08x: <-  req=0x%x, eventKind=%s, threadID=%s",
						indent, Integer.valueOf(offsetStream),
						Integer.valueOf(evReqId), eventType, threadId));
			}
			else if (eventType == VMEventType.SINGLE_STEP
					|| eventType == VMEventType.BREAKPOINT
					|| eventType == VMEventType.METHOD_ENTRY
					|| eventType == VMEventType.METHOD_EXIT
					|| eventType == VMEventType.METHOD_EXIT_WITH_RETURN_VALUE) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				final byte bTag = cmdBuf.readByte();
				final TypeTag typeTag = TypeTag.lookupByKind(bTag);
				final VMClassID classId = new VMClassID(cmdBuf.readLong());
				final VMMethodID methodId = new VMMethodID(cmdBuf.readLong());
				final long idxInstr = cmdBuf.readLong();
				psOut.println(String.format("%s%08x: <-  reqId=0x%x, eventKind=%s, threadID=%s, typeTag=%s, classId=%s, methodId=%s, idx=%d",
						indent, Integer.valueOf(offsetStream),
						Integer.valueOf(evReqId), eventType, threadId, typeTag, classId, methodId, Long.valueOf(idxInstr)));
				if (eventType == VMEventType.METHOD_EXIT_WITH_RETURN_VALUE) {
					final byte bType = cmdBuf.readByte();
					if (bType == 'V') {
						psOut.println(String.format("%s%08x: <-   type='%c'",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType)));
					}
					else if (bType == 'B') {
						final byte value = cmdBuf.readByte();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%d",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Byte.valueOf(value)));
					}
					else if (bType == 'C') {
						final short value = cmdBuf.readShort();
						psOut.println(String.format("%s%08x: <-   type='%c', value='%c'",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Character.valueOf((char) value)));
					}
					else if (bType == 'C') {
						final short value = cmdBuf.readShort();
						psOut.println(String.format("%s%08x: <-   type='%c', value='%c'",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Character.valueOf((char) value)));
					}
					else if (bType == 'S') {
						final short value = cmdBuf.readShort();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%d",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Short.valueOf(value)));
					}
					else if (bType == 'I') {
						final int value = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%d",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Integer.valueOf(value)));
					}
					else if (bType == 'J') {
						final long value = cmdBuf.readLong();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%d",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Long.valueOf(value)));
					}
					else if (bType == 'Z') {
						final byte value = cmdBuf.readByte();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%s",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Boolean.valueOf(value != 0)));
					}
					else if (bType == 'F') {
						final int value = cmdBuf.readInt();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%f",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Float.valueOf(Float.intBitsToFloat(value))));
					}
					else if (bType == 'D') {
						final long value = cmdBuf.readLong();
						psOut.println(String.format("%s%08x: <-   type='%c', value=%f",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Double.valueOf(Double.longBitsToDouble(value))));
					}
					else {
						final long objId = cmdBuf.readLong();
						psOut.println(String.format("%s%08x: <-   type='%c', objectId=0x%x",
								indent, Integer.valueOf(offsetStream),
								Character.valueOf((char) bType), Long.valueOf(objId)));
						break;
					}
				}
			}
			else if (eventType == VMEventType.CLASS_PREPARE) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				final byte refTypeTag = cmdBuf.readByte();
				final TypeTag typeTag = TypeTag.lookupByKind(refTypeTag);
				final VMReferenceTypeID typeId = new VMReferenceTypeID(cmdBuf.readLong());
				final String signature = cmdBuf.readString(cmdBuf.readInt());
				final int status = cmdBuf.readInt();
				psOut.println(String.format("%s%08x: <-  reqId=0x%x, eventKind=%s, threadID=%s, typeTag=%s, typeId=%s, signature=%s, status=%d",
						indent, Integer.valueOf(offsetStream),
						Integer.valueOf(evReqId), eventType, threadId, typeTag, typeId, signature, Integer.valueOf(status)));
			}
			else if (eventType == VMEventType.VM_START
					|| eventType == VMEventType.THREAD_START
					|| eventType == VMEventType.THREAD_DEATH) {
				final VMThreadID threadId = new VMThreadID(cmdBuf.readLong());
				psOut.println(String.format("%s%08x: <-   reqId=0x%x, eventKind=%s, thread=%s",
						indent, Integer.valueOf(offsetStream), Integer.valueOf(evReqId), eventType,
						threadId));
			}
			else if (eventType == VMEventType.VM_DEATH) {
				psOut.println(String.format("%s%08x: <-   reqId=0x%x, eventKind=%s",
						indent, Integer.valueOf(offsetStream), Integer.valueOf(evReqId), eventType));
			}
			else {
				psOut.println(String.format("%s%08x: <-   reqId=0x%x, eventKind=0x%x",
						indent, Integer.valueOf(offsetStream),
						Integer.valueOf(evReqId), Integer.valueOf(eventKind)));
				break;
			}
		}
	}

	/**
	 * Reads an 32-bit signed-integer.
	 * @return int
	 */
	static int readInt(final byte[] buf, final int offset) {
		final int length = ((buf[offset] & 0xff) << 24)
				+ ((buf[offset + 1] & 0xff) << 16)
				+ ((buf[offset + 2] & 0xff) << 8)
				+ (buf[offset + 3] & 0xff);
		return length;
	}

	/**
	 * Removes read bytes and shifts the buffer.
	 * @param num number of read bytes
	 */
	private void shiftBuffer(int num) {
		System.arraycopy(buf, num, buf, 0, offsetNext - num);
		offsetStream += num;
		offsetNext -= num;
	}
}
