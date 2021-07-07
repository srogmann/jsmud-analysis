package org.rogmann.jsmud.replydata;

import org.rogmann.jsmud.datatypes.VMMethodID;

/**
 * ID, signature and modifiers of a method.
 */
public class RefMethodBean {
	
	/** method-id */
	private final VMMethodID methodID;
	/** name of the method */
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
	 * @param methodID type-id
	 * @param signature signature
	 * @param genericSignature generic signature, empty if there is none
	 * @param status status of class
	 */
	public RefMethodBean(final VMMethodID methodID, final String name,
			final String signature, final String genericSignature, final int modBits) {
		this.methodID = methodID;
		this.name = name;
		this.signature = signature;
		this.genericSignature = genericSignature;
		this.modBits = modBits;
	}

	/**
	 * Gets the method-id
	 * @return id
	 */
	public VMMethodID getMethodID() {
		return methodID;
	}

	/**
	 * Gets the name of the method.
	 * @return method's name
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
