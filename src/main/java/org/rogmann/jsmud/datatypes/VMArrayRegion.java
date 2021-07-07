package org.rogmann.jsmud.datatypes;

/**
 * Region of an array.
 */
public class VMArrayRegion extends VMDataField {

	/** type of the array */
	private Tag tag;
	/** values */
	private VMDataField[] values;

	/**
	 * Constructor
	 * @param tag type of the array
	 * @param values values
	 */
	public VMArrayRegion(final Tag tag, final VMDataField[] values) {
		this.tag = tag;
		this.values = values;
	}
	
	/**
	 * Gets the type of the value.
	 * @return tag
	 */
	public Tag getTag() {
		return tag;
	}
	
	/**
	 * Gets the values.
	 * @return values
	 */
	public VMDataField[] getValue() {
		return values;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		int len = 1 + 4;
		for (VMDataField vmDataField : values) {
			len += vmDataField.length();
		}
		return len;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int pOffset) {
		int offset = pOffset;
		buf[offset] = tag.getTag();
		offset++;

		final VMInt vmLen = new VMInt(values.length);
		vmLen.write(buf, offset);
		offset += 4;

		for (VMDataField value : values) {
			value.write(buf, offset);
			offset += value.length();
		}
	}
}
