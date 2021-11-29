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
		final Class<?> classPrimitive = AtypeEnum.lookupAtypeClass(insn.operand);
		sb.append(classPrimitive.getSimpleName());
		sb.append('[');
		exprCount.render(sb);
		sb.append(']');
	}

}
