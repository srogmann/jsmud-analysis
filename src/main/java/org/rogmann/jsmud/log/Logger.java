package org.rogmann.jsmud.log;

/**
 * logger.
 */
public interface Logger {
	
	/**
	 * Checks if the logger is enabled for debug-level.
	 * @return debug-flag
	 */
	boolean isDebugEnabled();

	/**
	 * Log a message at debug-level.
	 * @param msg message
	 */
	void debug(String msg);

	/**
	 * Checks if the logger is enabled for info-level.
	 * @return info-flag
	 */
	boolean isInfoEnabled();

	/**
	 * Log a message at info-level.
	 * @param msg message
	 */
	void info(String msg);

	/**
	 * Log a message at error-level.
	 * @param msg message
	 */
	void error(String msg);

	/**
	 * Log a message at error-level.
	 * @param msg message
	 * @param t throwable
	 */
	void error(String msg, Throwable t);
}
