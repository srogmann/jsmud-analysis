package org.rogmann.jsmud.vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Uncaught exception thrown in a method.
 */
public class JvmUncaughtException extends JvmException {

	/** serialization-id */
	private static final long serialVersionUID = 20210811L;

	/** stacktrace */
	private final List<StackTraceElement> simStacktrace = new ArrayList<>();

	/**
	 * Constructor
	 * @param message message-text
	 * @param cause uncaught exception
	 */
	public JvmUncaughtException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Gets the simulated stacktrace.
	 * @return stacktrace
	 */
	public List<StackTraceElement> getSimStacktrace() {
		return simStacktrace;
	}
}
