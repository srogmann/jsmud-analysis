package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;

/**
 * Cast-expression with primitive type.
 */
public class ExpressionCastPrimitive extends ExpressionBase<InsnNode> {

	/** primitive type */
	private final Type primitiveType;
	/** expression to be casted */
	private final ExpressionBase<?> expr;

	/**
	 * Constructor
	 * @param insn instruction, e.g. I2S
	 * @param primitiveType primitive type
	 * @param expr expression to be casted
	 */
	public ExpressionCastPrimitive(final InsnNode insn, final Type primitiveType,
			final ExpressionBase<?> expr) {
		super(insn);
		this.primitiveType = primitiveType;
		this.expr = expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		boolean isNeedBrackets = !ExpressionInfixBinary.isNeedsNoBrackets(expr);
		sb.append('(');
		sb.append(primitiveType.getClassName());
		sb.append(')');
		sb.append(' ');
		if (isNeedBrackets) {
			sb.append('(');
		}
		expr.render(sb);
		if (isNeedBrackets) {
			sb.append(')');
		}
	}

}
