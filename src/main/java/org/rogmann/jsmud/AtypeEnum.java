package org.rogmann.jsmud;

public enum AtypeEnum {
	T_BOOLEAN(4, boolean.class),
	T_CHAR(5, char.class),
	T_FLOAT(6, float.class),
	T_DOUBLE(7, double.class),
	T_BYTE(8, byte.class),
	T_SHORT(9, short.class),
	T_INT(10, int.class),
	T_LONG(11, long.class);
	
	/** atype-code */
	private final int fAtype;
	/** primitive-type */
	private final Class<?> fAtypeClass;
	
	/** array of atypes */
	private static final Class<?>[] ATYPES = new Class<?>[12];
	
	static {
		for (AtypeEnum atypeEnum : values()) {
			ATYPES[atypeEnum.fAtype] = atypeEnum.fAtypeClass;
		}
	}
	
	/**
	 * Constructor
	 * @param atype atype-code
	 * @param atypeClass primitive class of atype
	 */
	private AtypeEnum(final int atype, final Class<?> atypeClass) {
		fAtype = atype;
		fAtypeClass = atypeClass;
	}
	
	/**
	 * Gets the atype-code.
	 * @return code
	 */
	public int getAtype() {
		return fAtype;
	}
	
	/**
	 * Gets the primitive type.
	 * @return type
	 */
	public Class<?> getAtypeClass() {
		return fAtypeClass;
	}

	/**
	 * Looks up the primitive type of a atype-code.
	 * @param atypeCode atype-code
	 * @return type
	 */
	public static Class<?> lookupAtypeClass(final int atypeCode) {
		assert atypeCode >= 0 && atypeCode < ATYPES.length : "atype-code " + atypeCode +  "out of range";
		final Class<?> classAtype = ATYPES[atypeCode];
		assert classAtype != null : "unknown atype-code " + atypeCode;
		return classAtype;
	}
}
