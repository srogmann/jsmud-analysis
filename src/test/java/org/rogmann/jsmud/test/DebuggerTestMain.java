package org.rogmann.jsmud.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;

import javax.net.SocketFactory;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.ClassExecutionFilter;
import org.rogmann.jsmud.ClassRegistry;
import org.rogmann.jsmud.JvmHelper;
import org.rogmann.jsmud.JvmInvocationHandler;
import org.rogmann.jsmud.JvmInvocationHandlerReflection;
import org.rogmann.jsmud.OperandStack;
import org.rogmann.jsmud.SimpleClassExecutor;
import org.rogmann.jsmud.debugger.DebuggerJvmVisitor;
import org.rogmann.jsmud.debugger.JdwpCommandProcessor;

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
			throw new IllegalArgumentException("Usage: debugger-host debugger-port");
		}
		final String host = args[0];
		final int port = Integer.parseInt(args[1]);
		try {
			final Socket socket = SocketFactory.getDefault().createSocket(host, port);
			socket.setSoTimeout(200);
			final ClassLoader classLoader = DebuggerTestMain.class.getClassLoader();
			final ClassExecutionFilter executionFilter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
			final DebuggerJvmVisitor visitor = new DebuggerJvmVisitor();
			final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection();
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
			final JdwpCommandProcessor debugger = new JdwpCommandProcessor(socket.getInputStream(), socket.getOutputStream(), 
					vm, visitor);
			visitor.visitThreadStarted(Thread.currentThread());
			vm.suspendThread(vm.getCurrentThreadId());
			debugger.processPackets();
			
			debuggerTest2(vm);
		} catch (IOException e) {
			throw new RuntimeException("IO-Exception while speaking to " + host + ':' + port, e);
		}
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

}
