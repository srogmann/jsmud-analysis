package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Duplicate of an expression.
 */
public class ExpressionDuplicate<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** duplicated expression */
	private final StatementExpressionDuplicated<A> exprDup;

	/**
	 * Constructor
	 * @param exprDup statement of expression duplicated
	 */
	public ExpressionDuplicate(final StatementExpressionDuplicated<A> exprDup) {
		super(exprDup.insn);
		this.exprDup = exprDup;
	}

	/**
	 * Gets the statement carrying the expression whose value was duplicated.
	 * @return expression
	 */
	public StatementExpressionDuplicated<A> getStatementExpressionDuplicated() {
		return exprDup;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append(exprDup.getDummyName());
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s);",
				getClass().getSimpleName(), exprDup);
	}

}
