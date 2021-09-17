package org.rogmann.jsmud.vm;

/**
 * Interface of a {@link JvmExecutionVisitor}-provider.
 */
public interface JvmExecutionVisitorProvider {

	/**
	 * Creates a JVM-execution-visitor
	 * @param currentThread current thread
	 * @param visitorParent visitor of parent-thread or <code>null</code>
	 * @return execution-visitor
	 */
	JvmExecutionVisitor create(ClassRegistry vm, Thread currentThread, JvmExecutionVisitor visitorParent);

}
