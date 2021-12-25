package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Prefix-expression, e.g. "++", "-" or casting
 */
public class ExpressionPrefix<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** prefix */
	private final String prefix;

	/** argument */
	private final ExpressionBase<?> expr;

	/**
	 * Constructor
	 * @param insn instruction, e.g. CHECKCAST
	 * @param pre prefix of expression
	 * @param expr argument
	 */
	public ExpressionPrefix(final A insn, final String prefix, final ExpressionBase<?> expr) {
		super(insn);
		this.prefix = prefix;
		this.expr = expr;
	}

	/** {@inheritDoc} */
	@Override
	public Type getType() {
		Type type = null;
		if ("-".equals(prefix)) {
			type = expr.getType();
		}
		return type;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append(prefix);

		final boolean needsBrackets = !ExpressionSuffix.isNeedsNoBrackets(expr);
		if (needsBrackets) {
			sb.append('(');
		}
		expr.render(sb);
		if (needsBrackets) {
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
		return String.format("%s(%s%s);",
				getClass().getSimpleName(),
				prefix, expr);
	}

}
