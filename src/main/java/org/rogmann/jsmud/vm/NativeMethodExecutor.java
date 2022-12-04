package org.rogmann.jsmud.vm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Interface to execute methods without jsmud-analysis.
 * A typical implementation is to use reflection.
 * This interface can be used to do the execution in a different simulation-engine.
 */
public interface NativeMethodExecutor {

	/**
	 * Executes a constructor.
	 * @param constructor constructor to be executed
	 * @param aJvmArgs parameters of the constructor (JVM arguments)
	 * @return new instance
	 * @throws IllegalArgumentException in case of an illegal argument
	 * @throws IllegalAccessException in case of an access-violation
	 * @throws InstantiationException in case of an instantiation-error
	 * @throws InvocationTargetException in case of an error while execution
	 */
	Object executeConstructorNative(Constructor<?> constructor, final Object[] aJvmArgs)
			throws IllegalAccessException, IllegalArgumentException, InstantiationException, InvocationTargetException;

	/**
	 * Executes a method.
	 * @param method method to be executed
	 * @param objRef object-instance
	 * @param aJvmArgs parameters of the method (JVM arguments)
	 * @return return-value
	 * @throws IllegalAccessException in case of an access-violation
	 * @throws IllegalArgumentException in case of an illegal argument
	 * @throws InvocationTargetException in case of an error while execution
	 */
	Object executeMethodNative(Method method, Object objRef, final Object[] aJvmArgs)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;
}
