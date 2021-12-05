package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Conditional operator, (exprCond) ? expr1 : expr2.
 */
public class ExpressionConditionalOperator<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** operator */
	private final ExpressionBase<?> exprCond;
	/** argument 1 */
	private final ExpressionBase<?> expr1;
	/** argument 2 */
	private final ExpressionBase<?> expr2;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. IADD
	 * @param exprCond conditional expression
	 * @param exprArg1 first value
	 * @param exprArg2 second value
	 */
	public ExpressionConditionalOperator(final A insn, final ExpressionBase<?> exprCond,
			final ExpressionBase<?> exprArg1, final ExpressionBase<?> exprArg2) {
		super(insn);
		this.exprCond = exprCond;
		this.expr1 = exprArg1;
		this.expr2 = exprArg2;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append('(');
		sb.append('(');
		exprCond.render(sb);
		sb.append(')');
		sb.append(' ').append('?').append(' ');
		expr1.render(sb);
		sb.append(' ').append(':').append(' ');
		expr2.render(sb);
		sb.append(')');
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s ? %s : %s);",
				getClass().getSimpleName(), exprCond, expr1, expr2);
	}

}
