package org.rogmann.jsmud.vm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Interface to execute methods without jsmud-analysis.
 * A typical implementation is to use reflection.
 * This interface can be used to do the execution in a different simulation-engine.
 */
public interface NativeMethodExecutor {

	/**
	 * Executes a method.
	 * @param methodExec method to be executed
	 * @param objRef object-instance
	 * @param aJvmArgs parameters of the method (JVM arguments)
	 * @return return-value
	 * @throws IllegalAccessException in case of an access-violation
	 * @throws IllegalArgumentException in case of an illegal argument
	 * @throws InvocationTargetException in case of an error while execution
	 */
	Object executeMethodNative(Method methodExec, Object objRef, final Object[] aJvmArgs)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;
}
