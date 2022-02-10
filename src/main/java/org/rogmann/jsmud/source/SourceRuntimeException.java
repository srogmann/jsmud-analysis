package org.rogmann.jsmud.source;

/**
 * Exception while analyzing bytecode to display a source-file.
 */
public class SourceRuntimeException extends RuntimeException {

	/** Serialization-Id */
	private static final long serialVersionUID = 20220210L;

	/**
	 * Constructor
	 * @param msg exception-message
	 */
	public SourceRuntimeException(final String msg) {
		super(msg);
	}
	
	/**
	 * Constructor
	 * @param msg exception-message
	 * @param e cause-throwable
	 */
	public SourceRuntimeException(final String msg, Throwable e) {
		super(msg, e);
	}

}
