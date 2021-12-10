package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Binary infix-expression, e.g. "a + b".
 */
public class ExpressionInfixBinary<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** operator */
	private final String operator;
	/** argument 1 */
	private final ExpressionBase<?> expArg1;
	/** argument 2 */
	private final ExpressionBase<?> expArg2;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. IADD
	 * @param operator operator, e.g. "+"
	 * @param exprArg1 first argument
	 * @param exprArg2 second argument
	 */
	public ExpressionInfixBinary(final A insn, final String operator,
			final ExpressionBase<?> exprArg1, final ExpressionBase<?> exprArg2) {
		super(insn);
		this.operator = operator;
		this.expArg1 = exprArg1;
		this.expArg2 = exprArg2;
	}

	/** {@inheritDoc} */
	@Override
	public Type getType() {
		return expArg1.getType();
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		// Is arg1 the same infix-operator?
		boolean isSameOperator = false;
		if (expArg1 instanceof ExpressionInfixBinary) {
			ExpressionInfixBinary<?> exprIb1 = (ExpressionInfixBinary<?>) expArg1;
			if (exprIb1.operator.equals(operator)) {
				isSameOperator = true;
			}
		}

		final boolean isNeedBrackets1 = !isNeedsNoBrackets(expArg1) && !isSameOperator;
		final boolean isNeedBrackets2 = !isNeedsNoBrackets(expArg2);
		if (isNeedBrackets1) {
			sb.append('(');
		}
		expArg1.render(sb);
		if (isNeedBrackets1) {
			sb.append(')');
		}
		sb.append(' ').append(operator).append(' ');
		if (isNeedBrackets2) {
			sb.append('(');
		}
		expArg2.render(sb);
		if (isNeedBrackets2) {
			sb.append(')');
		}
	}

	public static boolean isNeedsNoBrackets(final ExpressionBase<?> expr) {
		return (expr instanceof ExpressionInstrConstant
				|| expr instanceof ExpressionInstrZeroConstant
				|| expr instanceof ExpressionVariableLoad);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s %s %s);",
				getClass().getSimpleName(), expArg1, operator, expArg2);
	}

}
