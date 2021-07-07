package org.rogmann.jsmud.datatypes;

/**
 * 0-bit VM-value.
 */
public class VMVoid extends VMDataField {

	/**
	 * Constructor
	 */
	public VMVoid() {
		// no value
	}
	
	/** {@inheritDoc} */
	@Override
	public int length() {
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int offset) {
		// no value to write
	}

}
