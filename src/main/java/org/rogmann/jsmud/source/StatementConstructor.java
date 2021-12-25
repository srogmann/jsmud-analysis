package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Execution of a constructor (e.g. super or this) without new.
 */
public class StatementConstructor extends StatementInstr<MethodInsnNode>{

	/** class-node */
	private final ClassNode classNode;
	/** object to be initialized */
	protected final ExpressionBase<?> exprObj;
	/** arguments of constructor */
	private final ExpressionBase<?>[] exprArgs;

	/**
	 * Constructor
	 * @param insn INVOKESPECIAL-instruction
	 * @param classNode class containing the method to be called
	 * @param exprObj object to be initialized
	 * @param exprArgs arguments of constructor
	 */
	public StatementConstructor(MethodInsnNode insn, final ClassNode classNode,
			final ExpressionBase<?> exprObj,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.classNode = classNode;
		this.exprObj = exprObj;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final String classRef;
		if (classNode.name.equals(insn.owner)) {
			classRef = "this";
		}
		else {
			classRef = "super";
		}
		sb.append(classRef);
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
