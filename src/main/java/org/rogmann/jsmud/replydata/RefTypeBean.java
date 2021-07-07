package org.rogmann.jsmud.replydata;

import org.rogmann.jsmud.datatypes.VMReferenceTypeID;

/**
 * Type, ID and status of a reference-type.
 */
public class RefTypeBean {
	
	/** class-status "verified" */
	public static final int STATUS_VERIFIED = 1;
	/** class-status "prepared" */
	public static final int STATUS_PREPARED = 2;
	/** class-status "initialized" */
	public static final int STATUS_INITIALIZED = 4;
	/** class-status "error" */
	public static final int STATUS_ERROR = 8;

	/** type-tag */
	private final TypeTag typeTag;
	/** type-id */
	private final VMReferenceTypeID typeID;
	/** JNI-signature */
	private final String signature;
	/** generic signature (may be empty if there is none) */
	private final String genericSignature;
	/** current status */
	private final int status;

	/**
	 * Construtor
	 * @param typeTag type-tag
	 * @param typeID type-id
	 * @param signature signature
	 * @param genericSignature generic signature, empty if there is none
	 * @param status status of class
	 */
	public RefTypeBean(TypeTag typeTag, VMReferenceTypeID typeID, String signature, String genericSignature, int status) {
		this.typeTag = typeTag;
		this.typeID = typeID;
		this.signature = signature;
		this.genericSignature = genericSignature;
		this.status = status;
	}

	/**
	 * Gets the type-tag.
	 * @return type-tag
	 */
	public TypeTag getTypeTag() {
		return typeTag;
	}
	
	/**
	 * Gets the type-id
	 * @return id
	 */
	public VMReferenceTypeID getTypeID() {
		return typeID;
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
	 * Gets the class' status.
	 * @return status
	 */
	public int getStatus() {
		return status;
	}
}
