package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.InsnNode;

/**
 * Expression loading an array-element.
 */
public class ExpressionArrayLoad extends ExpressionBase<InsnNode>{

	/** array */
	private final ExpressionBase<?> expArray;
	/** index */
	private final ExpressionBase<?> expIndex;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. AALOAD
	 * @param expArray array-expression
	 * @param expIndex index-expression
	 */
	public ExpressionArrayLoad(final InsnNode insn, final ExpressionBase<?> expArray, final ExpressionBase<?> expIndex) {
		super(insn);
		this.expArray = expArray;
		this.expIndex = expIndex;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		expArray.render(sb);
		sb.append('[');
		expIndex.render(sb);
		sb.append(']');
	}

}
