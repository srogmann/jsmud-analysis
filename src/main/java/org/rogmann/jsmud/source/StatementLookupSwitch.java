package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.LookupSwitchInsnNode;

/**
 * switch-Statement (LOOKUPSWITCH-instruction).
 */
public class StatementLookupSwitch extends StatementInstr<LookupSwitchInsnNode> {

	/** key */
	private final ExpressionBase<?> exprKey;

	/** display-name of default-label */
	private final String nameDefault;

	/** display-names of case-labels */
	private final String[] aLabelName;

	/**
	 * Constructor
	 * @param insn TABLESWITCH-instruction
	 * @param exprKey index-expression
	 * @param nameDefault name of default label
	 * @param aLabelName display-name of labels
	 */
	public StatementLookupSwitch(final LookupSwitchInsnNode insn, final ExpressionBase<?> exprKey,
			final String nameDefault, final String[] aLabelName) {
		super(insn);
		this.exprKey = exprKey;
		this.nameDefault = nameDefault;
		this.aLabelName = aLabelName;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("switch").append(' ').append('(');
		exprKey.render(sb);
		sb.append(')').append(' ').append('{');
		final int numCases = insn.keys.size();
		for (int i = 0; i < numCases; i++) {
			sb.append(' ').append("case").append(' ');
			sb.append(insn.keys.get(i)).append(':').append(' ');
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
