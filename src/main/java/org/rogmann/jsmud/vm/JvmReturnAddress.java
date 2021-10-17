package org.rogmann.jsmud.vm;

/**
 * value-object returnAddress used in instructions JSR and RET.
 * 
 * <p>JSR and RET where used in pre Java 1.6-finally-implementations.</p>
 * <p>Example: org.apache.xerces.parsers.XML11Configuration</p>
 */
public class JvmReturnAddress {

	/** index of an instruction */
	private final int address;

	public JvmReturnAddress(final int address) {
		this.address = address;
	}

	/**
	 * Gets the address of an instruction.
	 * @return address
	 */
	public int getAddress() {
		return address;
	}
}
