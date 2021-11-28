package org.rogmann.jsmud.source;

import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

/**
 * Label (possible destination of goto-instruction).
 */
public class StatementLabel extends StatementInstr<LabelNode> {

	/** map with used labels as keys and display-names as values */
	private Map<Label, String> mapUsedLabels;

	/**
	 * Constructor
	 * @param insn label-instruction
	 * @param mapUsedLabels map from label to display-name
	 */
	protected StatementLabel(LabelNode insn, Map<Label, String> mapUsedLabels) {
		super(insn);
		this.mapUsedLabels = mapUsedLabels;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isVisible() {
		return mapUsedLabels.containsKey(insn.getLabel());
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final Label label = insn.getLabel();
		final String labelName = mapUsedLabels.get(label);
		if (labelName != null) {
			sb.append(labelName).append(':');
		}
	}

}
