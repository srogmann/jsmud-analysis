package org.rogmann.jsmud.events;

import org.rogmann.jsmud.datatypes.VMThreadID;

/**
 * Details of a STEP-event-request.
 */
public class JdwpModifierStep extends JdwpEventModifier {
	/** step into method-calls */
	public static final int STEP_DEPTH_INTO = 0;
	/** step over method-calls */
	public static final int STEP_DEPTH_OVER = 1;
	/** step out of current method */
	public static final int STEP_DEPTH_OUT = 2;
	
	/** step by minimum possible amount */
	public static final int STEP_SIZE_MIN = 0;
	/** step to next source line */
	public static final int STEP_SIZE_LINE = 1;
	

	/** thread-id */
	private VMThreadID threadID;
	/** step-size */
	private int stepSize;
	/** step-depth */
	private int stepDepth;

	/**
	 * Constructor
	 * @param threadID thread-id 
	 * @param stepDepth step-size (0 = min, 1 = line)
	 * @param stepSize step-depth (0 = into, 1 = over, 2 = out)
	 */
	public JdwpModifierStep(final VMThreadID threadID, final int stepSize, final int stepDepth) {
		super(ModKind.STEP);
		this.threadID = threadID;
		this.stepSize = stepSize;
		this.stepDepth = stepDepth;
	}

	/**
	 * Gets the thread-id.
	 * @return thread-id
	 */
	public VMThreadID getThreadID() {
		return threadID;
	}

	/**
	 * Gets the step-size (0 = min, 1 = line).
	 * @return step-size
	 */
	public int getStepSize() {
		return stepSize;
	}

	/**
	 * Gets the step-depth (0 = into, 1 = over, 2 = out).
	 * @return step-depth
	 */
	public int getStepDepth() {
		return stepDepth;
	}

}
