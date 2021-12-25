package org.rogmann.jsmud.events;

import org.rogmann.jsmud.datatypes.VMThreadID;

/**
 * Case ThreadOnly.
 */
public class JdwpModifierThreadOnly extends JdwpEventModifier {

	/** thread-id */
	private VMThreadID threadId;

	/**
	 * Constructor
	 * @param threadId thread-id
	 */
	public JdwpModifierThreadOnly(final VMThreadID threadId) {
		super(ModKind.THREAD_ONLY);
		this.threadId = threadId;
	}

	/**
	 * Gets the thread-id
	 * @return thread-id
	 */
	public VMThreadID getThreadId() {
		return threadId;
	}
}
