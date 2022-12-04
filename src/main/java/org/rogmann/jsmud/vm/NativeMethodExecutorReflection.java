package org.rogmann.jsmud.vm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Execution of methods via reflection.
 */
public class NativeMethodExecutorReflection implements NativeMethodExecutor {

	/** {@inheritDoc} */
	@Override
	public Object executeConstructorNative(Constructor<?> constructor, Object[] aJvmArgs)
			throws IllegalAccessException, IllegalArgumentException, InstantiationException, InvocationTargetException {
		constructor.setAccessible(true);
		final Object newObj = constructor.newInstance(aJvmArgs);
		return newObj;
	}

	/** {@inheritDoc} */
	@Override
	public Object executeMethodNative(Method methodExec, Object objRef, Object[] initargs)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		final Object returnObj;
		methodExec.setAccessible(true);
		returnObj = methodExec.invoke(objRef, initargs);
		return returnObj;
	}

}
