package org.rogmann.jsmud.datatypes;

/**
 * 64-bit VM-value.
 */
public class VMLong extends VMDataField {

	/** long-value */
	private final long value;
	
	/**
	 * Constructor
	 * @param value long-value
	 */
	public VMLong(final long value) {
		this.value = value;
	}
	
	/**
	 * Gets the long-value.
	 * @return long
	 */
	public long getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return 8;
	}
	
	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		buf[offset] = (byte) (value >> 56);
		buf[offset + 1] = (byte) ((value >> 48) & 0xff);
		buf[offset + 2] = (byte) ((value >> 40) & 0xff);
		buf[offset + 3] = (byte) ((value >> 32) & 0xff);
		buf[offset + 4] = (byte) ((value >> 24) & 0xff);
		buf[offset + 5] = (byte) ((value >> 16) & 0xff);
		buf[offset + 6] = (byte) ((value >> 8) & 0xff);
		buf[offset + 7] = (byte) (value & 0xff);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return new StringBuilder(20).append(getClass().getSimpleName()).append('(').append(value).append(')').toString();
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (value ^ (value >>> 32));
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof VMLong)) {
			return false;
		}
		VMLong other = (VMLong) obj;
		if (value != other.value) {
			return false;
		}
		return true;
	}
}
