package org.rogmann.jsmud.debugger;

import java.io.IOException;

import org.rogmann.jsmud.datatypes.VMDataField;

/**
 * Interface of the debugger-command-processor.
 */
public interface DebuggerInterface {

	/**
	 * Processes a set of packets.
	 * @throws IOException in case of an IO-error
	 */
	void processPackets() throws IOException;

	/**
	 * Sends an event in an event-command-set.
	 * @param policy suspend-policy
	 * @param eventType event-type
	 * @param fields fields of the event
	 * @return id of command
	 * @throws IOException in case of an io-error 
	 */
	int sendVMEvent(final JdwpSuspendPolicy policy, VMEventType eventType, VMDataField... fields) throws IOException;

}
