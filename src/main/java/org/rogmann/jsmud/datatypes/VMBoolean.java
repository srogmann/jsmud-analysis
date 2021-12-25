package org.rogmann.jsmud.datatypes;

/**
 * boolean VM-value.
 */
public class VMBoolean extends VMByte {

	/**
	 * Constructor
	 * @param b byte-value of boolean
	 */
	public VMBoolean(final boolean b) {
		super(b ? (byte) 1 : 0);
	}
	
}
