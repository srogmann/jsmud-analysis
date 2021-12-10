package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.OpcodeDisplay;

/**
 * Type-instruction of an expression.
 */
public class ExpressionTypeInstr extends ExpressionBase<TypeInsnNode>{

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. NEW
	 */
	public ExpressionTypeInstr(TypeInsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.NEW) {
			throw new JvmException("The NEW-instruction should be rendered at INVOKESPECIAL <init>");
		}
		else {
			throw new JvmException("Unexpected opcode " + opcode);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s %s);",
				getClass().getSimpleName(),
				OpcodeDisplay.lookup(insn.getOpcode()), insn.desc);
	}

}
