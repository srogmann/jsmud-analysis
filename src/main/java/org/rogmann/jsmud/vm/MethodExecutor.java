package org.rogmann.jsmud.vm;

/**
 * Interface of a generic method executor.
 */
public interface MethodExecutor {

	/**
	 * Executes or simulates a method execution.
	 * @param executable implementation dependent object describing the method to be simulated 
	 * @param obj object-instance
	 * @param args JVM-arguments
	 * @return result
	 */
	Object execute(final Object executable, final Object obj, final Object[] args);

}
