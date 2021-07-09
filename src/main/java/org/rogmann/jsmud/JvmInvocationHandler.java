package org.rogmann.jsmud;

import org.objectweb.asm.tree.MethodInsnNode;

/**
 * This interfaces can be used to modify method-executions.
 */
public interface JvmInvocationHandler {

	/**
	 * Called before an invocation of a static method.
	 * @param frame current method-frame
	 * @param mi method-invocation-instruction
	 * @param stack current operand-stack
	 * @return continue-while-flag in case of a execution, else <code>null</code> for normal continuation
	 * @throws Exception in case of an unhandled exception
	 */
	Boolean preprocessStaticCall(MethodFrame methodFrame, MethodInsnNode mi, OperandStack stack) throws Exception;

	/**
	 * Called before an invocation of an instance-method (or constructor).
	 * @param frame current method-frame
	 * @param mi method-invocation-instruction
	 * @param objRefStack instance-object
	 * @param stack current operand-stack
	 * @return continue-while-flag in case of a execution, else <code>null</code> for normal continuation
	 * @throws Throwable in case of an unhandled exception
	 */
	Boolean preprocessInstanceCall(MethodFrame methodFrame, MethodInsnNode mi,
			Object objRefStack, OperandStack stack) throws Throwable;

	/**
	 * Called after a invocation of a method (or constructor).
	 * @param frame current method-frame
	 * @param mi method-invocation-instruction
	 * @param stack current operand-stack
	 * @return continue-while-flag
	 * @throws Throwable in case of an unhandled throwable
	 */
	boolean postprocessCall(MethodFrame frame, MethodInsnNode mi, OperandStack stack) throws Throwable;

}
