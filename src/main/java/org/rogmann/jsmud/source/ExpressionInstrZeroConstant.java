package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.OpcodeDisplay;

/**
 * Zero operand constant-instruction on stack.
 */
public class ExpressionInstrZeroConstant extends ExpressionBase<InsnNode>{

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. ICONST_1
	 */
	public ExpressionInstrZeroConstant(InsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		switch (opcode) {
		case Opcodes.ACONST_NULL: sb.append("null"); break;
		case Opcodes.ICONST_M1: sb.append(-1); break;
		case Opcodes.ICONST_0: sb.append(0); break;
		case Opcodes.ICONST_1: sb.append(1); break;
		case Opcodes.ICONST_2: sb.append(2); break;
		case Opcodes.ICONST_3: sb.append(3); break;
		case Opcodes.ICONST_4: sb.append(4); break;
		case Opcodes.ICONST_5: sb.append(5); break;
		case Opcodes.LCONST_0: sb.append("0L"); break;
		case Opcodes.LCONST_1: sb.append("1L"); break;
		case Opcodes.FCONST_0: sb.append("0f"); break;
		case Opcodes.FCONST_1: sb.append("1f"); break;
		case Opcodes.DCONST_0: sb.append("0d"); break;
		case Opcodes.DCONST_1: sb.append("1d"); break;
		default:
			throw new JvmException(String.format("Unexpected opcode %d", Integer.valueOf(opcode)));
		}
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s);",
				getClass().getSimpleName(), OpcodeDisplay.lookup(insn.getOpcode()));
	}

}
