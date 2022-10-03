package org.rogmann.jsmud.visitors;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.MethodFrame;
import org.rogmann.jsmud.vm.OperandStack;

/**
 * Delegates visitor-calls to an internal visitor.
 * 
 * <p>One can use this class to override some methods.</p>
 */
public class ExecutionVisitorDelegation implements JvmExecutionVisitor {

	/** wrapped visitor */
	protected final JvmExecutionVisitor visitor;

	/**
	 * Constructor
	 * @param visitor wrapped visitor
	 */
	public ExecutionVisitorDelegation(final JvmExecutionVisitor visitor) {
		this.visitor = visitor;
	}

	/** {@inheritDoc} */
	@Override
	public void visitThreadStarted(Thread startedThread) {
		visitor.visitThreadStarted(startedThread);
	}

	/** {@inheritDoc} */
	@Override
	public void visitLoadClass(Class<?> loadedClass) {
		visitor.visitLoadClass(loadedClass);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodEnter(Class<?> currClass, Executable method, MethodFrame frame) {
		visitor.visitMethodEnter(currClass, method, frame);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExit(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn) {
		visitor.visitMethodExit(currClass, method, frame, objReturn);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExitBack(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn) {
		visitor.visitMethodExitBack(currClass, method, frame, objReturn);
	}

	/** {@inheritDoc} */
	@Override
	public void visitInstruction(AbstractInsnNode instr, OperandStack stack, Object[] aLocals) {
		visitor.visitInstruction(instr, stack, aLocals);
	}

	/** {@inheritDoc} */
	@Override
	public Object visitFieldAccess(int opcode, Object owner, Field field, Object value) {
		return visitor.visitFieldAccess(opcode, owner, field, value);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEnter(Object objMonitor) {
		visitor.visitMonitorEnter(objMonitor);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEntered(Object objMonitor, Integer counter) {
		visitor.visitMonitorEntered(objMonitor, counter);
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorExit(Object objMonitor, Integer counter) {
		visitor.visitMonitorExit(objMonitor, counter);
	}

	/** {@inheritDoc} */
	@Override
	public void invokeException(Throwable e) {
		visitor.invokeException(e);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		visitor.close();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDumpJreInstructions() {
		return visitor.isDumpJreInstructions();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDumpClassStatistic() {
		return visitor.isDumpClassStatistic();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDumpInstructionStatistic() {
		return visitor.isDumpInstructionStatistic();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDumpMethodCallTrace() {
		return visitor.isDumpMethodCallTrace();
	}

	/** {@inheritDoc} */
	@Override
	public void setStatisticsAddon(Consumer<MessagePrinter> statisticsAddon) {
		visitor.setStatisticsAddon(statisticsAddon);
	}

}
