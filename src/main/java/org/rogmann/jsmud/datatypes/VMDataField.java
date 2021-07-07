package org.rogmann.jsmud.datatypes;

/**
 * VM-data-field.
 */
public abstract class VMDataField {

	/**
	 * Gets the length of the value.
	 * @return length in bytes
	 */
	public abstract int length();

	/**
	 * Writes the field into a buffer.
	 * @param buf buffer
	 * @param offset offset in buffer
	 */
	public abstract void write(byte[] buf, int offset);
}
