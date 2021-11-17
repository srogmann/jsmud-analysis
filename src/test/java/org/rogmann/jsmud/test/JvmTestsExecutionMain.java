package org.rogmann.jsmud.test;

import java.io.PrintStream;
import java.lang.reflect.Method;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.log.LoggerFactorySystemOut;
import org.rogmann.jsmud.visitors.InstructionVisitorProvider;
import org.rogmann.jsmud.vm.ClassExecutionFilter;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JsmudConfiguration;
import org.rogmann.jsmud.vm.JvmInvocationHandler;
import org.rogmann.jsmud.vm.JvmInvocationHandlerReflection;
import org.rogmann.jsmud.vm.MethodFrame;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.SimpleClassExecutor;

/**
 * Executes a class.
 * 
 * <p>This class executes all methods in JvmTests marked with annotation @JsmudTest.</p>
 */
public class JvmTestsExecutionMain {

	static boolean repeatFailedTestsWithTracing = true;
	static boolean stopAtFailedTest = true;
	
	/**
	 * Entry point.
	 * @param args no arguments
	 */
	public static void main(String[] args) throws Throwable {
		final PrintStream psOut = System.out;
		psOut.println(String.format("JRE: %s, %s", System.getProperty("java.vendor"), System.getProperty("java.version")));
		final Class<?> classTest = JvmTests.class;
		for (Method method : classTest.getDeclaredMethods()) {
			final JsmudTest jsmudTest = method.getAnnotation(JsmudTest.class);
			if (jsmudTest == null) {
				continue;
			}
			
			LoggerFactory.setLoggerSpi(new LoggerFactorySystemOut(psOut, false, true));
			boolean dumpInstructions = false;
			//if (!"testsProxyViaReflection".equals(method.getName())) {
			//	continue;
			//}
			psOut.println("Test: " + method.getName());
			try {
				executeTestMethod(classTest, method, psOut, dumpInstructions);
			} catch (Throwable e) {
				psOut.println(String.format("Exception while simulating method %s", method));
				e.printStackTrace();
				psOut.flush();

				if (!repeatFailedTestsWithTracing && !stopAtFailedTest) {
					continue;
				}
				LoggerFactory.setLoggerSpi(new LoggerFactorySystemOut(psOut, true, true));
				dumpInstructions = true;
				try {
					executeTestMethod(classTest, method, psOut, dumpInstructions);
				} catch (Throwable e1) {
					psOut.println(String.format("Exception while tracing method %s", method));
				}
				if (stopAtFailedTest) {
					break;
				}
			}

		}
		
	}

	private static void executeTestMethod(final Class<?> classTest, Method method, final PrintStream psOut,
			final boolean dumpInstructions) throws Throwable {
		final Object objJvmTests = classTest.newInstance();
		final boolean dumpJreInstructions = true;
		final boolean dumpClassStatistic = false;
		final boolean dumpInstructionStatistic = false;
		final boolean dumpMethodCallTrace = false;
		final InstructionVisitorProvider visitorProvider = new InstructionVisitorProvider(psOut, dumpJreInstructions,
				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
		visitorProvider.setShowOutput(dumpInstructions);
		visitorProvider.setShowStatisticsAfterExecution(false);

		final String packageTests = classTest.getName().replaceFirst("[.][^.]*$", "");
		final ClassLoader classLoader = classTest.getClassLoader();
		ClassExecutionFilter filter = (c -> c.getName().startsWith(packageTests));
		final JsmudConfiguration config = new JsmudConfiguration();
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(filter, config);
		final ClassRegistry registry = new ClassRegistry(filter, config,
				classLoader, visitorProvider, invocationHandler);
		registry.registerThread(Thread.currentThread());
		try {
			final SimpleClassExecutor executor = new SimpleClassExecutor(registry, classTest, invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(objJvmTests);
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, method, "()V", stackArgs);
		}
		finally {
			registry.unregisterThread(Thread.currentThread());
		}
	}

	/**
	 * Method-frame in a visitor.
	 */
	static class VisitorFrame {
		Class<?> clazz;
		MethodFrame frame;
		boolean isJreClass;
		int currLine = -1;
	}

}
