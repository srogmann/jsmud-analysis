package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Duplication of an expression, original saved as statement storing this expression
 * into a temporary variable.
 */
public class StatementExpressionDuplicated<A extends AbstractInsnNode> extends StatementInstr<A> {

	/** duplicated expression */
	private final ExpressionBase<A> exprDuplicated;

	/** dummy-name */
	private final String dummyName;

	/**
	 * Constructor
	 * @param expr expression to be duplicated
	 * @param dummyName name of duplicated expression
	 */
	public StatementExpressionDuplicated(final ExpressionBase<A> expr, final String dummyName) {
		super(expr.insn);
		this.exprDuplicated = expr;
		this.dummyName = dummyName;
	}

	/**
	 * Gets the expression whose value was duplicated.
	 * @return expression
	 */
	public ExpressionBase<A> getExpression() {
		return exprDuplicated;
	}

	/**
	 * Gets the dummy-name of the duplicated expression.
	 * @return dummy-name
	 */
	public String getDummyName() {
		return dummyName;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append(dummyName);
		sb.append(' ').append('=').append(' ');
		exprDuplicated.render(sb);
		sb.append(';');
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s->%s);",
				getClass().getSimpleName(), exprDuplicated, dummyName);
	}

}
