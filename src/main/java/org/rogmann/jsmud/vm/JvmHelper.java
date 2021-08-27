package org.rogmann.jsmud.vm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.Socket;

import javax.net.SocketFactory;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitor;
import org.rogmann.jsmud.debugger.JdwpCommandProcessor;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.visitors.InstructionVisitor;

/**
 * Helper-methods for initializing a JVM.
 */
public class JvmHelper {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmHelper.class);

	/**
	 * Connects to a remote-debugger for executing the given runnable.
	 * @param host remote-host
	 * @param port remote-port
	 * @param runnable runnable to be executed
	 */
	public static void connectRunnableToDebugger(final String host, final int port,
			final Runnable runnable) {
		final ClassExecutionFilter executionFilter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
		connectRunnableToDebugger(host, port, runnable, executionFilter, null);
	}

	/**
	 * Connects to a remote-debugger for executing the given runnable.
	 * @param host remote-host
	 * @param port remote-port
	 * @param runnable runnable to be executed
	 * @param executionFilter execution-filter
	 * @param sourceFileRequester optional source-file-requester (on-the-fly pseudo-source-generation)
	 */
	public static void connectRunnableToDebugger(final String host, final int port,
			final Runnable runnable, final ClassExecutionFilter executionFilter,
			final SourceFileRequester sourceFileRequester) {
		LOG.info(String.format("connectRunnableToDebugger(host=%s, port=%d, version=%s)", host, Integer.valueOf(port), ClassRegistry.VERSION));
		try (final Socket socket = SocketFactory.getDefault().createSocket(host, port)) {
			socket.setSoTimeout(200);
			final ClassLoader classLoader = runnable.getClass().getClassLoader();
			final DebuggerJvmVisitor visitor = new DebuggerJvmVisitor(sourceFileRequester);
			final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(executionFilter);
			final boolean simulateReflection = true;
			final ClassRegistry vm = new ClassRegistry(executionFilter, classLoader,
					simulateReflection, visitor, invocationHandler);
			vm.registerThread(Thread.currentThread());
			final Class<?>[] classesPreload = {
					Thread.class,
					Throwable.class,
					Error.class
			};
			for (final Class<?> classPreload : classesPreload) {
				try {
					vm.loadClass(classPreload.getName(), classPreload);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException("Couldn't preload class " + classPreload, e);
				}
			}
			visitor.setJvmSimulator(vm);
			try (final InputStream socketIs = socket.getInputStream()) {
				try (final OutputStream socketOs = socket.getOutputStream()) {
					final JdwpCommandProcessor debugger = new JdwpCommandProcessor(socketIs, socketOs, 
							vm, visitor);
					visitor.visitThreadStarted(Thread.currentThread());
					vm.suspendThread(vm.getCurrentThreadId());
					debugger.processPackets();
		
					vm.registerThread(Thread.currentThread());
					try {
						final SimpleClassExecutor executor = new SimpleClassExecutor(vm, runnable.getClass(), visitor, invocationHandler);
						// We have to announce the class to the debugger.
						visitor.visitLoadClass(runnable.getClass());
						final OperandStack stackArgs = new OperandStack(1);
						stackArgs.push(runnable);
						try {
							executor.executeMethod(Opcodes.INVOKEVIRTUAL, lookup(runnable.getClass(), "run"), "()V", stackArgs);
						} catch (Throwable e) {
							throw new RuntimeException("Exception while simulating runnable", e);
						}
					}
					finally {
						vm.unregisterThread(Thread.currentThread());
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("IO-Exception while speaking to " + host + ':' + port, e);
		}
	}

	public static void executeRunnable(final Runnable runnable, final PrintStream psOut) {
		executeRunnable(runnable, createNonJavaButJavaUtilExecutionFilter(), psOut);
	}

	public static void executeRunnable(final Runnable runnable, final ClassExecutionFilter filter,
			final PrintStream psOut) {
		LOG.info(String.format("executeRunnable(version=%s)", ClassRegistry.VERSION));
		final boolean dumpJreInstructions = true;
		final boolean dumpClassStatistic = true;
		final boolean dumpInstructionStatistic = true;
		final boolean dumpMethodCallTrace = true;
		final InstructionVisitor visitor = new InstructionVisitor(psOut, dumpJreInstructions,
				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
		visitor.setShowOutput(true);

		final Class<?> classRunnable = runnable.getClass();
		final ClassLoader classLoader = classRunnable.getClassLoader();
		final boolean simulateReflection = true;
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(filter);
		final ClassRegistry registry = new ClassRegistry(filter, classLoader,
				simulateReflection, visitor, invocationHandler);
		registry.registerThread(Thread.currentThread());
		try {
			final SimpleClassExecutor executor = new SimpleClassExecutor(registry, runnable.getClass(), visitor, invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(runnable);
			try {
				executor.executeMethod(Opcodes.INVOKEVIRTUAL, lookup(runnable.getClass(), "run"), "()V", stackArgs);
			} catch (Throwable e) {
				throw new RuntimeException("Exception while simulating runnable", e);
			}
			visitor.showStatistics();
		}
		finally {
			registry.unregisterThread(Thread.currentThread());
		}

	}

	/**
	 * Creates a filter to exclude java.*- and sun.*-classes from simulation.
	 * @return non java.*-filter
	 */
	public static ClassExecutionFilter createNonJavaExecutionFilter() {
		ClassExecutionFilter filter = new ClassExecutionFilter() {
			/** {@inheritDoc} */
			@Override
			public boolean isClassToBeSimulated(Class<?> clazz) {
				final String className = clazz.getName();
				return !(className.startsWith("java.")
						|| className.startsWith("sun.")
						|| className.startsWith("com.sun.")
						|| className.contains("$$Lambda"));
			}
		};
		return filter;
	}

	/**
	 * Creates a filter to exclude java.*- and sun.*-classes from simulation.
	 * @return non java.*-filter
	 */
	public static ClassExecutionFilter createNonJavaButJavaUtilExecutionFilter() {
		ClassExecutionFilter filter = new ClassExecutionFilter() {
			/** {@inheritDoc} */
			@Override
			public boolean isClassToBeSimulated(Class<?> clazz) {
				final String className = clazz.getName();
				final boolean isSimulation;
				if (className.contains("$$Lambda")) {
					// This seems to be a call-site created by the JRE.
					isSimulation = false;
				}
				else if (className.startsWith("java.util.")
						|| className.startsWith("com.sun.net.")) {
					// We simulate java.util.* to support simulation of the stream-API.
					isSimulation = true;
				}
				else if (className.startsWith("java.")
						|| className.startsWith("com.sun.")
						|| className.startsWith("sun.")) {
					// No java.*- or sun.*-classes.
					isSimulation = false;
				}
				else {
					isSimulation = true;
				}
				return isSimulation;
			}
		};
		return filter;
	}

	/**
	 * Gets the first non-synthetic method of a class with a given name.
	 * @param clazz class
	 * @param methodName name of a method in the class
	 * @return method
	 */
	public static Method lookup(final Class<?> clazz, final String methodName) {
		for (Method method : clazz.getDeclaredMethods()) {
			if (methodName.equals(method.getName()) && !method.isSynthetic()) {
				return method;
			}
		}
		throw new RuntimeException(String.format("Unknown method (%s) in (%s)", methodName, clazz));
	}

}
