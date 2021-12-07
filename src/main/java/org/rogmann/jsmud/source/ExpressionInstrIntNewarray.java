package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.IntInsnNode;
import org.rogmann.jsmud.vm.AtypeEnum;

/**
 * int-operand newarray-instruction.
 */
public class ExpressionInstrIntNewarray extends ExpressionBase<IntInsnNode>{

	/** length of new array */
	private ExpressionBase<?> exprCount;

	/**
	 * Constructor
	 * @param insn type-instruction, NEWARRAY 
	 * @param exprCount length of new array
	 */
	public ExpressionInstrIntNewarray(IntInsnNode insn, final ExpressionBase<?> exprCount) {
		super(insn);
		this.exprCount = exprCount;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new").append(' ');
		final String displayName = computeDisplayName();
		sb.append(displayName);
		sb.append('[');
		exprCount.render(sb);
		sb.append(']');
	}

	private String computeDisplayName() {
		final Class<?> classPrimitive = AtypeEnum.lookupAtypeClass(insn.operand);
		final String displayName = classPrimitive.getSimpleName();
		return displayName;
	}

	/**
	 * Gets the count-expression.
	 * @return count-expression
	 */
	public ExpressionBase<?> getExprCount() {
		return exprCount;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(new %s[%s]);",
				getClass().getSimpleName(), computeDisplayName(), exprCount);
	}

}
