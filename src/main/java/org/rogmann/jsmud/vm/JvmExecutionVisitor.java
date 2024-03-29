package org.rogmann.jsmud.vm;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rogmann.jsmud.visitors.MessagePrinter;

/**
 * Visitor of bytecode-execution.
 */
public interface JvmExecutionVisitor {

	/**
	 * Called when a thread is started.
	 * @param startedThread started thread
	 */
	void visitThreadStarted(Thread startedThread);

	/**
	 * Called when a class is loaded.
	 * @param loadedClass class loaded
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

	/**
	 * Gets the show-instructions flag.
	 * @return show-instructions flag
	 */
	boolean isDumpJreInstructions();
	/**
	 * Gets the dump class-usage statistics flag.
	 * @return dump class-usage statistics flag
	 */
	boolean isDumpClassStatistic();
	/**
	 * Gets the dump class-usage statistics flag.
	 * @return dump class-usage statistics flag
	 */
	boolean isDumpInstructionStatistic();
	/**
	 * Gets the dump method-call-trace flag.
	 * @return dump method-call-trace flag
	 */
	boolean isDumpMethodCallTrace();

	/**
	 * Sets an optional statistics-addon to be called at visitor-close.
	 * @param statisticsAddon addon
	 */
	void setStatisticsAddon(Consumer<MessagePrinter> statisticsAddon);
}
