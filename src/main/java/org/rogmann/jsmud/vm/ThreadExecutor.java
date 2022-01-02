package org.rogmann.jsmud.vm;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import org.objectweb.asm.Opcodes;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Class to execute a thread via patched run()-method.
 */
public class ThreadExecutor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ThreadExecutor.class);

	/** registry */
	private final ClassRegistry registry;

	/** visitor of parent-thread */
	private final JvmExecutionVisitor visitorParent;

	/**
	 * Constructor
	 * @param registry class-registry (VM)
	 * @param visitorParent visitor of parent-thread
	 */
	public ThreadExecutor(final ClassRegistry registry, final JvmExecutionVisitor visitorParent) {
		this.registry = registry;
		this.visitorParent = visitorParent;
	}

	/**
	 * Runs a callable in the current thread (e.g. FutureTask) via jsmud-analysis.
	 * @param thread thread to be executed
	 * @return result
	 */
	public Object call(final Callable<?> callable) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("run: callable %s", callable));
		}
		final Thread thread = Thread.currentThread();
		final boolean isThreadIsNew = registry.registerThread(thread, visitorParent);
		if (isThreadIsNew) {
			visitorParent.visitThreadStarted(thread);
		}
		final Object returnObj;
		try {
			final Class<?> threadClass = callable.getClass();
			final SimpleClassExecutor executor = registry.getClassExecutor(threadClass);
			if (executor == null) {
				throw new JvmException(String.format("No executor for callable-class (%s) of thread (%s)",
						threadClass, thread));
			}
			final Method methodCall;
			try {
				methodCall = threadClass.getDeclaredMethod("call");
			}
			catch (NoSuchMethodException e) {
				throw new JvmException(String.format("call-method of (%s) is missing", threadClass), e);
			}
			catch (SecurityException e) {
				throw new JvmException(String.format("Executing call-method of (%s) is not allowed", threadClass), e);
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("callable: execute method %s", methodCall));
			}
			final OperandStack stack = new OperandStack(1);
			stack.push(callable);
			try {
				returnObj = executor.executeMethod(Opcodes.INVOKEVIRTUAL, methodCall, "()Ljava/lang/Object;", stack);
			} catch (Throwable e) {
				throw new JvmException(String.format("Throwable occured while executing thread (%s)", thread), e);
			}
		}
		finally {
			if (isThreadIsNew) {
				registry.unregisterThread(thread);
			}
		}
		return returnObj;
	}

	/**
	 * Runs a thread via jsmud-analysis.
	 * @param thread thread to be executed
	 */
	public void run(final Thread thread) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("run: %s", thread));
		}
		final boolean isThreadIsNew = registry.registerThread(thread, visitorParent);
		if (isThreadIsNew) {
			visitorParent.visitThreadStarted(thread);
		}
		try {
			final Class<? extends Thread> threadClass = thread.getClass();
			final SimpleClassExecutor executor = registry.getClassExecutor(threadClass);
			if (executor == null) {
				throw new JvmException(String.format("No executor for thread-class (%s) of thread (%s)",
						threadClass, thread));
			}
			Class<?> classLoop = threadClass.getSuperclass();
			Method methodRun = null;
			while (!Object.class.equals(classLoop)) {
				try {
					methodRun = classLoop.getDeclaredMethod("run");
				} catch (NoSuchMethodException e) {
					classLoop = classLoop.getSuperclass();
					continue;
				} catch (SecurityException e) {
					throw new JvmException(String.format("Examination of (%s) is not allowed", classLoop), e);
				}
				break;
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("run: execute method %s", methodRun));
			}
			if (methodRun == null) {
				throw new JvmException(String.format("Can't find run-method in class (%s), parent of (%s)",
						threadClass.getSuperclass(), threadClass));
			}
			final Class<?> methodRunClass = methodRun.getDeclaringClass();
			final SimpleClassExecutor executorParent = registry.getClassExecutor(methodRunClass, true);
			if (executorParent == null) {
				throw new JvmException(String.format("No executor for describing class (%s) of (%s), parent of (%s)",
						methodRunClass, methodRun, threadClass));
			}
			final OperandStack stack = new OperandStack(1);
			stack.push(thread);
			try {
				executorParent.executeMethod(Opcodes.INVOKEVIRTUAL, methodRun, "()V", stack);
			} catch (Throwable e) {
				throw new JvmException(String.format("Throwable occured while executing thread (%s)", thread), e);
			}
		}
		finally {
			if (isThreadIsNew) {
				registry.unregisterThread(thread);
			}
		}
	}
}
