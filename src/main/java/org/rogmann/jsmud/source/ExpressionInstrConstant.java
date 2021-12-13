package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LdcInsnNode;
import org.rogmann.jsmud.vm.Utils;

/**
 * Expression which loads a constant value.
 */
public class ExpressionInstrConstant extends ExpressionBase<LdcInsnNode> {
	/**
	 * Constructor
	 * @param insn instruction
	 */
	public ExpressionInstrConstant(final LdcInsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final Object cst = insn.cst;
		Utils.appendConstant(sb, cst);
	}

	/** {@inheritDoc} */
	@Override
	public Type getType() {
		final Type type;
		final Object cst = insn.cst;
		if (cst instanceof Integer) {
			type = Type.INT_TYPE;
		}
		else if (cst instanceof String) {
			type = Type.getType(String.class);
		}
		else if (cst instanceof Long) {
			type = Type.LONG_TYPE;
		}
		else if (cst instanceof Float) {
			type = Type.FLOAT_TYPE;
		}
		else if (cst instanceof Double) {
			type = Type.DOUBLE_TYPE;
		}
		else if (cst instanceof Type) {
			type = (Type) cst;
		}
		else if (cst instanceof Byte) {
			type = Type.BYTE_TYPE;
		}
		else if (cst instanceof Boolean) {
			type = Type.BOOLEAN_TYPE;
		}
		else if (cst instanceof Character) {
			type = Type.CHAR_TYPE;
		}
		else if (cst instanceof Short) {
			type = Type.SHORT_TYPE;
		}
		else {
			throw new RuntimeException(String.format("Unexpected type (%s): %s",
					cst.getClass(), cst));
		}
		return type;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s)", getClass().getSimpleName(), insn.cst);
	}

}
