package org.rogmann.jsmud.datatypes;

/**
 * Object-id.
 */
public class VMValue extends VMDataField {

	/** type of the value */
	private byte tag;
	/** value */
	private VMDataField value;

	/**
	 * Constructor
	 * @param tag type of the value
	 * @param value value of VMObjectID
	 */
	public VMValue(final byte tag, final VMDataField value) {
		this.tag = tag;
		this.value = value;
	}
	
	/**
	 * Gets the type of the value.
	 * @return tag
	 */
	public byte getTag() {
		return tag;
	}
	
	/**
	 * Gets the value
	 * @return value
	 */
	public VMDataField getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 1 + value.length();
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		buf[offset] = tag;
		value.write(buf, offset + 1);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return new StringBuilder(20).append(getClass().getSimpleName())
				.append("(tag:").append(Tag.lookupByTag(tag))
				.append(", value:").append(value).append(')').toString();
	}
}
