package org.rogmann.jsmud.debugger;

import org.rogmann.jsmud.MethodFrame;
import org.rogmann.jsmud.events.JdwpEventRequest;
import org.rogmann.jsmud.events.JdwpModifierStep;

/**
 * Debugger-context of a method-frame.
 */
public class MethodFrameDebugContext {

	/** method-frame */
	final MethodFrame frame;
	
	/** current STEP-event-request */
	JdwpEventRequest eventRequestStep;

	/** current STEP-OUT-event-request for parent frame */
	JdwpEventRequest eventRequestStepUp;

	/** current STEP-modifier */
	JdwpModifierStep modStep;

	/** current STEP-OUT-modifier for parent frame */
	JdwpModifierStep modStepUp;

	/** line of step-start */
	int stepLine;

	/**
	 * Constructor
	 * @param frame method-frame
	 */
	public MethodFrameDebugContext(final MethodFrame frame) {
		this.frame = frame;
	}

	/** {@inheritDoc} */
	public String toString() {
		final StringBuilder sb = new StringBuilder(12);
		sb.append("MFDC#").append(Integer.toHexString(System.identityHashCode(this)));
		return sb.toString();
	}
}
