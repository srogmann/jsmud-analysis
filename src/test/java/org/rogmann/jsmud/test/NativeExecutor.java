package org.rogmann.jsmud.test;

import java.util.concurrent.Callable;

/**
 * Helper-class to execute a method without JSMUD.
 * This has to be configured in a filter.
 */
public class NativeExecutor {

	/**
	 * Executes a callable.
	 * @param callable callable
	 * @param <T> return-type of the callable
	 * @return result
	 */
	public static <T> T executeCallable(Callable<T> callable) {
		try {
			return callable.call();
		} catch (Exception e) {
			throw new RuntimeException("Exception while executing callable", e);
		}
	}
}
