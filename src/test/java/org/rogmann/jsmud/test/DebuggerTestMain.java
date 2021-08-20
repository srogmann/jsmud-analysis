package org.rogmann.jsmud.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import javax.net.SocketFactory;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitor;
import org.rogmann.jsmud.debugger.JdwpCommandProcessor;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.debugger.SourceFilesLocalDirectory;
import org.rogmann.jsmud.vm.ClassExecutionFilter;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JvmHelper;
import org.rogmann.jsmud.vm.JvmInvocationHandler;
import org.rogmann.jsmud.vm.JvmInvocationHandlerReflection;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.SimpleClassExecutor;

/**
 * test of jwdp-implementation.
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
		try (final Socket socket = SocketFactory.getDefault().createSocket(host, port)) {
			socket.setSoTimeout(200);
			final ClassLoader classLoader = DebuggerTestMain.class.getClassLoader();
			final ClassExecutionFilter executionFilter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
			final SourceFileRequester sourceFileRequester = createSourceFileRequester(folderSources);
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
			try (final InputStream is = socket.getInputStream()) {
				try (final OutputStream os = socket.getOutputStream()) {
					final JdwpCommandProcessor debugger = new JdwpCommandProcessor(is, os, 
							vm, visitor);
					visitor.visitThreadStarted(Thread.currentThread());
					vm.suspendThread(vm.getCurrentThreadId());
					debugger.processPackets();
					
					debuggerTest4(vm);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("IO-Exception while speaking to " + host + ':' + port, e);
		}
	}
	
	private static SourceFileRequester createSourceFileRequester(File folderSources) {
		SourceFileRequester sfr = null;
		if (folderSources != null) {
			final Predicate<Class<?>> classFilter = (clazz -> clazz.getName().startsWith("com.sun.net."));
			sfr = new SourceFilesLocalDirectory(classFilter, folderSources, StandardCharsets.UTF_8,
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
		final SimpleClassExecutor executor = vm.getClassExecutor(DebuggerTestMain.class);
		final OperandStack stackArgs = new OperandStack(1);
		stackArgs.push(DebuggerTestMain.class);
		final Method method = JvmHelper.lookup(DebuggerTestMain.class, "stepIntoSSLContext");
		try {
			executor.executeMethod(Opcodes.INVOKEVIRTUAL, method, "()V", stackArgs);
		} catch (Throwable e) {
			throw new RuntimeException("Exception while executing method " + method, e);
		}
	}

	public static void stepIntoSSLContext() {
		final Class<?> classDepr;
		try {
			classDepr = Class.forName("com.sun.net.ssl.SSLContext");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't load deprecated class", e);
		}
		try {
			final Method method = classDepr.getDeclaredMethod("getInstance", String.class);
			method.invoke(classDepr, "INVALID_TLS");
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Reflection-error while executing getInstance", e);
		}
	}
}
