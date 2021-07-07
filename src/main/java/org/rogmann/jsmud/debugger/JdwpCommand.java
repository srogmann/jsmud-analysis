package org.rogmann.jsmud.debugger;

/**
 * jwdp-command.
 */
public enum JdwpCommand {

	/** Version Command */
	VERSION(JdwpCommandSet.VIRTUAL_MACHINE, 1),
	/** ClassesBySignature Command */
	CLASSES_BY_SIGNATURE(JdwpCommandSet.VIRTUAL_MACHINE, 2),
	/** AllThreads Command */
	ALL_THREADS(JdwpCommandSet.VIRTUAL_MACHINE, 4),
	/** TopLevelThreadGroups Command */
	TOP_LEVEL_THREAD_GROUPS(JdwpCommandSet.VIRTUAL_MACHINE, 5),
	/** Dispose Command */
	DISPOSE(JdwpCommandSet.VIRTUAL_MACHINE, 6),
	/** IDSizes Command */
	IDSIZES(JdwpCommandSet.VIRTUAL_MACHINE, 7),
	/** Suspend Command */
	SUSPEND(JdwpCommandSet.VIRTUAL_MACHINE, 8),
	/** Resume Command */
	RESUME(JdwpCommandSet.VIRTUAL_MACHINE, 9),
	/** CreateString Command */
	CREATE_STRING(JdwpCommandSet.VIRTUAL_MACHINE, 11),
	/** ClassPaths Command */
	CLASS_PATHS(JdwpCommandSet.VIRTUAL_MACHINE, 13),
	/** CapabilitiesNew Command  */
	CAPABILITIES_NEW(JdwpCommandSet.VIRTUAL_MACHINE, 17),
	/** AllClassesWithGeneric Command  */
	ALL_CLASSES_WITH_GENERIC(JdwpCommandSet.VIRTUAL_MACHINE, 20),

	/** Signature Command */
	SIGNATURE(JdwpCommandSet.REFERENCE_TYPE, 1),
	/** ClassLoader Command */
	CLASS_LOADER(JdwpCommandSet.REFERENCE_TYPE, 2),
	/** Modifiers Command */
	MODIFIERS(JdwpCommandSet.REFERENCE_TYPE, 3),
	/** SourceFile Command */
	SOURCE_FILE(JdwpCommandSet.REFERENCE_TYPE, 7),
	/** Interfaces Command */
	INTERFACES(JdwpCommandSet.REFERENCE_TYPE, 10),
	/** SourceDebugExtension Command */
	SOURCE_DEBUG_EXTENSION(JdwpCommandSet.REFERENCE_TYPE, 12),
	/** SignatureWithGeneric Command */
	SIGNATURE_WITH_GENERIC(JdwpCommandSet.REFERENCE_TYPE, 13),
	/** FieldsWithGeneric Command */
	FIELDS_WITH_GENERIC(JdwpCommandSet.REFERENCE_TYPE, 14),
	/** MethodsWithGeneric Command */
	METHODS_WITH_GENERIC(JdwpCommandSet.REFERENCE_TYPE, 15),

	/** Superclass Command */
	SUPERCLASS(JdwpCommandSet.CLASS_TYPE, 1),

	/** LineTable Command */
	LINE_TABLE(JdwpCommandSet.METHOD, 1),
	/** VarialeTableWithGenerics Command */
	VARIABLE_TABLE_WITH_GENERICS(JdwpCommandSet.METHOD, 5),

	/** ReferenceType Command */
	REFERENCE_TYPE(JdwpCommandSet.OBJECT_REFERENCE, 1),
	/** GetValues Command */
	GET_VALUES(JdwpCommandSet.OBJECT_REFERENCE, 2),
	/** InvokeMethod Command */
	INVOKE_METHOD(JdwpCommandSet.OBJECT_REFERENCE, 6),
	/** IsCollected Command */
	IS_COLLECTED(JdwpCommandSet.OBJECT_REFERENCE, 9),

	/** StringReference Value Command */ 
	STRING_REFERENCE_VALUE(JdwpCommandSet.STRING_REFERENCE, 1),

