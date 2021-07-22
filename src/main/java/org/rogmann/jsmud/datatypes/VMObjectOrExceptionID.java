package org.rogmann.jsmud.datatypes;

/**
 * Newly created object or exception.
 */
public class VMObjectOrExceptionID {

	/** object-id */
	private final VMTaggedObjectId vmObjectID;
	/** exception-id */
	private final VMTaggedObjectId vmExceptionID;

	/**
	 * Constructor
	 * @param vmObjectID object-id or <code>null</code>
	 * @param vmExceptionID exception-id or <code>null</code>
	 */
	public VMObjectOrExceptionID(final VMTaggedObjectId vmObjectID, final VMTaggedObjectId vmExceptionID) {
		this.vmObjectID = vmObjectID;
		this.vmExceptionID = vmExceptionID;
	}

	/**
	 * Gets the object-id.
	 * @return tagged-object-id or <code>null</code>
	 */
	public VMTaggedObjectId getVmObjectID() {
		return vmObjectID;
	}

	/**
	 * Gets the exception-id.
	 * @return tagged-object-id or <code>null</code>
	 */
	public VMTaggedObjectId getVmExceptionID() {
		return vmExceptionID;
	}
}
