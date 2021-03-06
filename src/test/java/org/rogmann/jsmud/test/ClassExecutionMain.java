package org.rogmann.jsmud.test;

import java.io.PrintStream;
import java.lang.reflect.Constructor;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.visitors.InstructionVisitorProvider;
import org.rogmann.jsmud.vm.ClassExecutionFilter;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JsmudClassLoader;
import org.rogmann.jsmud.vm.JsmudConfiguration;
import org.rogmann.jsmud.vm.JvmHelper;
import org.rogmann.jsmud.vm.JvmInvocationHandler;
import org.rogmann.jsmud.vm.JvmInvocationHandlerReflection;
import org.rogmann.jsmud.vm.JvmUncaughtException;
import org.rogmann.jsmud.vm.MethodFrame;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.SimpleClassExecutor;

/**
 * Executes a class.
 */
public class ClassExecutionMain {
	static int testNr = 11;

	/**
	 * Entry point.
	 * @param args [test-nr]
	 */
	public static void main(String[] args) throws Throwable {
		if (args.length > 0) {
			testNr = Integer.parseInt(args[0]);
		}
		final PrintStream psOut = System.out;
		psOut.println(String.format("JRE: %s, %s", System.getProperty("java.vendor"), System.getProperty("java.version")));
		final boolean dumpJreInstructions = true;
		final boolean dumpClassStatistic = true;
		final boolean dumpInstructionStatistic = true;
		final boolean dumpMethodCallTrace = true;
		final InstructionVisitorProvider visitorProvider = new InstructionVisitorProvider(psOut, dumpJreInstructions,
				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
		visitorProvider.setShowOutput(true);
		visitorProvider.setShowStatisticsAfterExecution(true);

		final ClassExecutionFilter executionFilterDefault = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
		final ClassExecutionFilter executionFilter = (clazz ->
			NativeExecutor.class.equals(clazz) ? false : executionFilterDefault.isClassToBeSimulated(clazz));
		final ClassLoader classLoaderParent = ClassExecutionMain.class.getClassLoader();
		final boolean patchClinit = false;
		final boolean patchInit = false;
		final boolean redefineClass = false;
		final JsmudConfiguration config = new JsmudConfiguration();
		final JsmudClassLoader classLoader = new JsmudClassLoader(classLoaderParent, config,
				name -> !name.startsWith("java.") && !name.startsWith("com.sun.") && !name.startsWith("sun."),
				patchClinit, patchInit, redefineClass);
		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(executionFilter, config);
		final ClassRegistry registry = new ClassRegistry(executionFilter, config,
				classLoader, visitorProvider, invocationHandler);
		registry.registerThread(Thread.currentThread());
		final SimpleClassExecutor executor = new SimpleClassExecutor(registry, SampleClass.class, invocationHandler);
		if (testNr == 0) {
			final SampleClass sc = new SampleClass(5);
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(sc);
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, JvmHelper.lookup(SampleClass.class, "exampleMax"), "()V", stackArgs);
			System.out.println("myField: " + sc.getMyField1());
		}
		else if (testNr == 1) {
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(Integer.valueOf(3));
			stackArgs.push(Integer.valueOf(5));
			executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example"), "(II)V", stackArgs);
		}
		else if (testNr == 2) {
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(Integer.valueOf(1));
			stackArgs.push(Integer.valueOf(4));
			executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example2"), "(II)V", stackArgs);
		}
		else if (testNr == 3) {
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(Long.valueOf(125000));
			stackArgs.push(Long.valueOf(5000000000L));
			executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example3"), "(JJ)V", stackArgs);
		}
		else if (testNr == 4) {
			final SampleClass sampleClass = new SampleClass(5);
			final OperandStack stackArgs = new OperandStack(3);
			stackArgs.push(sampleClass);
			stackArgs.push(Long.valueOf(125000));
			stackArgs.push(Long.valueOf(5000000000L));
			final Long c = (Long) executor.executeMethod(Opcodes.INVOKEDYNAMIC, JvmHelper.lookup(SampleClass.class, "example4"), "(JJ)J", stackArgs);
			psOut.println("Ergebnis: " + c);
		}
		else if (testNr == 5) {
			final OperandStack stackArgs = new OperandStack(2);
			stackArgs.push(Integer.valueOf(10));
			final Long c = (Long) executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example5"), "(I)J", stackArgs);
			psOut.println("Ergebnis: " + c);
		}
		else if (testNr == 6) {
			final OperandStack stackArgs = new OperandStack(2);
			final Long c = (Long) executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example6"), "()J", stackArgs);
			psOut.println("Ergebnis: " + c);
		}
		else if (testNr == 7) {
			final OperandStack stackArgs = new OperandStack(0);
			executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example7"), "()V", stackArgs);
		}
		else if (testNr == 8) {
			final OperandStack stackArgs = new OperandStack(0);
			executor.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(SampleClass.class, "example8"), "()V", stackArgs);
		}
		else if (testNr == 10) {
			final SimpleClassExecutor executorNC = new SimpleClassExecutor(registry, ConstructorNesting.class, invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(Integer.valueOf(71));
			final String output = (String) executorNC.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(ConstructorNesting.class, "test"), "(I)Ljava/lang/String;", stackArgs);
			System.out.println("Output: " + output);
		}
		else if (testNr == 11) {
			// We use a string-representation of the class to prevent it from being loaded too early.
			// We want it to be loaded by the JsmudClassLoader.
			final Class<?> classJvmTests = registry.loadClass("org.rogmann.jsmud.test.JvmTests", ClassExecutionMain.class);
			final Object jvmTests = classJvmTests.getDeclaredConstructor().newInstance();
			final SimpleClassExecutor executorNC = new SimpleClassExecutor(registry, jvmTests.getClass(), invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(jvmTests);
			final Constructor<? extends Object> defaultConstr = jvmTests.getClass().getDeclaredConstructor();
			executorNC.executeMethod(Opcodes.INVOKESPECIAL, defaultConstr, "()V", stackArgs);
			stackArgs.push(jvmTests);
			try {
				executorNC.executeMethod(Opcodes.INVOKEDYNAMIC, JvmHelper.lookup(jvmTests.getClass(), "tests"), "()V", stackArgs);
			} catch (JvmUncaughtException e) {
				JvmHelper.dumpStacktrace(e, System.err);
			}
		}
		else if (testNr == 12) {
			final SimpleClassExecutor executorNC = new SimpleClassExecutor(registry, SwingTest.class, invocationHandler);
			final OperandStack stackArgs = new OperandStack(1);
			stackArgs.push(new String[0]);
			executorNC.executeMethod(Opcodes.INVOKESTATIC, JvmHelper.lookup(JvmTests.class, "main"), "([Ljava/lang/String;)V", stackArgs);
		}
		
		registry.unregisterThread(Thread.currentThread());
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
