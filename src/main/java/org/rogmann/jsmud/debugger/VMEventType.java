package org.rogmann.jsmud.debugger;

/**
 * Type of a JVM-event.
 */
public enum VMEventType {

	/** Notification of step completion in the target VM. */
	SINGLE_STEP(1),
	/** Notification of a breakpoint in the target VM. */
	BREAKPOINT(2),
	/** Notification of an exception in the target VM. */
	EXCEPTION(4),
	/** Notification of a new running thread in the target VM. */
	THREAD_START(6),
	/** Notification of a completed thread in the target VM. */
	THREAD_DEATH(7),
	/** Notification of a class prepare in the target VM. */
	CLASS_PREPARE(8),
	/** Notification of a class unload in the target VM. */
	CLASS_UNLOAD(9),
	/** Notification of a field modification in the target VM. */
	FIELD_MODIFICATION(21),
	/** Notification of a method invocation in the target VM. */
	METHOD_ENTRY(40),
	/** Notification of a method return in the target VM. */
	METHOD_EXIT(41),
	/** Notification of a method return in the target VM. */
	METHOD_EXIT_WITH_RETURN_VALUE(42),
	/** Notification of initialization of a target VM. */
	VM_START(90),
	/** Notification of initialization of a target VM. */
	VM_DEATH(99);

	/** event-kind */
	private final byte eventKind;
	
	/**
	 * Constructor
	 * @param eventKind event-kind
	 */
	private VMEventType(final int eventKind) {
		this.eventKind = (byte) eventKind;
	}
	
	/**
	 * Gets the event-kind.
	 * @return event-kind
	 */
	public byte getEventKind() {
		return eventKind;
	}

	/**
	 * Looks up an event-type by kind.
	 * @param kind event-kind
	 * @return event-type or <code>null</code>
	 */
	public static VMEventType lookupByKind(byte kind) {
		for (VMEventType type : values()) {
			if (type.eventKind == kind) {
				return type;
			}
		}
		return null;
	}
}
