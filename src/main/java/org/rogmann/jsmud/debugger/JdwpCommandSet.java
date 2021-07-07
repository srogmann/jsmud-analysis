package org.rogmann.jsmud.debugger;

/**
 * jwdp-command-set.
 */
public enum JdwpCommandSet {

	/** VirtualMachine Command Set */
	VIRTUAL_MACHINE(1),
	/** ReferenceType Command Set */
	REFERENCE_TYPE(2),
	/** ClassType Command Set */
	CLASS_TYPE(3),
	/** Method Command Set */
	METHOD(6),
	/** ObjectReference Command Set */
	OBJECT_REFERENCE(9),
	/** StringReference Command Set */
	STRING_REFERENCE(10),
	/** ThreadReference Command Set */
	THREAD_REFERENCE(11),
	/** ThreadGroupReference Command Set */
	THREAD_GROUP_REFERENCE(12),
	/** ArrayReference Command Set */
	ARRAY_REFERENCE(13),
	/** EventRequest Command Set */
	EVENT_REQUEST(15),
	/** StackFrame Command Set */
	STACK_FRAME(16),
	/** Event Command Set */
	EVENT(64);
	
	/** Command-sets by number */
	private static final JdwpCommandSet[] A_CS = new JdwpCommandSet[65];
	
	/** command-set */
	private final byte commandSet;
	
	static {
		for (JdwpCommandSet cs : values()) {
			A_CS[cs.commandSet] = cs;
			
		}
	}

	/**
	 * Internal constructor
	 * @param cs command-set
	 */
	private JdwpCommandSet(final int cs) {
		commandSet = (byte) cs;
	}
	
	/**
	 * Gets the command-set.
	 * @return command-set
	 */
	public byte getCommandSet() {
		return commandSet;
	}
	
	/**
	 * Lookups a command-set by number.
	 * @param number number
	 * @return command-set or <code>null</code>
	 */
	public static JdwpCommandSet lookupByKind(final byte number) {
		return ((number & 0xff) < A_CS.length) ? A_CS[number] : null; 
	}
}
