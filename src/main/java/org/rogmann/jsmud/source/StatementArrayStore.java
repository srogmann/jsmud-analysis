package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.InsnNode;

/**
 * Statement storing an array-element.
 */
public class StatementArrayStore extends StatementInstr<InsnNode>{

	/** array */
	private final ExpressionBase<?> exprArray;
	/** index */
	private final ExpressionBase<?> exprIndex;
	/** value */
	private final ExpressionBase<?> exprValue;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. BASTORE
	 * @param exprArray array
	 * @param exprIndex index
	 * @param exprValue value
	 */
	public StatementArrayStore(final InsnNode insn, final ExpressionBase<?> exprArray,
			final ExpressionBase<?> exprIndex, final ExpressionBase<?> exprValue) {
		super(insn);
		this.exprArray = exprArray;
		this.exprIndex = exprIndex;
		this.exprValue = exprValue;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		exprArray.render(sb);
		sb.append('[');
		exprIndex.render(sb);
		sb.append(']');
		sb.append(' ');
		sb.append('=');
		sb.append(' ');
		exprValue.render(sb);
		sb.append(';');
	}

}
