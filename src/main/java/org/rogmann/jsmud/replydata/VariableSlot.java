package org.rogmann.jsmud.replydata;

/**
 * Information of a variable in a method.
 */
public class VariableSlot {

	/** first code index at which the variable is visible */
	private final long codeIndex;

	/** variable's name */
	private final String name;
	
	/** JNI-signature of the type of the variable */
	private final String signature;

	/** generic signature or empty string */
	private final String genericSignature;

	/** length of variable-visibility beginning at codeIndex */
	private final int length;

	/** index of variable in its frame */
	private final int slot;

	/**
	 * Constructor
	 * @param codeIndex first code index at which the variable is visible
	 * @param name variable's name
	 * @param signature JNI-signature of the type of the variable
	 * @param genericSignature generic signature or empty string
	 * @param length length of variable-visibility beginning at codeIndex
	 * @param slot index of variable in its frame
	 */
	public VariableSlot(long codeIndex, String name, String signature, String genericSignature, int length, int slot) {
		this.codeIndex = codeIndex;
		this.name = name;
		this.signature = signature;
		this.genericSignature = genericSignature;
		this.length = length;
		this.slot = slot;
	}

	/**
	 * Gets the first code index at which the variable is visible.
	 * @return codeIndex
	 */
	public long getCodeIndex() {
		return codeIndex;
	}

	/**
	 * Gets the variable's name.
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return signature JNI-signature of the type of the variable
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * Gets the generic signature or empty string.
	 * @return genericSignature 
	 */
	public String getGenericSignature() {
		return genericSignature;
	}

	/**
	 * Gets the length of variable-visibility beginning at codeIndex.
	 * @return length
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Gets the slot index of variable in its frame.
	 * @return slot index
	 */
	public int getSlot() {
		return slot;
	}
	
}
