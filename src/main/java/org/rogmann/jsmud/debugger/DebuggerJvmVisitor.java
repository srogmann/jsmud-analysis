package org.rogmann.jsmud.debugger;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.rogmann.jsmud.datatypes.VMByte;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMInt;
import org.rogmann.jsmud.datatypes.VMLong;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMString;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.events.JdwpEventModifier;
import org.rogmann.jsmud.events.JdwpEventRequest;
import org.rogmann.jsmud.events.JdwpModifierClassMatch;
import org.rogmann.jsmud.events.JdwpModifierClassOnly;
import org.rogmann.jsmud.events.JdwpModifierCount;
import org.rogmann.jsmud.events.JdwpModifierFieldOnly;
import org.rogmann.jsmud.events.JdwpModifierLocationOnly;
import org.rogmann.jsmud.events.JdwpModifierStep;
import org.rogmann.jsmud.events.JdwpModifierThreadOnly;
import org.rogmann.jsmud.events.ModKind;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.replydata.TypeTag;
import org.rogmann.jsmud.visitors.InstructionVisitor;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.MethodFrame;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.SimpleClassExecutor;
import org.rogmann.jsmud.vm.VM;

/**
 * JVM-debugger-visitor.
 */
public class DebuggerJvmVisitor implements JvmExecutionVisitor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(DebuggerJvmVisitor.class);

	/** step-depths */
	private static final String[] STEP_DEPTHS = { "INTO", "OVER", "OUT" };
	/** step-sizes */
	private static final String[] STEP_SIZES = { "MIN", "LINE" };

	/** VM-simulator */
	private ClassRegistry vm;

	/** map from request-id to event-request */
	private final ConcurrentMap<Integer, JdwpEventRequest> eventRequests;
	
	/** interface of the debugger */
	private DebuggerInterface debugger;

	/** stack of method-frames */
	private final Deque<MethodFrameDebugContext> stack = new ConcurrentLinkedDeque<>();

	/** current method-frame */
	private MethodFrameDebugContext currFrame;

	/** number of instructions to be logged in detail */
	private final int maxInstrLogged;

	/** number of method-invocations to be logged in detail */
	private final int maxMethodsLogged;

	/** optional source-file-requester */
	private final SourceFileRequester sourceFileRequester;

	/** number of processed instructions */
	private final AtomicLong instrCounter = new AtomicLong();

	/** number of processed method-invocations */
	private final AtomicLong methodCounter = new AtomicLong();

	/** <code>true</code> if the debugger is processing packets */
	private final AtomicBoolean isProcessingPackets = new AtomicBoolean(false);

	/**
	 * Constructor
	 * @param eventRequests map from jdwp-request-id to event-request
	 * @param maxInstrLogged number of instructions to be logged at debug-level
	 * @param maxMethodsLogged number of method-invocations to be logged at debug-level
	 * @param sourceFileRequester optional source-file-requester
	 */
	public DebuggerJvmVisitor(final ConcurrentMap<Integer, JdwpEventRequest> eventRequests,
			final int maxInstrLogged, final int maxMethodsLogged,
			final SourceFileRequester sourceFileRequester) {
		this.maxInstrLogged = maxInstrLogged;
		this.maxMethodsLogged = maxMethodsLogged;
		this.sourceFileRequester = sourceFileRequester;
		this.eventRequests = eventRequests;
	}

	/**
	 * Gets the JVM-simulation used by the debugger-visitor.
	 * @return vm-simulation
	 */
	public VM getJvmSimulator() {
		return vm;
	}

	/**
	 * Gets the debugger-interface.
	 * @return debugger-interface
	 */
	public DebuggerInterface getDebugger() {
		return debugger;
	}

	/**
	 * Sets the JVM-simulator.
	 * @param vm simulator
	 */
	public void setJvmSimulator(ClassRegistry vm) {
		this.vm = vm;
	}

	/**
	 * Sets the debugger-interface.
	 * @param debugger debugger
	 */
	public void setDebugger(DebuggerInterface debugger) {
		this.debugger = debugger;
	}

	/**
	 * Sets if the debugger is processing JDWP-messages currently.
	 * @param isProcessingFlag is-processing-flag
	 */
	public void setIsProcessingPackets(boolean isProcessingFlag) {
		isProcessingPackets.set(isProcessingFlag);
	}

	/**
	 * Executes a supplier.
	 * The call-method is expected in the class of the supplier-instance (not in a parent-class).
	 * @param supplier supplier to be called
	 * @param <T> return-type
	 * @param classReturnObj class of return-object
	 * @return return-object
	 * @throws IOException in case of an {@link IOException}
	 */
	public <T> T executeSupplier(final Supplier<T> supplier, final Class<T> classReturnObj) throws IOException {
		final boolean isThreadNew = vm.registerThread(Thread.currentThread());
		if (!isThreadNew) {
			LOG.info(String.format("The thread (%s) to execute the supplier (%s) is known already.",
					Thread.currentThread(), supplier));
		}
		final Object objReturn;
		try {
			if (isThreadNew) {
				visitThreadStarted(Thread.currentThread());
			}
			vm.suspendThread(vm.getCurrentThreadId());
			debugger.processPackets();

			final Class<?> supplierClass = supplier.getClass();
			final SimpleClassExecutor executor = new SimpleClassExecutor(vm, supplierClass, vm.getInvocationHandler());
			// We have to announce the class to the debugger.
			visitLoadClass(supplierClass);
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(supplier);
			try {
				final Method methodCall = supplierClass.getDeclaredMethod("get");
				final String methodDesc = "()" + Type.getType(methodCall.getReturnType()).getDescriptor();
				objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, methodCall, methodDesc, stackArgs);
			} catch (Throwable e) {
				throw new RuntimeException("Exception while simulating supplier " + supplierClass.getName(), e);
			}
		}
		finally {
			if (isThreadNew) {
				vm.unregisterThread(Thread.currentThread());
			}
		}
		if (objReturn != null && !classReturnObj.isInstance(objReturn)) {
			throw new JvmException(String.format("Unexpected return-type (%s) instead of (%s) of supplier",
					objReturn.getClass(), classReturnObj));
		}
		@SuppressWarnings("unchecked")
		final T t = (T) objReturn;
		return t;
	}

	/**
	 * Adds an event-request.
	 * @param evReq event-request.
	 */
	public void addEventRequest(final JdwpEventRequest evReq) {
		eventRequests.put(Integer.valueOf(evReq.getRequestId()), evReq);
		if (evReq.getEventType() == VMEventType.SINGLE_STEP) {
			addEventRequestSingleStep(evReq);
		}
	}

	/**
	 * Adds a SINGLE_STEP-event-request to the current frame.
	 * @param evReq SINGLE_STEP-event-request
	 */
	private void addEventRequestSingleStep(final JdwpEventRequest evReq) {
		for (final JdwpEventModifier mod : evReq.getModifiers()) {
			if (mod.getModKind() == ModKind.STEP) {
				final JdwpModifierStep modStep = (JdwpModifierStep) mod;
				if (vm.getCurrentThreadId().equals(modStep.getThreadID())) {
					// A step starts.
					final int ss = modStep.getStepSize();
					final int sd = modStep.getStepDepth();
					if (currFrame == null) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("STEP-start (req-id %d, not in execution yet): size=%s, depth=%s",
									Integer.valueOf(evReq.getRequestId()),
									computeName(STEP_SIZES, ss), computeName(STEP_DEPTHS, sd)));
						}
					}
					else {
						if (sd == JdwpModifierStep.STEP_DEPTH_OUT) {
							currFrame.eventRequestStepUp = evReq;
							currFrame.modStepUp = modStep;
						}
						else {
							currFrame.eventRequestStep = evReq;
							currFrame.modStep = modStep;
							currFrame.stepLine = currFrame.frame.getCurrLineNum();
						}
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("STEP-start (req-id %d, method %s): size=%s, depth=%s, startLine=%d",
									Integer.valueOf(evReq.getRequestId()),
									currFrame.frame.getMethod(),
									computeName(STEP_SIZES, ss), computeName(STEP_DEPTHS, sd),
									Integer.valueOf(currFrame.stepLine)));
						}
					}
					break;
				}
			}
		}
	}

	/**
	 * Computes the name of a constant.
	 * @param sNames array of names
	 * @param idx index
	 * @return
	 */
	static String computeName(String[] sNames, final int idx) {
		final String name;
		if (idx >= 0 && idx < sNames.length) {
			name = sNames[idx];
		}
		else {
			name = Integer.toString(idx);
		}
		return name;
	}

	/**
	 * Removes an event-request.
	 * @param eventType event-type
	 * @param requestId request-id
	 */
	public void clearEventRequest(VMEventType eventType, int requestId) {
		final JdwpEventRequest evReq = eventRequests.remove(Integer.valueOf(requestId));
		if (evReq == null) {
			LOG.debug(String.format("Event (type %s, req-id %d) is not known",
					eventType, Integer.valueOf(requestId)));
		}
		else if (eventType == VMEventType.SINGLE_STEP) {
			for (final JdwpEventModifier mod : evReq.getModifiers()) {
				if (mod.getModKind() == ModKind.STEP) {
					final JdwpModifierStep modStep = (JdwpModifierStep) mod;
					if (vm.getCurrentThreadId().equals(modStep.getThreadID())) {
						// A step starts.
						if (currFrame != null) {
							currFrame.eventRequestStep = null;
							currFrame.modStep = null;
						}
						LOG.debug(String.format("STEP (req-id %d) removed",
								Integer.valueOf(evReq.getRequestId())));
						break;
					}
				}
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitThreadStarted(Thread startedThread) {
		LOG.debug("visitThreadStarted: " + startedThread.getName());
		for (final JdwpEventRequest evReq : eventRequests.values()) {
			if (evReq.getEventType() != VMEventType.THREAD_START) {
				continue;
			}
			final VMThreadID curThreadId = vm.getThreadId(startedThread);
			if (curThreadId == null) {
				throw new DebuggerException(String.format("The thread (%s) is not known to the VM", startedThread));
			}
			try {
				debugger.sendVMEvent(JdwpSuspendPolicy.NONE, evReq.getEventType(),
						new VMInt(evReq.getRequestId()), curThreadId);
			} catch (IOException e) {
				throw new DebuggerException("IO-error while talking with the debugger (thread-started)", e);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitLoadClass(Class<?> loadedClass) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("visitLoadClass: %s in %s", loadedClass.getName(), loadedClass.getClassLoader()));
		}
		
		if (sourceFileRequester != null && sourceFileRequester.isSourceRequested(loadedClass)) {
			try {
				vm.generateSourceFile(loadedClass, sourceFileRequester);
			} catch (IOException e) {
				throw new DebuggerException(String.format("IO-error while writing source-file of (%s)", loadedClass), e);
			}
		}
loopEvents:
		for (final JdwpEventRequest evReq : eventRequests.values()) {
			if (evReq.getEventType() != VMEventType.CLASS_PREPARE) {
				continue;
			}
			
			for (final JdwpEventModifier mod : evReq.getModifiers()) {
				if (mod.getModKind() == ModKind.CLASS_MATCH
						|| mod.getModKind() == ModKind.CLASS_EXCLUDE) {
					final JdwpModifierClassMatch modClass = (JdwpModifierClassMatch) mod;
					final Matcher m = modClass.getClassPattern().matcher(loadedClass.getName());
					final boolean isMatch = m.matches();
					if (mod.getModKind() == ModKind.CLASS_MATCH && !isMatch) {
						// no match
						continue loopEvents;
					}
					if (mod.getModKind() == ModKind.CLASS_EXCLUDE && isMatch) {
						// exclude
						continue loopEvents;
					}
				}
				else if (mod.getModKind() == ModKind.COUNT) {
					final JdwpModifierCount modCount = (JdwpModifierCount) mod;
					LOG.info("TODO: count " + modCount.getCount());
				}
				else {
					throw new DebuggerException(String.format("Unexpected modifier (%s) in event 0x%x of type %s",
							mod, Integer.valueOf(evReq.getRequestId()), evReq.getEventType()));
				}
			}

			LOG.debug("visitLoadClass: send class-prepare-event");
			final VMThreadID curThreadId = vm.getThreadId(Thread.currentThread());
			final RefTypeBean refTypeBean = vm.getClassRefTypeBean(loadedClass);
			JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
			try {
				if (debugger == null) {
					throw new IllegalStateException("There is no debugger registered (see method setDebugger).");
				}
				debugger.sendVMEvent(suspendPolicy, evReq.getEventType(),
						new VMInt(evReq.getRequestId()), curThreadId,
						new VMByte(refTypeBean.getTypeTag().getTag()),
						refTypeBean.getTypeID(),
						new VMString(refTypeBean.getSignature()),
						new VMInt(refTypeBean.getStatus()));
			} catch (IOException e) {
				throw new DebuggerException("IO-error while talking with the debugger (class-prepare)", e);
			}

			giveDebuggerControl(curThreadId, suspendPolicy);

		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodEnter(Class<?> currClass, Executable method, MethodFrame frame) {
		currFrame = new MethodFrameDebugContext(frame);
		if (methodCounter.incrementAndGet() <= maxMethodsLogged) {
			LOG.debug(String.format("methodEnter lvl %d in frameCtx %s (%s/%s) to %s",
					Integer.valueOf(stack.size()), currFrame,
					currFrame.frame, currFrame.frame.getMethod(),
					method));
			// stack.stream().map(f -> f.frame.getMethod().toString()).collect(Collectors.joining(", "))
		}
		stack.push(currFrame);
		for (final JdwpEventRequest evReq : eventRequests.values()) {
			if (evReq.getEventType() == VMEventType.METHOD_ENTRY) {
				final VMThreadID curThreadId = vm.getThreadId(Thread.currentThread());
				boolean isThreadOk = true;
				for (final JdwpEventModifier mod : evReq.getModifiers()) {
					if (mod.getModKind() == ModKind.THREAD_ONLY) {
						final JdwpModifierThreadOnly modThread = (JdwpModifierThreadOnly) mod;
						if (!modThread.getThreadId().equals(curThreadId)) {
							isThreadOk = false;
						}
					}
					else {
						throw new DebuggerException(String.format("Unexpected modifier (%s) in event 0x%x of type %s",
								mod, Integer.valueOf(evReq.getRequestId()), evReq.getEventType()));
					}
				}
				if (isThreadOk) {
					final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
					sendThreadLocEvent(suspendPolicy, evReq, curThreadId, frame);
					
					// Gives control to the debugger.
					giveDebuggerControl(curThreadId, suspendPolicy);
				}
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExit(final Class<?> currClass, final Executable method, final MethodFrame frame, final Object objReturn) {
		if (methodCounter.get() <= maxMethodsLogged) {
			LOG.debug(String.format("methodExit lvl %d in frame %s (%s/%s) from %s: objReturn.class=%s",
					Integer.valueOf(stack.size()), currFrame, currFrame.frame, currFrame.frame.getMethod(),
					method, (objReturn != null) ? objReturn.getClass() : "null"));
		}

		for (final JdwpEventRequest evReq : eventRequests.values()) {
			if (evReq.getEventType() != VMEventType.METHOD_EXIT
					&& evReq.getEventType() != VMEventType.METHOD_EXIT_WITH_RETURN_VALUE) {
				continue;
			}
			final VMThreadID curThreadId = vm.getThreadId(Thread.currentThread());
			boolean isOk = true;
			for (final JdwpEventModifier mod : evReq.getModifiers()) {
				if (mod.getModKind() == ModKind.THREAD_ONLY) {
					final JdwpModifierThreadOnly modThread = (JdwpModifierThreadOnly) mod;
					if (!modThread.getThreadId().equals(curThreadId)) {
						LOG.debug(String.format("methodExit: other thread, %s != %s", curThreadId, modThread.getThreadId()));
						isOk = false;
						break;
					}
				}
				else if (mod.getModKind() == ModKind.CLASS_ONLY) {
					final JdwpModifierClassOnly modClass = (JdwpModifierClassOnly) mod;
					final Class<?> modClazz = (Class<?>) vm.getVMObject(modClass.getClazz());
					if (!currClass.equals(modClazz)) {
						LOG.debug(String.format("methodExit: other class, %s != %s", currClass, modClazz));
						isOk = false;
						break;
					}
				}
				else {
					throw new DebuggerException(String.format("Unexpected modifier (%s) in event 0x%x of type %s",
							mod, Integer.valueOf(evReq.getRequestId()), evReq.getEventType()));
				}
			}
			if (isOk) {
				LOG.debug(String.format("methodExit: send method-exit-event (method=%s, objReturn.class=%s)",
						method, (objReturn != null) ? objReturn.getClass() : "null"));
				final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
				if (evReq.getEventType() == VMEventType.METHOD_EXIT) {
					sendThreadLocEvent(suspendPolicy, evReq, curThreadId, frame);
				}
				else {
					sendThreadLocEvent(suspendPolicy, evReq, curThreadId, frame, true, objReturn);
				}

				// Gives control to the debugger.
				giveDebuggerControl(curThreadId, suspendPolicy);
			}
		}
		
		final JdwpEventRequest evReqStep = currFrame.eventRequestStep;
		final JdwpModifierStep modStep = currFrame.modStep;

		final JdwpEventRequest evReqStepOut = currFrame.eventRequestStepUp;
		final JdwpModifierStep modStepOut = currFrame.modStepUp;

		stack.pollFirst();
		currFrame = stack.peekFirst();
		if (currFrame == null) {
			LOG.debug("Bottom of stack and therefore end of debugging-code reached.");
		}
		else {
			if (methodCounter.get() <= maxMethodsLogged) {
				LOG.debug(String.format("methodExit to %s", currFrame.frame.getMethod()));
			}
			if (evReqStepOut != null) {
				// We leaved the method and should send a step-out-event.
				LOG.debug(String.format("methodExit: found step-out-request eventRequestStepUp=%s, modStep=%s",
						evReqStepOut, modStepOut));
				currFrame.eventRequestStep = evReqStepOut;
				currFrame.modStep = modStepOut;
			}
			else if (evReqStep != null) {
				// We leaved the method and still have a step-event.
				LOG.debug(String.format("methodExit: found step-request eventRequestStep=%s, modStep=%s",
						evReqStep, modStep));
				currFrame.eventRequestStep = evReqStep;
				currFrame.modStep = modStep;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExitBack(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn) {
		if (currFrame != null) {
			JdwpEventRequest evReqStepOut = null;
			if (currFrame.eventRequestStep != null || currFrame.modStep != null) {
				LOG.debug(String.format("methodExit: currFrame.eventRequestStep=%s, currFrame.modStep=%s",
						currFrame.eventRequestStep, currFrame.modStep));
			}
			if (currFrame.eventRequestStep != null && currFrame.modStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_OUT) {
				// We leaved the method and should send a step-out-event.
				evReqStepOut = currFrame.eventRequestStep;
			}
			if (evReqStepOut != null) {
				LOG.debug(String.format("methodExitBack: stepOut of evreq 0x%x recognized in %s",
						Integer.valueOf(evReqStepOut.getRequestId()), method));
				final JdwpEventRequest evReq = evReqStepOut;
				final VMThreadID threadId = vm.getCurrentThreadId();
				final MethodFrame curMFrame = currFrame.frame;
				LOG.debug(String.format("Step-Event: method=%s, instr=%d, line=%d",
						curMFrame.getMethod().getName(),
						Long.valueOf(curMFrame.instrNum),
						Integer.valueOf(curMFrame.getCurrLineNum())));
				sendThreadLocEvent(evReq.getSuspendPolicy(), evReq, threadId, curMFrame);
	
				// Gives control to the debugger.
				giveDebuggerControl(threadId, evReqStepOut.getSuspendPolicy());
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitInstruction(AbstractInsnNode instr, OperandStack opStack, Object[] aLocals) {
		JdwpEventRequest currStepReq = null;
		JdwpModifierStep currModStep = null;
		if (instrCounter.incrementAndGet() < maxInstrLogged) {
			LOG.debug(String.format("visitInstruction: line=%d, index=%d, %s",
					Integer.valueOf(currFrame.frame.getCurrLineNum()),
					Integer.valueOf(currFrame.frame.instrNum),
					InstructionVisitor.displayInstruction(instr, currFrame.frame.getMethodNode())));
		}
		final int opcode = instr.getOpcode();
		if (opcode < 0 && !(instr instanceof LineNumberNode)) {
			// Suspend at real instructions or line-nodes only.
			// No suspend at frame- or label-nodes.
			return;
		}
		if (currFrame.eventRequestStep == null) {
stepSearch:
			for (JdwpEventRequest evReq : eventRequests.values()) {
				if (evReq.getEventType() == VMEventType.SINGLE_STEP) {
					for (JdwpEventModifier mod : evReq.getModifiers()) {
						if (mod.getModKind() == ModKind.STEP) {
							currStepReq = evReq;
							currModStep = (JdwpModifierStep) mod;
							break stepSearch;
						}
					}
					break;
				}
			}
		}
		if (currFrame.eventRequestStep != null
				|| (currStepReq != null && currModStep != null
					&& currModStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_INTO)) {
			final JdwpEventRequest evReq;
			final JdwpModifierStep modStep;
			if (currFrame.eventRequestStep != null) {
				evReq = currFrame.eventRequestStep;
				modStep = currFrame.modStep;
			}
			else {
				evReq = currStepReq;
				modStep = currModStep;
			}
			if (modStep == null) {
				// modStep can't be null here.
				throw new JvmException(String.format("Internal error: evReq (%s) but modStep == null (currFrame=%s, currModStep=%s)",
						evReq, currFrame, currModStep));
			}
			if (evReq == null) {
				// evReq can't be null here.
				throw new JvmException(String.format("Internal error: evReq == null (currFrame=%s, currModstep=%s)",
						currFrame, currModStep));
			}
			
			if ((modStep.getStepSize() == JdwpModifierStep.STEP_SIZE_MIN && modStep.getStepDepth() != JdwpModifierStep.STEP_DEPTH_OUT)
					|| (modStep.getStepSize() == JdwpModifierStep.STEP_SIZE_LINE && currFrame.frame.getCurrLineNum() != currFrame.stepLine && modStep.getStepDepth() != JdwpModifierStep.STEP_DEPTH_OUT)
					|| (currStepReq != null && modStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_INTO)) {
				final VMThreadID threadId = vm.getCurrentThreadId();
				final MethodFrame curMFrame = currFrame.frame;
				LOG.debug(String.format("Step-Event: reqId=0x%x, method=%s, instr=%d, line=%d",
						Integer.valueOf(evReq.getRequestId()),
						curMFrame.getMethod().getName(),
						Long.valueOf(curMFrame.instrNum),
						Integer.valueOf(curMFrame.getCurrLineNum())));
				final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
				sendThreadLocEvent(suspendPolicy, evReq, threadId, curMFrame);
				// Remove the event.
				currFrame.eventRequestStep = null;
				currFrame.modStep = null;
				//currFrame.stepLine = curMFrame.getCurrLineNum();

				// Gives control to the debugger.
				giveDebuggerControl(threadId, suspendPolicy);
				return;
			}
		}

		for (JdwpEventRequest evReq : eventRequests.values()) {
			final VMEventType eventType = evReq.getEventType();
			if (eventType == VMEventType.BREAKPOINT) {
				for (JdwpEventModifier modifier : evReq.getModifiers()) {
					if (modifier.getModKind() != ModKind.LOCATION_ONLY) {
						continue;
					}
					final JdwpModifierLocationOnly bp = (JdwpModifierLocationOnly) modifier;
					final Executable currMethod = currFrame.frame.getMethod();
					final Object evMethod = vm.getVMObject(bp.getMethodId());
					// LOG.debug(String.format("instruction, Event %s: Check location currMethod=%s, evMethod=%s", evReq, currMethod, evMethod));
					if (currMethod.equals(evMethod)) {
						// We have found the right method.
						if (currFrame.frame.instrNum == bp.getIndex()) {
							// We are at the wanted index.
							final VMThreadID threadId = vm.getCurrentThreadId();
							final VMByte typeTag = new VMByte(bp.getTypeTag());
							final VMLong vIndex = new VMLong(bp.getIndex());
							final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
							LOG.debug(String.format("Breakpoint reached: method=%s, instrNum=%d, line=%d, suspPolicy=%s",
									currMethod.getName(),
									Long.valueOf(bp.getIndex()), Integer.valueOf(currFrame.frame.getCurrLineNum()),
									suspendPolicy));
							try {
								if (LOG.isDebugEnabled()) {
									LOG.debug(String.format("sendVMEvent: sP=%s, type=BREAKPOINT, reqId=0x%x, threadId=%s, typeTag=%s, classId=%s, methodId=%s, vIndex=%s",
											suspendPolicy, Integer.valueOf(evReq.getRequestId()), threadId,
											typeTag, bp.getClassID(), bp.getMethodId(), vIndex));
								}
								debugger.sendVMEvent(suspendPolicy, VMEventType.BREAKPOINT,
										new VMInt(evReq.getRequestId()), threadId,
										typeTag, bp.getClassID(), bp.getMethodId(), vIndex);
							} catch (IOException e) {
								throw new RuntimeException("IO-error while talking with the debugger (breakopint)", e);
							}
							giveDebuggerControl(threadId, suspendPolicy);
						}
					}
				}
			}
			else if ((eventType == VMEventType.FIELD_ACCESS
						&& (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC))
					|| (eventType == VMEventType.FIELD_MODIFICATION
						&& (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC))) {
				// Check for an watchpoint event.
				final FieldInsnNode fi = (FieldInsnNode) instr;
				final String nameFiOwner = fi.owner.replace('/', '.');
				Class<?> oClass = null;
				if (opcode == Opcodes.GETFIELD) {
					final Object obj = opStack.peek();
					if (obj != null) {
						oClass = obj.getClass();
					}
				}
				else if (opcode == Opcodes.PUTFIELD) {
					final Object obj = opStack.peek(1);
					if (obj != null) {
						oClass = obj.getClass();
					}
				}
				else {
					try {
						oClass = vm.loadClass(nameFiOwner, currFrame.frame.clazz);
					} catch (ClassNotFoundException e) {
						LOG.error(String.format("Can't find class (%s) of field (%s)", nameFiOwner, fi.name));
					}
				}
				final Executable currMethod = currFrame.frame.getMethod();
				for (JdwpEventModifier modifier : evReq.getModifiers()) {
					if (oClass == null) {
						break;
					}
					if (modifier.getModKind() != ModKind.FIELD_ONLY) {
						continue;
					}
					JdwpModifierFieldOnly fieldOnly = (JdwpModifierFieldOnly) modifier;
					final Class<?> clazz = fieldOnly.getClazz();
					if (oClass.equals(clazz) && fieldOnly.getFieldName().equals(fi.name)) {
						// We detected a field-access.
						final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
						LOG.debug(String.format("field access: clazz=%s, fieldName=%s, method=%s, line=%d, suspPolicy=%s",
								clazz, fi.name, currMethod.getName(),
								Integer.valueOf(currFrame.frame.getCurrLineNum()),
								suspendPolicy));
						final VMThreadID threadId = vm.getCurrentThreadId();
						final VMByte typeTag = new VMByte(TypeTag.CLASS.getTag());
						final VMMethodID methodId = vm.getMethodId(currFrame.frame.getMethod());
						final VMLong vIndex = new VMLong(currFrame.frame.instrNum);
						try {
							if (LOG.isDebugEnabled()) {
								LOG.debug(String.format("sendVMEvent: sP=%s, type=%s, reqId=0x%x, refId=%s, fieldId=%s",
										suspendPolicy, eventType, Integer.valueOf(evReq.getRequestId()),
										fieldOnly.getClassId(), fieldOnly.getFieldId()));
							}
							debugger.sendVMEvent(suspendPolicy, eventType,
									new VMInt(evReq.getRequestId()), threadId,
									typeTag, fieldOnly.getClassId(), methodId, vIndex);
						} catch (IOException e) {
							throw new RuntimeException("IO-error while talking with the debugger (breakopint)", e);
						}
						giveDebuggerControl(threadId, suspendPolicy);
					}
				}
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public Object visitFieldAccess(final int opcode, final Object owner, final Field field, final Object value) {
		return value;
	}

	/**
	 * Gives control to the debugger.
	 * In case of suspend-policy EVENT_THREAD the current thread will be suspended.
	 * In case of suspend-policy ALL the vm will be suspended.
	 * @param curThreadId current thread-id
	 * @param suspendPolicy suspend-policy
	 */
	private void giveDebuggerControl(final VMThreadID curThreadId, final JdwpSuspendPolicy suspendPolicy) {
		if (isProcessingPackets.get()) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("giveDebuggerControl: we are already processing packets");
			}
		}
		else {
			if (suspendPolicy == JdwpSuspendPolicy.EVENT_THREAD) {
				boolean isKnown = vm.suspendThread(curThreadId);
				if (!isKnown) {
					throw new DebuggerException(String.format("The thread %s/%s is not known in the suspend-map",
							curThreadId, vm.getVMObject(curThreadId)));
				}
			}
			else if (suspendPolicy == JdwpSuspendPolicy.ALL) {
				vm.suspend();
			}
			debuggerProcessPackets();
		}
	}

	/**
	 * Sends a single-step-event or method-event to the debugger.
	 * @param suspendPolicy suspend-policy
	 * @param evReq event-request
	 * @param threadId thread-id
	 * @param curMFrame current method-frame
	 */
	private void sendThreadLocEvent(final JdwpSuspendPolicy suspendPolicy,
			final JdwpEventRequest evReq, final VMThreadID threadId,
			final MethodFrame curMFrame) {
		sendThreadLocEvent(suspendPolicy, evReq, threadId, curMFrame, false, null);
	}

	/**
	 * Sends a single-step-event or method-event to the debugger.
	 * @param suspendPolicy suspend-policy
	 * @param evReq event-request
	 * @param threadId thread-id
	 * @param curMFrame current method-frame
	 * @param hasReturnObj <code>true</code> in case of a return-object
	 * @param objReturn return-value
	 */
	private void sendThreadLocEvent(final JdwpSuspendPolicy suspendPolicy,
			final JdwpEventRequest evReq, final VMThreadID threadId,
			final MethodFrame curMFrame, final boolean hasReturnObj, final Object objReturn) {
		try {
			final Class<?> frameClass = curMFrame.getFrameClass();
			try {
				vm.loadClass(frameClass.getName(), frameClass);
			} catch (ClassNotFoundException e) {
				throw new DebuggerException(String.format("Can't load frame-class (%s)", frameClass));
			}
			final String signature = Type.getDescriptor(frameClass);
			final List<RefTypeBean> refTypeBeans = vm.getClassesBySignature(signature);
			if (refTypeBeans.size() == 0) {
				throw new DebuggerException(String.format("Can't find classes by signature (%s)", signature));
			}
			final RefTypeBean refTypeBean = refTypeBeans.get(0);
			final VMDataField typeTag = new VMByte(refTypeBean.getTypeTag().getTag());
			final VMDataField classId = refTypeBean.getTypeID();
			final VMDataField methodId = vm.getMethodId(curMFrame.getMethod());
			if (methodId == null) {
				throw new DebuggerException(String.format("Unknown method (%s) of class (%s) in line %d",
						curMFrame.getMethod(), frameClass, Integer.valueOf(curMFrame.getCurrLineNum())));
			}
			final VMDataField vIndex = new VMLong(curMFrame.instrNum);
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("sendVMEvent: suspPol=%s, type=%s, reqId=0x%x, thread=%s, typeTag=%s, classId=%s, methodId=%s, vIndex=%d, hasReturnObj=%s",
						suspendPolicy, evReq.getEventType(), Integer.valueOf(evReq.getRequestId()),
						refTypeBean.getTypeTag(), threadId, classId, methodId,
						Long.valueOf(curMFrame.instrNum), Boolean.toString(hasReturnObj)));
			}
			if (hasReturnObj && currFrame.frame.getMethod() instanceof Method) {
				Class<?> typeValue = ((Method) currFrame.frame.getMethod()).getReturnType();
				final VMValue vmReturn = vm.getVMValue(typeValue, objReturn);
				debugger.sendVMEvent(suspendPolicy, evReq.getEventType(),
						new VMInt(evReq.getRequestId()), threadId,
						typeTag, classId, methodId, vIndex, vmReturn);
			}
			else {
				debugger.sendVMEvent(suspendPolicy, evReq.getEventType(),
						new VMInt(evReq.getRequestId()), threadId,
						typeTag, classId, methodId, vIndex);
			}
		} catch (IOException e) {
			throw new DebuggerException("IO-error while talking with the debugger (breakopint)", e);
		}
	}

	/**
	 * Gives control to the debugger, processes debugger-commands.
	 */
	private void debuggerProcessPackets() {
		try {
			debugger.processPackets();
		} catch (IOException e) {
			throw new RuntimeException("IO-error while talking with the debugger (processPackets)", e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void invokeException(Throwable e) {
		if (instrCounter.get() < maxInstrLogged) {
			LOG.debug("invokeException: " + e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEnter(final Object objMonitor) {
		LOG.debug("monitorEnter: " + objMonitor);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEntered(final Object objMonitor, final Integer counter) {
		LOG.debug("monitorEntered: " + objMonitor);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorExit(final Object objMonitor, final Integer counter) {
		LOG.debug("monitorExit: " + objMonitor);
	}

	/**
	 * Cancels all events.
	 */
	public void cancelAllEvents() {
		final MethodFrameDebugContext frame = currFrame;
		if (frame != null) {
			frame.eventRequestStep = null;
		}
		eventRequests.clear();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// nothing to do here.
	}

}
