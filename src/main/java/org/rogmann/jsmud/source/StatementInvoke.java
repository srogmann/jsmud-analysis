package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Method-instruction which executes a void method as a statement.
 */
public class StatementInvoke extends StatementInstr<MethodInsnNode> {

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/** expression of object-instance */
	private final ExpressionBase<?> exprObj;
	/** arguments of constructor */
	private final ExpressionBase<?>[] exprArgs;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param sourceNameRenderer source-name renderer
	 * @param exprObj expression of object-instance (<code>null</code> in case of INVOKESTATIC)
	 * @param exprArgs arguments of method
	 */
	public StatementInvoke(final MethodInsnNode insn, final SourceNameRenderer sourceNameRenderer,
			final ExpressionBase<?> exprObj,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.sourceNameRenderer = sourceNameRenderer;
		this.exprObj = exprObj;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
			final String className = insn.owner.replace('/', '.');
			sb.append(sourceNameRenderer.renderClassName(className));
		}
		else {
			exprObj.render(sb);
		}
		sb.append('.');
		sb.append(insn.name);
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
		sb.append(';');
	}

}
