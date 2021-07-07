package org.rogmann.jsmud.datatypes;

/**
 * 16-bit VM-value.
 */
public class VMShort extends VMDataField {

	/** short-value */
	private final short value;
	
	/**
	 * Constructor
	 * @param value short-value
	 */
	public VMShort(final short value) {
		this.value = value;
	}
	
	/**
	 * Gets the short-value.
	 * @return short
	 */
	public short getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 2;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		buf[offset] = (byte) (value >> 8);
		buf[offset + 1] = (byte) (value & 0xff);
	}

}
