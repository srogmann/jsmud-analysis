package org.rogmann.jsmud.source;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.JumpInsnNode;

/**
 * if-Statement.
 */
public class StatementIf extends StatementInstr<JumpInsnNode> {

	/** destination label */
	protected final Label labelDest;

	/** display-name of label */
	private final String labelName;

	/** condition-expression */
	private final ExpressionBase<?> exprCond;

	/**
	 * Constructor
	 * @param insn jump-instruction, e.g. IF_ICMPLE
	 * @param exprCond conditional-expression
	 * @param labelDest destination label
	 * @param labelName display-name of label
	 */
	public StatementIf(final JumpInsnNode insn, final ExpressionBase<?> exprCond, final Label labelDest, final String labelName) {
		super(insn);
		this.exprCond = exprCond;
		this.labelDest = labelDest;
		this.labelName = labelName;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("if").append(' ').append('(');
		exprCond.render(sb);
		sb.append(')').append(' ');
		sb.append("goto").append(' ').append(labelName);
		sb.append(';');
	}

}
