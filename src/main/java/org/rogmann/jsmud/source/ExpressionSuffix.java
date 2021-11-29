package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Suffix-expression, e.g. ".length".
 */
public class ExpressionSuffix<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** argument */
	private final ExpressionBase<?> expr;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. ARRAYLENGTH
	 * @param expr argument
	 */
	public ExpressionSuffix(final A insn, final ExpressionBase<?> expr) {
		super(insn);
		this.expr = expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final boolean neeedsBrackets = !isNeedsNoBrackets(expr);
		if (neeedsBrackets) {
			sb.append('(');
		}
		expr.render(sb);
		if (neeedsBrackets) {
			sb.append(')');
		}
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.ARRAYLENGTH) {
			sb.append('.').append("length");
		}
		else {
			throw new JvmException("Unexpected opcode " + opcode);
		}
	}

	public static boolean isNeedsNoBrackets(final ExpressionBase<?> expr) {
		return (expr instanceof ExpressionInstrConstant
				|| expr instanceof ExpressionInstrZeroConstant
				|| expr instanceof ExpressionVariableLoad);
	}

}
