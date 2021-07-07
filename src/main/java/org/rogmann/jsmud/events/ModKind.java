package org.rogmann.jsmud.events;

/**
 * Modifier kind.
 */
public enum ModKind {

	/** Limit the requested event */
	COUNT(1),
	/** Conditional on expression */
	CONDITIONAL(2),
	/** Restricts thread */
	THREAD_ONLY(3),
	/** Restricts reference type and subtypes */
	CLASS_ONLY(4),
	/** Restricts class-names (match) */
	CLASS_MATCH(5),
	/** Restricts class-names (exclude) */
	CLASS_EXCLUDE(6),
	/** Restricts location */
	LOCATION_ONLY(7),
	/** Restricts exception */
	EXCEPTION_ONLY(8),
	/** Restricts for a given field */
	FIELD_ONLY(9),
	/** Restricts reported step */
	STEP(10),
	/** Restricts to given object-instance */
	INSTANCE_ONLY(11),
	/** Restricts source-name */
	SOURCE_NAME_MATCH(12);
	
	/** modifier-kind */
	private final byte modKind;
	
	/**
	 * Internal contstructor
	 * @param modKind modifier kind
	 */
	private ModKind(final int modKind) {
		this.modKind = (byte) modKind;
	}
	
	/**
	 * Gets the modifier kind.
	 * @return modifier kind
	 */
	public byte getModKindAsByte() {
		return modKind;
	}
	
	/**
	 * Looks up a modifier-kind.
	 * @param bModKind modKind
	 * @return modifier-kind or <code>null</code>
	 */
	public static ModKind lookupByKind(byte bModKind) {
		for (ModKind modKind : values()) {
			if (modKind.modKind == bModKind) {
				return modKind;
			}
		}
		return null;
	}
}
