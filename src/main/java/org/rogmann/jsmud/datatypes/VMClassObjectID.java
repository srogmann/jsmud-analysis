package org.rogmann.jsmud.datatypes;

import java.nio.charset.StandardCharsets;

/**
 * UTF-8-string.
 */
public class VMClassObjectID extends VMDataField {

	/** string-value */
	private final String value;
	
	/** UTF-8-value */
	private final byte[] bufValue;
	
	/**
	 * Constructor
	 * @param value string-value
	 */
	public VMClassObjectID(final String value) {
		if (value == null) {
			throw new IllegalArgumentException("String null is not valid.");
		}
		this.value = value;
		bufValue = value.getBytes(StandardCharsets.UTF_8);
	}
	
	/**
	 * Gets the string-value.
	 * @return string
	 */
	public String getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 4 + bufValue.length;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		final int len = bufValue.length;
		buf[offset] = (byte) (len >> 24);
		buf[offset + 1] = (byte) ((len >> 16) & 0xff);
		buf[offset + 2] = (byte) ((len >> 8) & 0xff);
		buf[offset + 3] = (byte) (len & 0xff);
		System.arraycopy(bufValue, 0, buf, offset + 4, len);
	}

}
