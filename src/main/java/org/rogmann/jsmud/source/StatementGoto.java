package org.rogmann.jsmud.source;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.JumpInsnNode;

/**
 * goto-Statement.
 */
public class StatementGoto extends StatementInstr<JumpInsnNode> {

	/** destination label */
	protected final Label labelDest;

	/** display-name of label */
	private final String labelName;

	/**
	 * Constructor
	 * @param insn jump-instruction, e.g. GOTO
	 * @param labelDest destination label
	 * @param labelName display-name of label
	 */
	public StatementGoto(final JumpInsnNode insn, final Label labelDest, final String labelName) {
		super(insn);
		this.labelDest = labelDest;
		this.labelName = labelName;
	}

	/**
	 * Gets the name of the destination-label.
	 * @return label-name
	 */
	public String getLabelName() {
		return labelName;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("goto").append(' ');
		sb.append(labelName);
		sb.append(';');
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(goto %s);",
				getClass().getSimpleName(), labelName);
	}

}
