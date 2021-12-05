package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.visitors.InstructionVisitor;

/**
 * One instruction, displayed as bytecode only.
 * 
 * @param <A> type of instruction
 */
public class StatementInstrPlain<A extends AbstractInsnNode> extends StatementInstr<A> {

	/** method-node */
	private final MethodNode methodNode;

	/**
	 * Constructor
	 * @param insn instruction
	 * @param methodNode method
	 */
	protected StatementInstrPlain(final A insn, final MethodNode methodNode) {
		super(insn);
		this.methodNode = methodNode;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append(InstructionVisitor.displayInstruction(insn, methodNode));
	}

}
