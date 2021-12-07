package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.IntInsnNode;

/**
 * int-operand constant instruction.
 */
public class ExpressionInstrIntConstant extends ExpressionBase<IntInsnNode>{

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. BIPUSH 
	 */
	public ExpressionInstrIntConstant(final IntInsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		switch (opcode) {
		case Opcodes.BIPUSH: sb.append(insn.operand); break;
		case Opcodes.SIPUSH: sb.append(insn.operand); break;
		default:
			super.render(sb);
		}
	}

	/**
	 * Gets the int-operand.
	 * @return int-value
	 */
	public int getIntValue() {
		return insn.operand;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%d);",
				getClass().getSimpleName(), Integer.valueOf(insn.operand));
	}

}
