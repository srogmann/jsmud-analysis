package org.rogmann.jsmud.log;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import org.rogmann.jsmud.vm.JvmException;

/** logger-factory */
public class LoggerFactory {
	/** property-name */
	public static final String PROPERTY_NAME = LoggerSpi.class.getName();

	/** logger-SPI */
	private static final AtomicReference<LoggerSpi> LOGGER_SPI = new AtomicReference<>();

	/**
	 * Returns a logger.
	 * @param clazz owner-class of the logger
	 * @return logger
	 */
	public static final Logger getLogger(final Class<?> clazz) {
		LoggerSpi loggerSpi = LOGGER_SPI.get();
		if (loggerSpi == null) {
			// Some service-declaration in META-INF/services?
			final ClassLoader classLoader = LoggerSpi.class.getClassLoader();
			final ServiceLoader<LoggerSpi> serviceLoader = ServiceLoader.load(LoggerSpi.class, classLoader);
			final Iterator<LoggerSpi> it = serviceLoader.iterator();
			if (it.hasNext()) {
				loggerSpi = it.next();
				LOGGER_SPI.set(loggerSpi);
			}
		}
		if (loggerSpi == null) {
			// logger-spi via system-property.
			final String classNameLoggerSpi = System.getProperty(PROPERTY_NAME);
			if (classNameLoggerSpi != null) {
				final ClassLoader classLoader = LoggerSpi.class.getClassLoader();
				final Class<?> classLoggerSpi;
				try {
					classLoggerSpi = classLoader.loadClass(classNameLoggerSpi);
				} catch (ClassNotFoundException e) {
					throw new JvmException(String.format("Can't find logger-implementation (%s) in class-loader (%s)",
							classNameLoggerSpi, classLoader));
				}
				final Object oLoggerSpi;
				try {
					oLoggerSpi = classLoggerSpi.getDeclaredConstructor().newInstance();
				}
				catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
					throw new JvmException(String.format("Can't instanciate logger-implementation (%s)",
							classNameLoggerSpi), e);
				}
				if (oLoggerSpi instanceof LoggerSpi) {
					loggerSpi = (LoggerSpi) oLoggerSpi;
					LOGGER_SPI.set(loggerSpi);
				}
				else {
					throw new JvmException(String.format("logger-implementation (%s) has type (%s) instead of (%s)",
							classNameLoggerSpi, oLoggerSpi.getClass(), LoggerSpi.class));
				}
			}
		}
		if (loggerSpi == null) {
			loggerSpi = new LoggerFactorySystemOut();
		}
		return loggerSpi.getLogger(clazz);
	}

	/**
	 * Sets a logger-implementation.
	 * @param loggerSpi logger-spi
	 */
	public static final void setLoggerSpi(final LoggerSpi loggerSpi) {
		LOGGER_SPI.set(loggerSpi);
	}
}
