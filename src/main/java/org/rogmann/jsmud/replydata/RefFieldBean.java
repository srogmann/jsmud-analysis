package org.rogmann.jsmud.replydata;

import org.rogmann.jsmud.datatypes.VMFieldID;

/**
 * ID, signature and modifiers of a field.
 */
public class RefFieldBean {
	
	/** field-id */
	private final VMFieldID fieldID;
	/** name of the field */
	private final String name;
	/** JNI-signature */
	private final String signature;
	/** generic signature (may be empty if there is none) */
	private final String genericSignature;
	/** modifiers */
	private final int modBits;

	/**
	 * Construtor
	 * @param typeTag type-tag
	 * @param fieldID type-id
	 * @param signature signature
	 * @param genericSignature generic signature, empty if there is none
	 * @param status status of class
	 */
	public RefFieldBean(final VMFieldID fieldID, final String name,
			final String signature, final String genericSignature, final int modBits) {
		this.fieldID = fieldID;
		this.name = name;
		this.signature = signature;
		this.genericSignature = genericSignature;
		this.modBits = modBits;
	}

	/**
	 * Gets the field-id
	 * @return id
	 */
	public VMFieldID getFieldID() {
		return fieldID;
	}

	/**
	 * Gets the name of the field.
	 * @return field's name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the JNI-signature.
	 * @return signature
	 */
	public String getSignature() {
		return signature;
	}
	
	/**
	 * Gets the generic signature. This is empty if there is none.
	 * @return generic signature
	 */
	public String getGenericSignature() {
		return genericSignature;
	}

	/**
	 * Gets the modifiers.
	 * @return mod-bits
	 */
	public int getModBits() {
		return modBits;
	}
}
