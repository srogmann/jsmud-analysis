package org.rogmann.jsmud.vm;

/**
 * Uncaught exception thrown in a method.
 */
public class JvmUncaughtException extends JvmException {

	/** serialization-id */
	private static final long serialVersionUID = 20210811L;

	/**
	 * Constructor
	 * @param message message-text
	 * @param cause uncaught exception
	 */
	public JvmUncaughtException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
