package org.rogmann.jsmud.vm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.List;
import java.util.function.Supplier;

import javax.net.SocketFactory;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitor;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitorProvider;
import org.rogmann.jsmud.debugger.JdwpCommandProcessor;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.visitors.InstructionVisitorProvider;

/**
 * Helper-methods for initializing a JVM-simulation.
 */
public class JvmHelper {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmHelper.class);

	/** maximum lock time (in seconds) a threads waits for sending JDWP-packets (default is 600 seconds) */
	private static final int MAX_LOCK_TIME = Integer.getInteger(JvmHelper.class.getName() + ".maxLockTime", 600).intValue();

	/**
	 * Creates a vm-simulation with debugger-visitor which can be used to start a debugger in the current thread. 
	 * @param executionFilter class which should be interpreted
	 * @param classLoader default class-loader
	 * @param sourceFileRequester optional source-file-requester
	 * @return debugger-visitor
	 */
	public static DebuggerJvmVisitor createDebuggerVisitor(final ClassExecutionFilter executionFilter,
			final ClassLoader classLoader,
			final SourceFileRequester sourceFileRequester) {
		final DebuggerJvmVisitorProvider visitorProvider = new DebuggerJvmVisitorProvider(sourceFileRequester);
		final JsmudConfiguration config = new JsmudConfiguration();
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(executionFilter, config);
		final ClassRegistry vm = new ClassRegistry(executionFilter, config, classLoader,
				visitorProvider, invocationHandler);
		final Class<?>[] classesPreload = {
				Thread.class,
				Throwable.class,
				Error.class,
				// java.lang.Object[] is needed in org.eclipse.jdt.internal.debug.core.logicalstructures.JDIAllInstancesValue.JDIAllInstancesValue(JDIDebugTarget, JDIReferenceType)
				new Object[0].getClass()
		};
		for (final Class<?> classPreload : classesPreload) {
			try {
				vm.loadClass(classPreload.getName(), classPreload);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Couldn't preload class " + classPreload, e);
			}
		}
		// We register the current thread.
		vm.registerThread(Thread.currentThread());
		// And register the vm in the created visitor of this thread.
		final DebuggerJvmVisitor visitor = (DebuggerJvmVisitor) vm.getCurrentVisitor();
		visitor.setJvmSimulator(vm);
		return visitor;
	}

	/**
	 * Connects to a remote-debugger for executing the given supplier.
	 * @param visitor debugger-visitor initialized with vm-simulation
	 * @param host remote-host
	 * @param port remote-port
	 * @param supplier supplier to be executed
	 * @param classReturnObj class of return-type
	 * @param <T> return-type of supplier
	 * @return object returned by the supplier
	 */
	public static <T> T connectSupplierToDebugger(final DebuggerJvmVisitor visitor, String host, final int port,
			final Supplier<T> supplier, final Class<T> classReturnObj) {
		LOG.info(String.format("connectSupplierToDebugger(host=%s, port=%d, version=%s, jre.version=%s)",
				host, Integer.valueOf(port), ClassRegistry.VERSION, System.getProperty("java.version")));
		final T t;
		try (final Socket socket = SocketFactory.getDefault().createSocket(host, port)) {
			socket.setSoTimeout(200);
			try (final InputStream socketIs = socket.getInputStream()) {
				try (final OutputStream socketOs = socket.getOutputStream()) {
					final JdwpCommandProcessor debugger = new JdwpCommandProcessor(socketIs, socketOs, 
							visitor.getJvmSimulator(), visitor, MAX_LOCK_TIME);
					visitor.setDebugger(debugger);
					t = visitor.executeSupplier(supplier, classReturnObj);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("IO-Exception while speaking to " + host + ':' + port, e);
		}
		return t;
	}

	/**
	 * Connects to a remote-debugger for executing the given runnable.
	 * @param host remote-host
	 * @param port remote-port
	 * @param runnable runnable to be executed
	 */
	public static void connectRunnableToDebugger(final String host, final int port,
			final Runnable runnable) {
		final Supplier<Void> supplier = new Supplier<Void>() {
			@Override
			public Void get() {
				runnable.run();
				return null;
			}
		};
		final ClassExecutionFilter filter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
		final ClassLoader classLoader = runnable.getClass().getClassLoader();
		final SourceFileRequester sfr = null;
		final DebuggerJvmVisitor debuggerVisitor = createDebuggerVisitor(filter, classLoader, sfr);
		connectSupplierToDebugger(debuggerVisitor, host, port, supplier, Void.class);
	}

	public static <T> T executeSupplier(final Supplier<T> supplier, final ClassExecutionFilter filter,
			final PrintStream psOut) {
		LOG.info(String.format("executeRunnable(version=%s, jre.version=%s)",
				ClassRegistry.VERSION, System.getProperty("java.version")));
		final Object objReturn;
		final boolean dumpJreInstructions = true;
		final boolean dumpClassStatistic = true;
		final boolean dumpInstructionStatistic = true;
		final boolean dumpMethodCallTrace = true;
		final InstructionVisitorProvider visitorProvider = new InstructionVisitorProvider(psOut, dumpJreInstructions,
				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
		visitorProvider.setShowOutput(true);

		final Class<?> classSupplier = supplier.getClass();
		final ClassLoader classLoader = classSupplier.getClassLoader();
		final JsmudConfiguration config = new JsmudConfiguration();
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(filter, config);
		final ClassRegistry registry = new ClassRegistry(filter, config,
				classLoader, visitorProvider, invocationHandler);
		registry.registerThread(Thread.currentThread());
		try {
			final SimpleClassExecutor executor = new SimpleClassExecutor(registry, supplier.getClass(), invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(supplier);
			try {
				objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, lookup(supplier.getClass(), "get"), "()Ljava/lang/Object;", stackArgs);
			} catch (Throwable e) {
				throw new JvmUncaughtException("Exception while simulating supplier", e);
			}
		}
		finally {
			registry.unregisterThread(Thread.currentThread());
		}
		@SuppressWarnings("unchecked")
		final T t = (T) objReturn;
		return t;
	}

	public static void executeRunnable(final Runnable runnable, final PrintStream psOut) {
		executeRunnable(runnable, createNonJavaButJavaUtilExecutionFilter(), psOut);
	}

	public static void executeRunnable(final Runnable runnable, final ClassExecutionFilter filter,
			final PrintStream psOut) {
		LOG.info(String.format("executeRunnable(version=%s, jre.version=%s)",
				ClassRegistry.VERSION, System.getProperty("java.version")));
		final boolean dumpJreInstructions = true;
		final boolean dumpClassStatistic = true;
		final boolean dumpInstructionStatistic = true;
		final boolean dumpMethodCallTrace = true;
		final InstructionVisitorProvider visitorProvider = new InstructionVisitorProvider(psOut, dumpJreInstructions,
				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
		visitorProvider.setShowOutput(true);
		visitorProvider.setShowStatisticsAfterExecution(true);

		final Class<?> classRunnable = runnable.getClass();
		final ClassLoader classLoader = classRunnable.getClassLoader();
		final JsmudConfiguration config = new JsmudConfiguration();
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(filter, config);
		final ClassRegistry registry = new ClassRegistry(filter, config,
				classLoader, visitorProvider, invocationHandler);
		registry.registerThread(Thread.currentThread());
		try {
			final SimpleClassExecutor executor = new SimpleClassExecutor(registry, runnable.getClass(), invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(runnable);
			try {
				executor.executeMethod(Opcodes.INVOKEVIRTUAL, lookup(runnable.getClass(), "run"), "()V", stackArgs);
			} catch (Throwable e) {
				throw new RuntimeException("Exception while simulating runnable", e);
			}
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
						|| className.startsWith("jdk.internal.")
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
						|| className.startsWith("jdk.internal.")
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
	 * Dumps the simulated stack-trace in a JvmUncaughtException.
	 * @param e JvmUncaughtException
	 * @param psOut optional print-stream
	 */
	public static void dumpStacktrace(final JvmUncaughtException e, final PrintStream psOut) {
		final String errorMsg = "JvmUncaughException: " + e.getMessage();
		if (psOut != null) {
			psOut.println(errorMsg);
		}
		else {
			LOG.info(errorMsg);
		}
		final List<StackTraceElement> simStacktrace = e.getSimStacktrace();
		for (int i = 0; i < simStacktrace.size(); i++) {
			final StackTraceElement ste = simStacktrace.get(i);
			final String traceLine = String.format(" at %s.%s(%d)", ste.getClassName(),
					ste.getMethodName(), Integer.valueOf(ste.getLineNumber()));
			if (psOut != null) {
				psOut.println(traceLine);
			}
			else {
				LOG.info(traceLine);
			}
		}
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
