package org.rogmann.jsmud.log;

/**
 * SPI for inserting a logger-implementation.
 */
public interface LoggerSpi {

	/**
	 * Returns a logger.
	 * @param clazz owner-class of the logger
	 * @return logger
	 */
	Logger getLogger(final Class<?> clazz);

}
