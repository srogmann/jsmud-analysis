package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Execution of a constructor with new.
 */
public class ExpressionConstructor extends ExpressionBase<MethodInsnNode>{

	/** object to be initialized */
	private final ExpressionTypeInstr exprNew;
	/** arguments of constructor */
	private ExpressionBase<?>[] exprArgs;

	/**
	 * Constructor
	 * @param insn INVOKESPECIAL-instruction
	 * @param exprNew object to be initialized
	 * @param exprArgs arguments of constructor
	 */
	public ExpressionConstructor(MethodInsnNode insn,
			final ExpressionTypeInstr exprNew,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.exprNew = exprNew;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new ");
		final String newTypeInternal = exprNew.insn.desc;
		String name = newTypeInternal.replace('/', '.');
		sb.append(SourceFileWriter.simplifyClassName(name));
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

}
