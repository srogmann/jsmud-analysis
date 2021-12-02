package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 * switch-Statement (TABLESWITCH-instruction).
 */
public class StatementTableSwitch extends StatementInstr<TableSwitchInsnNode> {

	/** condition-expression */
	private final ExpressionBase<?> exprIndex;

	/** display-name of default-label */
	private final String nameDefault;

	/** display-names of case-labels */
	private final String[] aLabelName;

	/**
	 * Constructor
	 * @param insn TABLESWITCH-instruction
	 * @param exprIndex index-expression
	 * @param nameDefault destination label
	 * @param aLabelName display-name of label
	 */
	public StatementTableSwitch(final TableSwitchInsnNode insn, final ExpressionBase<?> exprIndex,
			final String nameDefault, final String[] aLabelName) {
		super(insn);
		this.exprIndex = exprIndex;
		this.nameDefault = nameDefault;
		this.aLabelName = aLabelName;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("switch").append(' ').append('(');
		exprIndex.render(sb);
		sb.append(')').append(' ').append('{');
		final int lMin = insn.min;
		final int lMax = insn.max;
		final int num = lMax - lMin + 1;
		for (int i = 0; i < num; i++) {
			sb.append(' ').append("case").append(' ');
			sb.append(i).append(':').append(' ');
			sb.append("goto").append(' ');
			sb.append(aLabelName[i]);
			sb.append(';');
		}
		sb.append(' ').append("default").append(':').append(' ');
		sb.append("goto").append(' ');
		sb.append(nameDefault);
		sb.append(';');
		sb.append('}');
	}

}
