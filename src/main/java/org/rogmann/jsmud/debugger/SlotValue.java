package org.rogmann.jsmud.debugger;

import org.rogmann.jsmud.datatypes.VMValue;

/**
 * Index and value of a variable-slot in a method-frame.
 */
public class SlotValue {

	/** variable's index in the frame */
	private final int slot;
	/** type and value of the variable */
	private final VMValue variable;

	/**
	 * Constructor
	 * @param slot variable's index
	 * @param variable type and value of the variable
	 */
	public SlotValue(int slot, VMValue variable) {
		this.slot = slot;
		this.variable= variable;
	}

	/**
	 * Gets the variable's index in the frame.
	 * @return index
	 */
	public int getSlot() {
		return slot;
	}

	/**
	 * Gets the type and value of the variale.
	 * @return tag and value
	 */
	public VMValue getVariable() {
		return variable;
	}
	
}
