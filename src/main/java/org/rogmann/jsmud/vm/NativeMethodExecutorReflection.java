package org.rogmann.jsmud.vm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Execution of methods via reflection.
 */
public class NativeMethodExecutorReflection implements NativeMethodExecutor {

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
