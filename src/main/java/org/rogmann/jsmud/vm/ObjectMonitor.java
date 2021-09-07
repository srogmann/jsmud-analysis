package org.rogmann.jsmud.vm;

public interface ObjectMonitor {

	/**
	 * Enters a monitor.
	 * @param objMonitor monitor-object
	 * @return current monitor-counter
	 */
	int enterMonitor(final Object objMonitor);

	/**
	 * Exits a monitor.
	 * @param objMonitor monitor-object
	 * @return current monitor-counter
	 */
	int exitMonitor(final Object objMonitor);

}
