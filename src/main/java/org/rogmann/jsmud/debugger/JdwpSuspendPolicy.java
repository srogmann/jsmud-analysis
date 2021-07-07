package org.rogmann.jsmud.debugger;

/**
 * jwdp-suspend-policy.
 */
public enum JdwpSuspendPolicy {

	/** Suspend no threads when this event is encountered. */
	NONE(0),
	/** Suspend the event thread when this event is encountered. */
	EVENT_THREAD(1),
	/** Suspend all threads when this event is encountered. */
	ALL(2);
	
	/** suspend-policy */
	private final byte policy;

	/**
	 * Internal constructor
	 * @param policy suspend-policy
	 */
	private JdwpSuspendPolicy(final int policy) {
		this.policy = (byte) policy;
	}
	
	/**
	 * Gets the suspend-policy.
	 * @return suspend-policy
	 */
	public byte getPolicy() {
		return policy;
	}
	
	/**
	 * Lookups a suspend-policy
	 * @param bSuspendPolicy suspend-poliy
	 * @return suspend-policy or <code>null</code>
	 */
	public static JdwpSuspendPolicy lookupBySuspendPolicy(final byte bSuspendPolicy) {
		for (JdwpSuspendPolicy loopPolicy : values()) {
			if (loopPolicy.policy == bSuspendPolicy) {
				return loopPolicy;
			}
		}
		return null;
	}
}
