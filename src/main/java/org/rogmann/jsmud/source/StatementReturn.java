package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.InsnNode;

/**
 * return-Statement.
 */
public class StatementReturn extends StatementInstrZeroOp {

	/** returned object */
	private ExpressionBase<?> expr;

	/**
	 * Constructor
	 * @param insn zero-op-instruction, e.g. RETURN
	 */
	public StatementReturn(final InsnNode insn) {
		this(insn, null);
	}

	/**
	 * Constructor
	 * @param insn zero-op-instruction, e.g. ARETURN
	 * @param expr optional expression if an object is returned
	 */
	public StatementReturn(final InsnNode insn, final ExpressionBase<?> expr) {
		super(insn);
		this.expr = expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("return");
		if (expr != null) {
			sb.append(' ');
			expr.render(sb);
		}
		sb.append(';');
	}

}
