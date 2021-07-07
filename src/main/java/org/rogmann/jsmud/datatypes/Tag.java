package org.rogmann.jsmud.datatypes;

/**
 * Tag of a value.
 */
public enum Tag {

	ARRAY('[', null),
	BYTE('B', byte.class),
	CHAR('C', char.class),
	OBJECT('L', null),
	FLOAT('F', float.class),
	DOUBLE('D', double.class),
	INT('I', int.class),
	LONG('J', long.class),
	SHORT('S', short.class),
	VOID('V', void.class),
	BOOLEAN('Z', boolean.class),
	STRING('s', null),
	THREAD('t', null),
	THREAD_GROUP('g', null),
	CLASS_LOADER('l', null),
	CLASS_OBJECT('c', null);
	
	/** tag */
	private final byte tag;

	/** class of tag (if primitive) */
	private final Class<?> classTag;

	
	/**
	 * Internal constructor
	 * @param tagChar tar-char
	 */
	private Tag(final char tagChar, final Class<?> classTag) {
		tag = (byte) tagChar;
		this.classTag = classTag;
	}
	
	/**
	 * Gets the tag-byte.
	 * @return tag
	 */
	public byte getTag() {
		return tag;
	}

	/**
	 * Gets the class of a tag (if primitive).
	 * @return class or <code>null</code>
	 */
	public Class<?> getClassTag() {
		return classTag;
	}
	
	/**
	 * Looks for a tag.
	 * @param tag tag-byte
	 * @return tag
	 */
	public static Tag lookupByTag(final byte tag) {
		Tag tTag = null;
		for (final Tag loopTag : values()) {
			if (loopTag.tag == tag) {
				tTag = loopTag;
				break;
			}
		}
		return tTag;
	}
}
