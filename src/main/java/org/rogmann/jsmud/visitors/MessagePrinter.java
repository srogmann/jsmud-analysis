package org.rogmann.jsmud.visitors;

/**
 * Interface used for printing lines or stacktraces.
 */
public interface MessagePrinter {

	/** Prints a message */
	void println(final String msg);

	/** Dumps a stacktrace */
	void dump(final Throwable e);
}
