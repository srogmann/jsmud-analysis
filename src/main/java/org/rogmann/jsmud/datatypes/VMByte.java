package org.rogmann.jsmud.datatypes;

/**
 * 8-bit VM-value.
 */
public class VMByte extends VMDataField {

	/** byte-value */
	private final byte value;
	
	/**
	 * Constructor
	 * @param value byte-value
	 */
	public VMByte(final byte value) {
		this.value = value;
	}
	
	/**
	 * Gets the byte-value.
	 * @return byte
	 */
	public byte getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 1;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return new StringBuilder(20).append(getClass().getSimpleName()).append('(').append(value).append(')').toString();
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		buf[offset] = value;
	}

}
