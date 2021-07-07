package org.rogmann.jsmud.debugger;

/**
 * Exception while executing the debugger.
 */
public class DebuggerException extends RuntimeException {

	/** Serialization-Id */
	private static final long serialVersionUID = 20210331L;

	/**
	 * Constructor
	 * @param msg exception-message
	 */
	public DebuggerException(final String msg) {
		super(msg);
	}
	
	/**
	 * Constructor
	 * @param msg exception-message
	 * @param e cause-throwable
	 */
	public DebuggerException(final String msg, Throwable e) {
		super(msg, e);
	}

}
