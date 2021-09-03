package org.rogmann.jsmud.debugger;

import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.JvmExecutionVisitorProvider;

/**
 * JVM-debugger-visitor provider.
 */
public class DebuggerJvmVisitorProvider implements JvmExecutionVisitorProvider {
	/** number of instructions to be logged in detail */
	private final int maxInstrLogged;

	/** number of method-invocations to be logged in detail */
	private final int maxMethodsLogged;

	/** optional source-file-requester */
	private final SourceFileRequester sourceFileRequester;

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
	 */
	public DebuggerJvmVisitorProvider(final int maxInstrLogged, final int maxMethodsLogged,
			final SourceFileRequester sourceFileRequester) {
		this.maxInstrLogged = maxInstrLogged;
		this.maxMethodsLogged = maxMethodsLogged;
		this.sourceFileRequester = sourceFileRequester;
	}

	/** {@inheritDoc} */
	@Override
	public JvmExecutionVisitor create(Thread currentThread) {
		return new DebuggerJvmVisitor(maxInstrLogged, maxMethodsLogged, sourceFileRequester);
	}

}
