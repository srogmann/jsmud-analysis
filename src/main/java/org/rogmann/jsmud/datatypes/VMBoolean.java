package org.rogmann.jsmud.datatypes;

/**
 * boolean VM-value.
 */
public class VMBoolean extends VMByte {

	/**
	 * Constructor
	 * @param value byte-value
	 */
	public VMBoolean(final boolean b) {
		super(b ? (byte) 1 : 0);
	}
	
}
