package org.rogmann.jsmud.replydata;

/**
 * Tag of a reference-type.
 */
public enum TypeTag {

	/** ReferenceType is a class */
	CLASS(1),
	/** ReferenceType is an interface */
	INTERFACE(2),
	/** ReferenceType is an array */
	ARRAY(3);
	
	private final byte tag;
	
	/**
	 * Internal constructor
	 * @param tag tag
	 */
	private TypeTag(final int tag) {
		this.tag = (byte) tag;
	}
	
	/**
	 * Tag.
	 * @return tag of the type
	 */
	public byte getTag() {
		return tag;
	}
	
	/**
	 * Looks up an type-tag by tag.
	 * @param bTag tag-byte
	 * @return tag-type or <code>null</code>
	 */
	public static TypeTag lookupByKind(byte bTag) {
		for (TypeTag typeTag : values()) {
			if (typeTag.tag == bTag) {
				return typeTag;
			}
		}
		return null;
	}
}
