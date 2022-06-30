package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Execution of a constructor with new.
 */
public class ExpressionConstructor extends ExpressionBase<MethodInsnNode>{

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/** object to be initialized */
	private final ExpressionTypeInstr exprNew;
	/** arguments of constructor */
	private final ExpressionBase<?>[] exprArgs;


	/**
	 * Constructor
	 * @param insn INVOKESPECIAL-instruction
	 * @param sourceNameRenderer source-name renderer
	 * @param exprNew object to be initialized
	 * @param exprArgs arguments of constructor
	 */
	public ExpressionConstructor(final MethodInsnNode insn,
			final SourceNameRenderer sourceNameRenderer,
			final ExpressionTypeInstr exprNew,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.sourceNameRenderer = sourceNameRenderer;
		this.exprNew = exprNew;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new ");
		final String newTypeInternal = exprNew.insn.desc;
		final String name = newTypeInternal.replace('/', '.');
		sb.append(sourceNameRenderer.renderClassName(name));
		sb.append('(');
		boolean isFirst = true;
		for (final ExpressionBase<?> arg : exprArgs) {
			if (isFirst) {
				isFirst = false;
			}
			else {
				sb.append(", ");
			}
			arg.render(sb);
		}
		sb.append(')');
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(new %s(...));",
				getClass().getSimpleName(), exprNew.insn.desc.replace('/', '.'));
	}

}
