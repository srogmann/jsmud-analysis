package org.rogmann.jsmud.vm;

/**
 * Bytecode of a class and its -- possibly remapped -- name.
 */
public class ClassWithName {

	/** name of the class */
	private final String name;
	/** bytecode */
	private final byte[] bytecode;
	
	/**
	 * Constructor
	 * @param name name of the class
	 * @param bytecode bytecode of the class
	 */
	public ClassWithName(String name, byte[] bytecode) {
		this.name = name;
		this.bytecode = bytecode;
	}

	/**
	 * Gets the -- possibly remapped -- name of the class.
	 * @return fully qualified name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the bytecode.
	 * @return bytecode
	 */
	public byte[] getBytecode() {
		return bytecode;
	}
}
