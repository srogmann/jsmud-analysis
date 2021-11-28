package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.visitors.InstructionVisitor;

/**
 * One instruction.
 * 
 * @param <A> type of instruction
 */
public abstract class StatementInstr<A extends AbstractInsnNode> extends StatementBase {

	/** instruction-node */
	protected final A insn;

	/**
	 * Constructor
	 * @param insn instruction
	 */
	protected StatementInstr(final A insn) {
		this.insn = insn;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		MethodNode methodNode = null;
		render(sb, methodNode);
	}

	/**
	 * Renders an instruction.
	 * @param sb string-builder
	 * @param methodNode optional method-node (used to display local-variables in variable instructions)
	 */
	protected void render(StringBuilder sb, MethodNode methodNode) {
		sb.append(InstructionVisitor.displayInstruction(insn, methodNode));
	}

	/**
	 * Gets the corresponding bytecode-instruction.
	 * @return instruction
	 */
	public A getInsn() {
		return insn;
	}

}
