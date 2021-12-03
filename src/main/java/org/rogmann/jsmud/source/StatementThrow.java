package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.InsnNode;

/**
 * throw-statement.
 */
public class StatementThrow extends StatementInstr<InsnNode> {

	/** expression */
	private final ExpressionBase<?> expr;

	/**
	 * Constructor
	 * @param expr expression
	 */
	public StatementThrow(final InsnNode insn, final ExpressionBase<?> expr) {
		super(insn);
		this.expr = expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("throw").append(' ');
		expr.render(sb);
		sb.append(';');
	}

}
