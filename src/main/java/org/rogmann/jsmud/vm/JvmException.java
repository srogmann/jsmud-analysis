package org.rogmann.jsmud.vm;

/**
 * Unexpected exception while executing bytecode.
 */
public class JvmException extends RuntimeException {

	/** serialization-id */
	private static final long serialVersionUID = 20210501L;

	/**
	 * Constructor
	 * @param message message-text
	 */
	public JvmException(final String message) {
		super(message);
	}

	/**
	 * Constructor
	 * @param message message-text
	 */
	public JvmException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