	/** Name Command: Returns the thread name */
	THREAD_NAME(JdwpCommandSet.THREAD_REFERENCE, 1),
	/** Suspend Command */
	THREAD_SUSPEND(JdwpCommandSet.THREAD_REFERENCE, 2),
	/** Resume Command */
	THREAD_RESUME(JdwpCommandSet.THREAD_REFERENCE, 3),
	/** Status Command */
	THREAD_STATUS(JdwpCommandSet.THREAD_REFERENCE, 4),
	/** ThreadGroup Command */
	THREAD_THREAD_GROUP(JdwpCommandSet.THREAD_REFERENCE, 5),
	/** Frames Command */
	THREAD_FRAMES(JdwpCommandSet.THREAD_REFERENCE, 6),
	/** FrameCount Command */
	THREAD_FRAME_COUNT(JdwpCommandSet.THREAD_REFERENCE, 7),
	/** OwnedMonitors Command */
	THREAD_OWNED_MONITORS(JdwpCommandSet.THREAD_REFERENCE, 8),
	/** CurrentContendedMonitor Command */
	THREAD_CURRENT_CONTENDED_MONITOR(JdwpCommandSet.THREAD_REFERENCE, 9),
	/** Interrupt Command */
	THREAD_INTERRUPT(JdwpCommandSet.THREAD_REFERENCE, 11),
	/** SuspendCount Command */
	THREAD_SUSPEND_COUNT(JdwpCommandSet.THREAD_REFERENCE, 12),

	/** Name Command: Returns the thread group name */
	THREAD_GROUP_NAME(JdwpCommandSet.THREAD_GROUP_REFERENCE, 1),
	/** Parent Command: Returns the thread group, if any, which contains a given thread group */
	THREAD_GROUP_PARENT(JdwpCommandSet.THREAD_GROUP_REFERENCE, 2),
	/** Children Command: Returns live threads and active thread groups */
	THREAD_GROUP_CHILDREN(JdwpCommandSet.THREAD_GROUP_REFERENCE, 3),

	/** Length Command */
	ARRAY_LENGTH(JdwpCommandSet.ARRAY_REFERENCE, 1),
	/** GetValues Command */
	ARRAY_GET_VALUES(JdwpCommandSet.ARRAY_REFERENCE, 2),
	/** SetValues Command */
	ARRAY_SET_VALUES(JdwpCommandSet.ARRAY_REFERENCE, 3),

	/** Set Command */
	SET(JdwpCommandSet.EVENT_REQUEST, 1),
	/** Clear Command */
	CLEAR(JdwpCommandSet.EVENT_REQUEST, 2),

	/** GetValues Command */
	STACK_FRAME_GET_VALUES(JdwpCommandSet.STACK_FRAME, 1),
	/** SetValues Command */
	STACK_FRAME_SET_VALUES(JdwpCommandSet.STACK_FRAME, 2),
	/** ThisObject Command */
	STACK_FRAME_THIS_OBJECT(JdwpCommandSet.STACK_FRAME, 3),

	/** Composite Command */
	COMPOSITE(JdwpCommandSet.EVENT, 100);
	
	/** command-set */
	private final byte commandSet;
	/** command */
	private final byte command;
	
	/**
	 * Internal constructor
	 * @param jcs command-se
	 * @param command command
	 */
	private JdwpCommand(final JdwpCommandSet jcs, final int command) {
		this.commandSet = jcs.getCommandSet();
		this.command = (byte) command;
	}
	
	/**
	 * Gets the command-set of the command.
	 * @return command-set
	 */
	public byte getCommandSet() {
		return commandSet;
	}

	/**
	 * Gets the command.
	 * @return command
	 */
	public byte getCommand() {
		return command;
	}
	
	/**
	 * Lookups a command by number.
	 * @param number number
	 * @return command or <code>null</code>
	 */
	public static JdwpCommand lookupByKind(final JdwpCommandSet cs, final byte number) {
		if (cs != null) {
			final byte pCs = cs.getCommandSet();
			for (JdwpCommand cmd : values()) {
				if (pCs == cmd.commandSet && cmd.command == number) {
					return cmd;
				}
				
			}
		}
		return null;
	}
}
