package org.rogmann.jsmud.vm;

import java.io.Closeable;

/**
 * Interface of a {@link JvmExecutionVisitor}-provider.
 */
public interface JvmExecutionVisitorProvider extends Closeable {

	/**
	 * Creates a JVM-execution-visitor
	 * @param currentThread current thread
	 * @return execution-visitor
	 */
	JvmExecutionVisitor create(final Thread currentThread);

}
