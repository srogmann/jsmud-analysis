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

	/**
	 * Gets the name of this label.
	 * @return label-name, may be <code>null</code> if this label is unused
	 */
	public String getLabelName() {
		final Label label = insn.getLabel();
		final String labelName = mapUsedLabels.get(label);
		return labelName;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isVisible() {
		return mapUsedLabels.containsKey(insn.getLabel());
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final String labelName = getLabelName();
		if (labelName != null) {
			sb.append(labelName).append(':');
		}
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		String labelName = mapUsedLabels.get(insn.getLabel());
		if (labelName == null) {
			labelName = insn.getLabel().toString();
		}
		return String.format("%s(%s:);",
				getClass().getSimpleName(), labelName);
	}

}
