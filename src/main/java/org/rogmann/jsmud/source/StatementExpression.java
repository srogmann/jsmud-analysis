package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * An expression converted into a statement, e.g. because of an POP-instruction.
 */
public class StatementExpression<A extends AbstractInsnNode> extends StatementInstr<A> {

	/** expression */
	private final ExpressionBase<A> expr;

	/**
	 * Constructor
	 * @param expr expression
	 */
	public StatementExpression(final ExpressionBase<A> expr) {
		super(expr.insn);
		this.expr = expr;
	}

	/**
	 * Gets the expression.
	 * @return expression
	 */
	public ExpressionBase<A> getExpression() {
		return expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		expr.render(sb);
		sb.append(';');
	}

}
