package org.rogmann.jsmud.log;

import java.io.PrintStream;

/**
 * Simple System.out-logger.
 */
public class LoggerFactorySystemOut implements LoggerSpi, Logger {
	/** output-stream */
	private final PrintStream psOut;
	
	/** debug-flag */
	private final boolean hasDebug;
	
	/** info-flag */
	private final boolean hasInfo;

	/** default-logger: everything into System.out */
	public LoggerFactorySystemOut() {
		psOut = System.out;
		hasDebug = true;
		hasInfo = true;
	}

	/**
	 * Constructor
	 * @param psOut output-stream
	 * @param hasDebug debug-enabled-flag
	 * @param hasInfo info-enabled-flag
	 */
	public LoggerFactorySystemOut(final PrintStream psOut, final boolean hasDebug, final boolean hasInfo) {
		this.psOut = psOut;
		this.hasDebug = hasDebug;
		this.hasInfo = hasInfo;
	}

	/** {@inheritDoc} */
	@Override
	public Logger getLogger(Class<?> clazz) {
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDebugEnabled() {
		return hasDebug;
	}

	/** {@inheritDoc} */
	@Override
	public void debug(String msg) {
		if (hasDebug) {
			psOut.println(msg);
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean isInfoEnabled() {
		return hasInfo;
	}

	/** {@inheritDoc} */
	@Override
	public void info(String msg) {
		if (hasInfo) {
			psOut.println(msg);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void error(String msg) {
		psOut.println(msg);
	}

	/** {@inheritDoc} */
	@Override
	public void error(String msg, Throwable t) {
		psOut.println(msg);
		t.printStackTrace(psOut);
	}
	
}
