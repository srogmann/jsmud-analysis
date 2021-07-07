package org.rogmann.jsmud;

/**
 * Helper-class for displaying types of frames.
 */
public enum FrameDisplay {
	/** An expanded frame. See {@link ClassReader#EXPAND_FRAMES}. */
	F_NEW(-1),
	/** A compressed frame with complete frame data. */
	F_FULL(0),
	/**
	 * A compressed frame where locals are the same as the locals in the previous frame, except that
	 * additional 1-3 locals are defined, and with an empty stack.
	 */
	F_APPEND(1),
	/**
	 * A compressed frame where locals are the same as the locals in the previous frame, except that
	 * the last 1-3 locals are absent and with an empty stack.
	 */
	F_CHOP(2),
	/**
	 * A compressed frame with exactly the same locals as the previous frame and with an empty stack.
	 */
	F_SAME(3),
	/**
	 * A compressed frame with exactly the same locals as the previous frame and with a single value
	 * on the stack.
	 */
	F_SAME1(4);
	
	/** type */
	private final int type;
	
	/**
	 * Internal constructor
	 * @param type type-num
	 */
	private FrameDisplay(int type) {
		this.type = type;
	}
	
	/**
	 * Gets the type-number.
	 * @return type
	 */
	public int getType() {
		return type;
	}
	
	/**
	 * Lookups a frame-type by the type-number.
	 * @param type type-number
	 * @return frame or <code>null</code>
	 */
	public static final FrameDisplay lookup(final int type) {
		for (FrameDisplay frame : values()) {
			if (frame.type == type) {
				return frame;
			}
		}
		return null;
	}
}
