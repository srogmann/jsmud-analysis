package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Compare two expressions (e.g. LCMP, FCMPL).
 */
public class ExpressionCompare extends ExpressionBase<InsnNode> {

	/** argument 1 */
	private final ExpressionBase<?> expArg1;
	/** argument 2 */
	private final ExpressionBase<?> expArg2;

	/**
	 * Constructor
	 * @param insn compare-instruction, e.g. LCMP or FCMPL
	 * @param exprArg1 first argument
	 * @param exprArg2 second argument
	 */
	public ExpressionCompare(final InsnNode insn,
			final ExpressionBase<?> exprArg1, final ExpressionBase<?> exprArg2) {
		super(insn);
		this.expArg1 = exprArg1;
		this.expArg2 = exprArg2;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		// Java SE specs, § 3.5: The compiler chooses the variant of the comparison instruction
		//   for the appropriate type that produces the same result whether the comparison fails
		//   on non-NaN values or encounters a NaN.
		switch (insn.getOpcode()) {
		case Opcodes.LCMP:
			renderCompare(sb, "Long");
			break;
		case Opcodes.FCMPL:
			renderNanCheck(sb, "Float", -1);
			renderCompare(sb, "Float");
			sb.append(')');
			break;
		case Opcodes.FCMPG:
			renderNanCheck(sb, "Float", 1);
			renderCompare(sb, "Float");
			sb.append(')');
			break;
		case Opcodes.DCMPL:
			renderNanCheck(sb, "Double", -1);
			renderCompare(sb, "Double");
			sb.append(')');
			break;
		case Opcodes.DCMPG:
			renderNanCheck(sb, "Double", 1);
			renderCompare(sb, "Double");
			sb.append(')');
			break;
		default: throw new JvmException("Unexpected opcode " + insn.getOpcode());
		}
	}

	/**
	 * Renders a NaN-check
	 * @param sb string-builder of expression
	 * @param type type of number
	 * @param resNan result in case of NaN
	 */
	public void renderNanCheck(StringBuilder sb, String type, int resNan) {
		// TODO temporary variable in case of complex expression.
		if (isExprConstant(expArg1)) {
			sb.append(type);
			sb.append('.').append("isNaN");
			sb.append('(');
			expArg2.render(sb);
			sb.append(')');
		}
		else if (isExprConstant(expArg2)) {
			sb.append(type);
			sb.append('.').append("isNaN");
			sb.append('(');
			expArg1.render(sb);
			sb.append(')');
		}
		else {
			sb.append('(');
			sb.append(type);
			sb.append('.').append("isNaN");
			sb.append('(');
			expArg1.render(sb);
			sb.append(')');
			sb.append("||");
			sb.append(type);
			sb.append('.').append("isNaN");
			sb.append('(');
			expArg2.render(sb);
			sb.append(')');
		}
		sb.append('?');
		sb.append(resNan);
		sb.append(':');
	}

	/**
	 * Checks if the expression is a known constant (we assume NaN in that case).
	 * @param expr expression
	 * @return constant-flag
	 */
	public static boolean isExprConstant(final ExpressionBase<?> expr) {
		return expr instanceof ExpressionInstrZeroConstant
				|| expr instanceof ExpressionInstrConstant;
	}

	/**
	 * Renders a compare-call
	 * @param sb string-builder of expression
	 * @param type type of number, e.g. "Long"
	 */
	public void renderCompare(StringBuilder sb, final String type) {
		sb.append(type);
		sb.append('.');
		sb.append("compare");
		sb.append('(');
		expArg1.render(sb);
		sb.append(',').append(' ');
		expArg2.render(sb);
		sb.append(')');
	}

	public static boolean isNeedsNoBrackets(final ExpressionBase<?> expr) {
		return (expr instanceof ExpressionInstrConstant
				|| expr instanceof ExpressionInstrZeroConstant
				|| expr instanceof ExpressionVariableLoad);
	}

}
