package org.rogmann.jsmud.datatypes;

/**
 * Tagged Object-id.
 */
public class VMTaggedObjectId extends VMValue {

	/**
	 * Constructor
	 * @param value value of VMObjectID
	 */
	public VMTaggedObjectId(final VMObjectID value) {
		super(Tag.OBJECT.getTag(), value);
	}
	
}
