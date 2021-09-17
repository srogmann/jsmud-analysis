package org.rogmann.jsmud.vm;

/**
 * Interface of a {@link JvmExecutionVisitor}-provider.
 */
public interface JvmExecutionVisitorProvider {

	/**
	 * Creates a JVM-execution-visitor
	 * @param currentThread current thread
	 * @return execution-visitor
	 */
	JvmExecutionVisitor create(VM vm, Thread currentThread);

}
