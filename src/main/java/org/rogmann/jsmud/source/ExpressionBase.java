package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
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

	/**
	 * Returns the type of the expression.
	 * @return type or <code>null</code> if unknown
	 */
	@SuppressWarnings("static-method")
	public Type getType() {
		return null;
	}
}
