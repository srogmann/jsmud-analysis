package org.rogmann.jsmud.replydata;

import org.rogmann.jsmud.datatypes.VMFrameID;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;

/**
 * Details of a frame in a frame-stack of a thread.
 */
public class RefFrameBean {

	/** frame-id */
	private final VMFrameID frameId;
	
	/** type-tag */
	private final TypeTag typeTag;
	
	/** type-id */
	private final VMReferenceTypeID typeId;
	
	/** method-id */
	private final VMMethodID methodId;

	/** index in method (VM-dependent) */
	private long index;

	/**
	 * Constructor
	 * @param frameId frame-id
	 * @param typeTag type-tag
	 * @param typeId type-id
	 * @param methodId method-id
	 * @param index index in method (VM-dependent)
	 */
	public RefFrameBean(VMFrameID frameId, TypeTag typeTag, VMReferenceTypeID typeId, VMMethodID methodId, long index) {
		assert frameId != null;
		assert typeTag != null;
		assert typeId != null;
		assert methodId != null;

		this.frameId = frameId;
		this.typeTag = typeTag;
		this.typeId = typeId;
		this.methodId = methodId;
		this.index = index;
	}

	/**
	 * Gets the frame-id
	 * @return frame-id
	 */
	public VMFrameID getFrameId() {
		return frameId;
	}

	/**
	 * Gets the type-tag.
	 * @return type-tag
	 */
	public TypeTag getTypeTag() {
		return typeTag;
	}

	/**
	 * Gets the type-id.
	 * @return type-id
	 */
	public VMReferenceTypeID getTypeId() {
		return typeId;
	}

	/**
	 * Gets the method-id.
	 * @return method-id
	 */
	public VMMethodID getMethodId() {
		return methodId;
	}

	/**
	 * Gets the VM-dependent index in the method
	 * @return index
	 */
	public long getIndex() {
		return index;
	}

	/**
	 * Updates the index in the method.
	 * @param index instruction-index
	 */
	public void setIndex(long index) {
		this.index = index;
	}
}
