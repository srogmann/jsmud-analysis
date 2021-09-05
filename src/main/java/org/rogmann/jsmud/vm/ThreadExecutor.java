package org.rogmann.jsmud.vm;

import java.lang.reflect.Method;

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

	/**
	 * Constructor
	 * @param registry class-registry (VM)
	 */
	public ThreadExecutor(final ClassRegistry registry) {
		this.registry = registry;
	}

	/**
	 * Runs a thread via JSMUD.
	 * @param thread thread to be executed
	 */
	public void run(final Thread thread) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("run: %s", thread));
		}
		registry.registerThread(thread);
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
			registry.unregisterThread(thread);
		}
	}
}
