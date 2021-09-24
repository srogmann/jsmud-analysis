package org.rogmann.jsmud.datatypes;

/**
 * Tagged Object-id.
 */
public class VMTaggedObjectId extends VMValue {
	
	/** null-object */
	public static final VMTaggedObjectId NULL = new VMTaggedObjectId(new VMObjectID(0l));

	/**
	 * Constructor
	 * @param value value of VMObjectID
	 */
	public VMTaggedObjectId(final VMObjectID value) {
		super(Tag.OBJECT.getTag(), value);
	}

	/**
	 * Constructor
	 * @param tag JDWP-tag
	 * @param value value of VMObjectID
	 */
	public VMTaggedObjectId(final Tag tag, final VMObjectID value) {
		super(tag.getTag(), value);
	}

}
