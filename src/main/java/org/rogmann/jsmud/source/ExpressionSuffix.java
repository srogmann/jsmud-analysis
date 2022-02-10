package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

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
	public Type getType() {
		final Type type;
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.ARRAYLENGTH) {
			type = Type.INT_TYPE;
		}
		else if (opcode == Opcodes.IINC) {
			type = Type.INT_TYPE;
		}
		else {
			throw new SourceRuntimeException("Unexpected opcode " + opcode);
		}
		return type;
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
		else if (opcode == Opcodes.IINC) {
			IincInsnNode ii = (IincInsnNode) insn;
			if (ii.incr == +1) {
				sb.append("++");
			}
			else if (ii.incr == -1) {
				sb.append("--");
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected incrment %d for local %d",
						Integer.valueOf(ii.incr), Integer.valueOf(ii.var)));
			}
		}
		else {
			throw new SourceRuntimeException("Unexpected opcode " + opcode);
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
		final String suffix;
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.ARRAYLENGTH) {
			suffix = ".length";
		}
		else if (opcode == Opcodes.IINC) {
			final IincInsnNode ii = (IincInsnNode) insn;
			final int incr = ii.incr;
			suffix = (incr == +1) ? "++" : ((incr == -1) ? "--" : "+" + incr); 
		}
		else {
			throw new SourceRuntimeException("Unexpected opcode " + opcode);
		}
		return String.format("%s(%s%s);",
				getClass().getSimpleName(), expr, suffix);
	}

}
