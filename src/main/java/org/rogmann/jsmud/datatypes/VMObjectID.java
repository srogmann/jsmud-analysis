package org.rogmann.jsmud.datatypes;

/**
 * Object-id.
 */
public class VMObjectID extends VMLong {

	/**
	 * Constructor
	 * @param objectId object-id
	 */
	public VMObjectID(final long objectId) {
		super(objectId);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return new StringBuilder(20).append(getClass().getSimpleName()).append('(').append("0x").append(Long.toHexString(getValue())).append(')').toString();
	}

}
