package org.rogmann.jsmud.debugger;

/**
 * Index and tag of a variable-slot in a method-frame.
 */
public class SlotRequest {

	/** variable's index in the frame */
	private final int slot;
	/** type of the variable */
	private final byte tag;

	/**
	 * Constructor
	 * @param slot variable's index
	 * @param tag type of the variable
	 */
	public SlotRequest(int slot, byte tag) {
		this.slot = slot;
		this.tag = tag;
	}

	/**
	 * Gets the variable's index in the frame.
	 * @return index
	 */
	public int getSlot() {
		return slot;
	}

	/**
	 * Gets the type of the variale.
	 * @return tag
	 */
	public byte getTag() {
		return tag;
	}

	
	
}
