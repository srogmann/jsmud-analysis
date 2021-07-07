package org.rogmann.jsmud.debugger;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.rogmann.jsmud.ClassRegistry;
import org.rogmann.jsmud.JvmExecutionVisitor;
import org.rogmann.jsmud.MethodFrame;
import org.rogmann.jsmud.OperandStack;
import org.rogmann.jsmud.datatypes.VMByte;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMInt;
import org.rogmann.jsmud.datatypes.VMLong;
import org.rogmann.jsmud.datatypes.VMString;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.events.JdwpEventModifier;
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
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.visitors.InstructionVisitor;

/**
 * JVM-debugger-visitor.
 */
public class DebuggerJvmVisitor implements JvmExecutionVisitor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(DebuggerJvmVisitor.class);

	/** vm-simulator */
	private ClassRegistry vm;

	/** map from request-id to event-request */
	private final ConcurrentMap<Integer, JdwpEventRequest> eventRequests = new ConcurrentHashMap<>();
	
	/** interface of the debugger */
	private JdwpCommandProcessor debugger;

	/** stack of method-frames */
	private final Deque<MethodFrameDebugContext> stack = new ConcurrentLinkedDeque<>();

	/** current method-frame */
	private MethodFrameDebugContext currFrame;
	
	/** number of processed instructions */
	private final AtomicLong instrCounter = new AtomicLong();

	/** <code>true</code> if the debugger is processing packets */
	private final AtomicBoolean isProcessingPackets = new AtomicBoolean(false);
	
	/**
	 * Sets the JVM-simulator.
	 * @param vm simulator
	 */
	public void setJvmSimulator(ClassRegistry vm) {
		this.vm = vm;
	}

	/**
	 * Sets the debugger.
	 * @param debugger debugger
	 */
	public void setDebugger(JdwpCommandProcessor debugger) {
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
					if (currFrame == null) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("STEP-start (req-id %d, not in execution yet): size=%d, depth=%d",
									Integer.valueOf(evReq.getRequestId()),
									Integer.valueOf(modStep.getStepSize()),
									Integer.valueOf(modStep.getStepDepth())));
						}
					}
					else {
						if (modStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_OUT) {
							currFrame.eventRequestStepUp = evReq;
							currFrame.modStepUp = modStep;
						}
						else {
							currFrame.eventRequestStep = evReq;
							currFrame.modStep = modStep;
							currFrame.stepLine = currFrame.frame.currLineNum;
						}
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("STEP-start (req-id %d, method %s): size=%d, depth=%d, startLine=%d",
									Integer.valueOf(evReq.getRequestId()),
									currFrame.frame.getMethod(),
									Integer.valueOf(modStep.getStepSize()),
									Integer.valueOf(modStep.getStepDepth()),
									Integer.valueOf(currFrame.stepLine)));
						}
					}
					break;
				}
			}
		}
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
		LOG.debug("visitLoadClass: " + loadedClass.getName());
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
		if (instrCounter.get() < 50000) {
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
					//eventRequests.remove(Integer.valueOf(evReq.getRequestId()));
					
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
		if (instrCounter.get() < 50000) {
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
			if (instrCounter.get() < 50000) {
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
			LOG.debug(String.format("methodExit: currFrame.eventRequestStep=%s, currFrame.modStep=%s",
					currFrame.eventRequestStep, currFrame.modStep));
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
						Integer.valueOf(curMFrame.currLineNum)));
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
		if (instrCounter.incrementAndGet() < 100) {
			LOG.debug(String.format("visitInstruction: method=%s, line=%d, index=%d, %s",
					currFrame.frame.getMethod(),
					Integer.valueOf(currFrame.frame.currLineNum),
					Integer.valueOf(currFrame.frame.instrNum),
					InstructionVisitor.displayInstruction(instr)));
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
				|| (currStepReq != null && currModStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_INTO)) {
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
			
			if ((modStep.getStepSize() == JdwpModifierStep.STEP_SIZE_MIN && modStep.getStepDepth() != JdwpModifierStep.STEP_DEPTH_OUT)
					|| (modStep.getStepSize() == JdwpModifierStep.STEP_SIZE_LINE && currFrame.frame.currLineNum > currFrame.stepLine && modStep.getStepDepth() != JdwpModifierStep.STEP_DEPTH_OUT)
					|| (currStepReq != null && modStep.getStepDepth() == JdwpModifierStep.STEP_DEPTH_INTO)) {
				final VMThreadID threadId = vm.getCurrentThreadId();
				final MethodFrame curMFrame = currFrame.frame;
				LOG.debug(String.format("Step-Event: reqId=0x%x, method=%s, instr=%d, line=%d",
						Integer.valueOf(evReq.getRequestId()),
						curMFrame.getMethod().getName(),
						Long.valueOf(curMFrame.instrNum),
						Integer.valueOf(curMFrame.currLineNum)));
				final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
				sendThreadLocEvent(suspendPolicy, evReq, threadId, curMFrame);
				// Remove the event.
				currFrame.eventRequestStep = null;
				currFrame.modStep = null;
				//currFrame.stepLine = curMFrame.currLineNum;

				// Gives control to the debugger.
				giveDebuggerControl(threadId, suspendPolicy);
				return;
			}
		}

		for (JdwpEventRequest evReq : eventRequests.values()) {
			if (evReq.getEventType() == VMEventType.BREAKPOINT) {
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
							LOG.debug(String.format("Breakpoint reached: method=%s, instrNum=%d, line=%d",
									currMethod.getName(),
									Long.valueOf(bp.getIndex()), Integer.valueOf(currFrame.frame.currLineNum)));
							final VMThreadID threadId = vm.getCurrentThreadId();
							final VMByte typeTag = new VMByte(bp.getTypeTag());
							final VMLong vIndex = new VMLong(bp.getIndex());
							final JdwpSuspendPolicy suspendPolicy = evReq.getSuspendPolicy();
							try {
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
		}
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
				vm.suspendThread(curThreadId);
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
				vm.loadClass(frameClass.getName());
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
						curMFrame.getMethod(), frameClass, Integer.valueOf(curMFrame.currLineNum)));
			}
			final VMDataField vIndex = new VMLong(curMFrame.instrNum);
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
		if (instrCounter.get() < 100) {
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

}
