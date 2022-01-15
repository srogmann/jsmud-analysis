package org.rogmann.jsmud.vm;

import org.objectweb.asm.tree.MethodInsnNode;

/**
 * This interfaces can be used to modify method-executions.
 */
public interface JvmInvocationHandler {

	/**
	 * Called before an invocation of a static method.
	 * @param methodFrame current method-frame
	 * @param mi method-invocation-instruction
	 * @param stack current operand-stack
	 * @return how to continue execution
	 * @throws Exception in case of an unhandled exception
	 */
	InvokeFlow preprocessStaticCall(MethodFrame methodFrame, MethodInsnNode mi, OperandStack stack) throws Throwable;

	/**
	 * Called before an invocation of an instance-method (or constructor).
	 * This offers the possibility to replace the invocation with an own implementation,
	 * return InvokeFlow.EXEC_OK in that case.
	 * 
	 * @param methodFrame current method-frame
	 * @param mi method-invocation-instruction
	 * @param objRefStack instance-object
	 * @param stack current operand-stack
	 * @return how to continue execution
	 * @throws Throwable in case of an unhandled exception
	 */
	InvokeFlow preprocessInstanceCall(MethodFrame methodFrame, MethodInsnNode mi,
			Object objRefStack, OperandStack stack) throws Throwable;

	/**
	 * Called before an invocation of an INVOKESPECIAL-execution (e.g. constructor-call).
	 * @param mi method-invocation-instruction
	 * @param methodFrame current method-frame
	 * @param stack current operand-stack
	 */
	void preprocessInvokeSpecialCall(MethodInsnNode mi, MethodFrame frame, OperandStack stack);

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
