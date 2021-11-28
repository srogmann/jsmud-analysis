package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Duplication of an expression.
 * <p><strong>CAVEAT: The value should be duplicated, not the expression.</strong></p>
 */
public class ExpressionDup<A extends AbstractInsnNode> extends ExpressionBase<A> {

	/** duplicated expression */
	private final ExpressionBase<A> expr;

	/**
	 * Constructor
	 * @param expr expression to be duplicated
	 */
	public ExpressionDup(final ExpressionBase<A> expr) {
		super(expr.insn);
		this.expr = expr;
	}

	/**
	 * Gets the expression whose value was duplicated.
	 * @return expression
	 */
	public ExpressionBase<A> getExpression() {
		return expr;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		throw new JvmException("Can't render a duplicate of an expression.");
	}

}
