package org.rogmann.jsmud.vm;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Visitor of bytecode-execution.
 */
public interface JvmExecutionVisitor {

	/**
	 * Called when a thread is started.
	 */
	void visitThreadStarted(Thread startedThread);

	/**
	 * Called when a class is loaded.
	 */
	void visitLoadClass(Class<?> loadedClass);

	/**
	 * Called before execution of a method or constructor.
	 * @param currClass current class
	 * @param method method
	 * @param frame frame of the execution
	 */
	void visitMethodEnter(Class<?> currClass, Executable method, MethodFrame frame);

	/**
	 * Called at the end of an execution of a method or constructor.
	 * @param currClass current class
	 * @param method method
	 * @param frame frame of the execution
	 * @param objReturn result-object
	 */
	void visitMethodExit(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn);

	/**
	 * Called in the calling method-frame at return from a method-call.
	 * @param currClass current class
	 * @param method method or constructor
	 * @param frame frame of the execution
	 * @param objReturn result-object
	 */
	void visitMethodExitBack(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn);

	/**
	 * Called before execution of a instruction.
	 * @param currClass current class
	 * @param method current method
	 * @param instr instruction to be executed
	 * @param stack current stack
	 * @param aLocals current locals
	 */
	void visitInstruction(final AbstractInsnNode instr,
			final OperandStack stack, final Object[] aLocals);

	/**
	 * Called after getting or before putting a field of an instance or class.
	 * @param opcode opcode
	 * @param owner instance or owner-class of the field
	 * @param field field
	 * @param value value
	 * @return (modified) value
	 */
	Object visitFieldAccess(final int opcode, final Object owner,
			final Field field, final Object value);

	/**
	 * Called when a thread wants to enter a monitor.
	 * @param objMonitor monitor-object
	 */
	void visitMonitorEnter(final Object objMonitor);

	/**
	 * Called when a monitor is entered.
	 * @param objMonitor monitor-object
	 * @param counter current counter (after entering)
	 */
	void visitMonitorEntered(final Object objMonitor, final Integer counter);

	/**
	 * Called when a monitor is leaved.
	 * @param objMonitor monitor-object
	 * @param counter current counter (after leaving)
	 */
	void visitMonitorExit(final Object objMonitor, final Integer counter);

	/**
	 * Reports an exception while executing a method-call.
	 * @param e thrown exception
	 */
	void invokeException(Throwable e);

	/**
	 * Called when the thread using this visitor has been unregistered.
	 * This method can be used to display statistics.
	 */
	void close();

}
