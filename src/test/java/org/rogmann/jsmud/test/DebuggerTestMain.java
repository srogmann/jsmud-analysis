package org.rogmann.jsmud.test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitor;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.debugger.SourceFilesLocalDirectory;
import org.rogmann.jsmud.vm.ClassExecutionFilter;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JsmudClassLoader;
import org.rogmann.jsmud.vm.JsmudConfiguration;
import org.rogmann.jsmud.vm.JvmHelper;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.SimpleClassExecutor;

/**
 * test of jdwp-implementation.
 */
public class DebuggerTestMain {

	/**
	 * Entry-method.
	 * @param args debugger-host debugger-port
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			throw new IllegalArgumentException("Usage: debugger-host debugger-port [sources-folder]");
		}
		final String host = args[0];
		final int port = Integer.parseInt(args[1]);
		final File folderSources = (args.length > 2) ? new File(args[2]) : null;

		final ClassExecutionFilter filter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
		final ClassLoader clParent = DebuggerTestMain.class.getClassLoader();
		boolean patchClinit = false;
		boolean patchInit = false;
		boolean acceptHotCodeReplace = true;
		final JsmudConfiguration config = new JsmudConfiguration();
		final ClassLoader cl = new JsmudClassLoader(clParent, config,
				c -> false, patchClinit, patchInit, acceptHotCodeReplace);
		final SourceFileRequester sfr = createSourceFileRequester(folderSources);
		final DebuggerJvmVisitor visitor = JvmHelper.createDebuggerVisitor(filter, cl, sfr);
		final Supplier<Void> supplier = new Supplier<Void>() {
			@Override
			public Void get() {
				final JvmTests jvmTests = new JvmTests();
				jvmTests.tests();
				return null;
			}
		};
		JvmHelper.connectSupplierToDebugger(visitor, host, port, supplier, Void.class);
	}
	
	private static SourceFileRequester createSourceFileRequester(File folderSources) {
		SourceFileRequester sfr = null;
		if (folderSources != null) {
			final Predicate<Class<?>> classFilter = (clazz -> clazz.getName().startsWith("com.sun.net."));
			final String extension = "java";
			sfr = new SourceFilesLocalDirectory(classFilter, folderSources, extension, StandardCharsets.UTF_8,
					System.lineSeparator());
		}
		return sfr;
	}

	public static void debuggerTest1(final ClassRegistry vm) {
		final SimpleClassExecutor executor = vm.getClassExecutor(SampleClass.class);
		final OperandStack stackArgs = new OperandStack(2);
		stackArgs.push(Integer.valueOf(3));
		stackArgs.push(Integer.valueOf(5));
		final Method method = JvmHelper.lookup(SampleClass.class, "example");
		try {
			executor.executeMethod(Opcodes.INVOKESTATIC, method, "(II)V", stackArgs);
		} catch (Throwable e) {
			throw new RuntimeException("Exception while executing method " + method, e);
		}
	}

	public static void debuggerTest2(final ClassRegistry vm) {
		final SimpleClassExecutor executor = vm.getClassExecutor(JvmTests.class);
		final JvmTests jvmTests = new JvmTests();
		final OperandStack stackArgs = new OperandStack(1);
		stackArgs.push(jvmTests);
		final Method method = JvmHelper.lookup(JvmTests.class, "tests");
		try {
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, method, "()V", stackArgs);
		} catch (Throwable e) {
			throw new RuntimeException("Exception while executing method " + method, e);
		}
	}

	public static void debuggerTest3(final ClassRegistry vm) {
		final Class<?> classDepr;
		try {
			classDepr = Class.forName("com.sun.net.ssl.SSLContext");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't load deprecated class", e);
		}
		final SimpleClassExecutor executor = vm.getClassExecutor(classDepr);
		final OperandStack stackArgs = new OperandStack(3);
		stackArgs.push(classDepr);
		stackArgs.push("INVALID_TLS");
		final Method method = JvmHelper.lookup(classDepr, "getInstance");
		try {
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, method, "(Ljava/lang/String;)Lcom/sun/net/ssl/SSLContext;", stackArgs);
		} catch (Throwable e) {
			throw new RuntimeException("Exception while executing method " + method, e);
		}
	}

	public static void debuggerTest4(final ClassRegistry vm) {
		final SimpleClassExecutor executor = vm.getClassExecutor(DebuggerTestMethods.class);
		final OperandStack stackArgs = new OperandStack(1);
		stackArgs.push(DebuggerTestMethods.class);
		final Method method = JvmHelper.lookup(DebuggerTestMain.class, "main");
		try {
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, method, "([Ljava/lang/String;)V", stackArgs);
		} catch (Throwable e) {
			throw new RuntimeException("Exception while executing method " + method, e);
		}
	}

}
