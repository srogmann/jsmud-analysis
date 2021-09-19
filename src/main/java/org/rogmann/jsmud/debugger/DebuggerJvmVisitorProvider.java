package org.rogmann.jsmud.debugger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.rogmann.jsmud.events.JdwpEventRequest;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.JvmExecutionVisitorProvider;

/**
 * JVM-debugger-visitor provider.
 */
public class DebuggerJvmVisitorProvider implements JvmExecutionVisitorProvider {
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(DebuggerJvmVisitorProvider.class);
	
	/** number of instructions to be logged in detail */
	private final int maxInstrLogged;

	/** number of method-invocations to be logged in detail */
	private final int maxMethodsLogged;

	/** optional source-file-requester */
	private final SourceFileRequester sourceFileRequester;

	/** map from request-id to event-request */
	private final ConcurrentMap<Integer, JdwpEventRequest> eventRequests = new ConcurrentHashMap<>();

	/**
	 * default-constructor,
	 * the first 100 instructions will be logged.
	 * @param sourceFileRequester optional source-file-requester
	 */
	public DebuggerJvmVisitorProvider(final SourceFileRequester sourceFileRequester) {
		maxInstrLogged = 100;
		maxMethodsLogged = 500;
		this.sourceFileRequester = sourceFileRequester;
	}

	/**
	 * Constructor
	 * @param maxInstrLogged number of instructions to be logged at debug-level
	 * @param maxMethodsLogged number of method-invocations to be logged at debug-level
	 * @param sourceFileRequester optional source-file-requester
	 */
	public DebuggerJvmVisitorProvider(final int maxInstrLogged, final int maxMethodsLogged,
			final SourceFileRequester sourceFileRequester) {
		this.maxInstrLogged = maxInstrLogged;
		this.maxMethodsLogged = maxMethodsLogged;
		this.sourceFileRequester = sourceFileRequester;
	}

	/** {@inheritDoc} */
	@Override
	public JvmExecutionVisitor create(final ClassRegistry vm, final Thread currentThread, final JvmExecutionVisitor visitorParent) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(String.format("create: vm=%s, cT=%s, visitorParent=%s",
					vm, currentThread, visitorParent));
		}
		final DebuggerJvmVisitor visitor = new DebuggerJvmVisitor(eventRequests,
				maxInstrLogged, maxMethodsLogged, sourceFileRequester);
		visitor.setJvmSimulator(vm);
		if (visitorParent instanceof DebuggerJvmVisitor) {
			final DebuggerJvmVisitor debuggerVisitorParent = (DebuggerJvmVisitor) visitorParent;
			visitor.setDebugger(debuggerVisitorParent.getDebugger());
		}
		return visitor;
	}

}
