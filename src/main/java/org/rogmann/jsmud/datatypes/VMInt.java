package org.rogmann.jsmud.datatypes;

/**
 * 32-bit VM-value.
 */
public class VMInt extends VMDataField {

	/** int-value */
	private final int value;
	
	/**
	 * Constructor
	 * @param value int-value
	 */
	public VMInt(final int value) {
		this.value = value;
	}
	
	/**
	 * Gets the int-value.
	 * @return int
	 */
	public int getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 4;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		buf[offset] = (byte) (value >> 24);
		buf[offset + 1] = (byte) ((value >> 16) & 0xff);
		buf[offset + 2] = (byte) ((value >> 8) & 0xff);
		buf[offset + 3] = (byte) (value & 0xff);
	}

}
