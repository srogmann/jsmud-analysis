package org.rogmann.jsmud.log;

import java.util.logging.Level;

/**
 * java.util.logging-backend.
 */
public class LoggerFactoryJavaLogging implements LoggerSpi {

	/** {@inheritDoc} */
	@Override
	public Logger getLogger(Class<?> clazz) {
		final java.util.logging.Logger loggerImpl = java.util.logging.Logger.getLogger(clazz.getName());
		return new LoggerJUL(loggerImpl);
	}

	/** JUL-bridge */
	static class LoggerJUL implements Logger {
		/** logger-implementation */
		private final java.util.logging.Logger loggerImpl;

		/**
		 * Constructor
		 * @param loggerImpl logger-implementation
		 */
		LoggerJUL(java.util.logging.Logger loggerImpl) {
			this.loggerImpl = loggerImpl;
		}

		/** {@inheritDoc} */
		@Override
		public boolean isDebugEnabled() {
			return loggerImpl.isLoggable(Level.FINE);
		}

		/** {@inheritDoc} */
		@Override
		public void debug(String msg) {
			loggerImpl.log(Level.FINE, msg);
		}

		/** {@inheritDoc} */
		@Override
		public boolean isInfoEnabled() {
			return loggerImpl.isLoggable(Level.FINE);
		}

		/** {@inheritDoc} */
		@Override
		public void info(String msg) {
			loggerImpl.info(msg);
		}

		/** {@inheritDoc} */
		@Override
		public void error(String msg) {
			loggerImpl.severe(msg);
		}

		/** {@inheritDoc} */
		@Override
		public void error(String msg, Throwable t) {
			loggerImpl.log(Level.SEVERE, msg, t);
		}
		
	}
}
