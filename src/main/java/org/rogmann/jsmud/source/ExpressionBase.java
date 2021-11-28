package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Expression which evaluates to an object or primitive.
 * 
 * @param <A> type of instruction
 */
public abstract class ExpressionBase<A extends AbstractInsnNode> extends StatementInstr<A> {

	/**
	 * Constructor.
	 * @param insn instruction
	 */
	protected ExpressionBase(A insn) {
		super(insn);
	}
	
}
