package org.rogmann.jsmud.vm;

/**
 * Type of flow after invoking an invocation-handler before executing a
 * constructor or method.
 */
public enum InvokeFlow {

	/** normal continuation (same as <code>null</code>) */
	CONTINUE(false),

	/** the method has been executed (or in some way simulated), execute next instruction */
	EXEC_OK(false),
	
	/** the method has been executed but throw a caught exception, execute catch block */
	EXEC_CATCH(true);

	/** continue-while-flag (<code>false</code> = normal continuation, <code>true</code> = execute catch block) */
	private final boolean continueWhileFlag;

	/**
	 * Internal constructor
	 * @param continueWhileFlag continue-while-flag
	 */
	private InvokeFlow(final boolean continueWhileFlag) {
		this.continueWhileFlag = continueWhileFlag;
	}

	/**
	 * Gets <code>true</code> if the while-loop executing instructions should be continue.
	 * <code>true</code> to skip the instruction increment, execute catch block.
	 * @return
	 */
	public boolean isHandleException() {
		return continueWhileFlag;
	}
}

