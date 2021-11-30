package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Duplicate of an expression.
 */
public class ExpressionDuplicate<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** duplicated expression */
	private final ExpressionDuplicated<A> exprDup;

	/**
	 * Constructor
	 * @param exprDup expression duplicated
	 */
	public ExpressionDuplicate(final ExpressionDuplicated<A> exprDup) {
		super(exprDup.insn);
		this.exprDup = exprDup;
	}

	/**
	 * Gets the expression carrying the expression whose value was duplicated.
	 * @return expression
	 */
	public ExpressionDuplicated<A> getExpression() {
		return exprDup;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append(exprDup.getDummyName());
	}

}
