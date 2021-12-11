package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
	public Type getType() {
		final Type type;
		if (insn == null) {
			type = null;
		}
		else {
			final int opcode = insn.getOpcode();
			switch (opcode) {
			case Opcodes.ACONST_NULL: type = null; break;
			case Opcodes.ICONST_M1:
			case Opcodes.ICONST_0:
			case Opcodes.ICONST_1:
			case Opcodes.ICONST_2:
			case Opcodes.ICONST_3:
			case Opcodes.ICONST_4:
			case Opcodes.ICONST_5: type = Type.INT_TYPE; break;
			case Opcodes.LCONST_0:
			case Opcodes.LCONST_1: type = Type.LONG_TYPE; break;
			case Opcodes.FCONST_0:
			case Opcodes.FCONST_1: type = Type.FLOAT_TYPE; break;
			case Opcodes.DCONST_0:
			case Opcodes.DCONST_1: type = Type.DOUBLE_TYPE; break;
			default:
				throw new JvmException(String.format("Unexpected opcode %d", Integer.valueOf(opcode)));
			}
		}
		return type;
	}

	/**
	 * Checks if this expression contains an ICONST-instruction.
	 * @return ICONST-flag
	 */
	public boolean isIConst() {
		final int opcode = insn.getOpcode();
		return opcode == Opcodes.ICONST_M1
				|| opcode == Opcodes.ICONST_0
				|| opcode == Opcodes.ICONST_1
				|| opcode == Opcodes.ICONST_2
				|| opcode == Opcodes.ICONST_3
				|| opcode == Opcodes.ICONST_4;
	}

	/**
	 * Gets the constant value.
	 * @return value as reference (non-primitive)
	 */
	public Object getValue() {
		final Object val;
		final int opcode = insn.getOpcode();
		switch (opcode) {
		case Opcodes.ACONST_NULL: val = null; break;
		case Opcodes.ICONST_M1: val = Integer.valueOf(-1); break;
		case Opcodes.ICONST_0: val = Integer.valueOf(0); break;
		case Opcodes.ICONST_1: val = Integer.valueOf(1); break;
		case Opcodes.ICONST_2: val = Integer.valueOf(2); break;
		case Opcodes.ICONST_3: val = Integer.valueOf(3); break;
		case Opcodes.ICONST_4: val = Integer.valueOf(4); break;
		case Opcodes.ICONST_5: val = Integer.valueOf(5); break;
		case Opcodes.LCONST_0: val = Long.valueOf(0); break;
		case Opcodes.LCONST_1: val = Long.valueOf(1); break;
		case Opcodes.FCONST_0: val = Float.valueOf(0); break;
		case Opcodes.FCONST_1: val = Float.valueOf(1); break;
		case Opcodes.DCONST_0: val = Double.valueOf(0); break;
		case Opcodes.DCONST_1: val = Double.valueOf(1); break;
		default:
			throw new JvmException(String.format("Unexpected opcode %d", Integer.valueOf(opcode)));
		}
		return val;
	}
	
	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s);",
				getClass().getSimpleName(), OpcodeDisplay.lookup(insn.getOpcode()));
	}

}
