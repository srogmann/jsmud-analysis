package org.rogmann.jsmud.vm;

/**
 * Interface for filtering classes to be simulated by the interpreter.
 */
public interface ClassExecutionFilter {

	/**
	 * Checks if a given class should be interpreted by the simulator.
	 * @param clazz class to be executed
	 * @return <code>true</code> if the class should be interpreted by the simulator, <code>false</code> if the class should be executed by the underlying JVM
	 */
	boolean isClassToBeSimulated(final Class<?> clazz);

}
