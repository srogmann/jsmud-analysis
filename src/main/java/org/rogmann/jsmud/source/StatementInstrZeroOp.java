package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.visitors.InstructionVisitor;

/**
 * Zero operand instruction on stack.
 */
public class StatementInstrZeroOp extends StatementInstr<InsnNode>{

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. DUP or ICONST_1
	 */
	public StatementInstrZeroOp(InsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	protected void render(StringBuilder sb, MethodNode methodNode) {
		switch (insn.getOpcode()) {
		default:
			sb.append(InstructionVisitor.displayInstruction(insn, methodNode));
		}
		sb.append(';');
	}

}
