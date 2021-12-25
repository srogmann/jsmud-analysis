package org.rogmann.jsmud.visitors;

/**
 * Interface used for printing lines or stacktraces.
 */
public interface MessagePrinter {

	/**
	 * Prints a message.
	 * @param msg message to be printed
	 */
	void println(final String msg);

	/**
	 * Dumps a stacktrace.
	 * @param e throwable
	 */
	void dump(final Throwable e);
}
