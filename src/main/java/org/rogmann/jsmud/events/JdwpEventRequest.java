package org.rogmann.jsmud.events;

import java.util.ArrayList;
import java.util.List;

import org.rogmann.jsmud.debugger.JdwpSuspendPolicy;
import org.rogmann.jsmud.debugger.VMEventType;

/**
 * Details of an event-request.
 */
public class JdwpEventRequest {

	/** request-id */
	private final int requestId;
	
	/** event-type */
	private final VMEventType eventType;
	/** suspend-policy */
	private final JdwpSuspendPolicy suspendPolicy;
	
	/** modifiers */
	private final List<JdwpEventModifier> modifiers;

	/**
	 * Constructor
	 * @param requestId request-id 
	 * @param eventType event-kind
	 * @param suspendPolicy suspend-policy
	 * @param modifers number of constraints
	 */
	public JdwpEventRequest(int requestId, VMEventType eventType, JdwpSuspendPolicy suspendPolicy, int modifers) {
		this.requestId = requestId;
		this.eventType = eventType;
		this.suspendPolicy = suspendPolicy;
		modifiers = new ArrayList<>(modifers);
	}
	
	/**
	 * Gets the request-id.
	 * @return request-id
	 */
	public int getRequestId() {
		return requestId;
	}

	/**
	 * Gets the event-type.
	 * @return event-kind
	 */
	public VMEventType getEventType() {
		return eventType;
	}
	
	/**
	 * Gets the suspend-policy.
	 * @return suspend-policy
	 */
	public JdwpSuspendPolicy getSuspendPolicy() {
		return suspendPolicy;
	}
	
	/**
	 * Adds a modifier.
	 * @param modifier event-modifier
	 */
	public void addModifier(final JdwpEventModifier modifier) {
		this.modifiers.add(modifier);
	}

	/**
	 * Gets the list of modifiers.
	 * @return modifiers
	 */
	public List<JdwpEventModifier> getModifiers() {
		return modifiers;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(50);
		sb.append(getClass().getSimpleName());
		sb.append('{');
		sb.append("id:").append(requestId);
		sb.append(", eventType:").append(eventType);
		sb.append('}');
		return sb.toString();
	}
}
