package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.rogmann.jsmud.vm.Utils;

/**
 * Execution of a constructor with new.
 */
public class ExpressionConstructor extends ExpressionBase<MethodInsnNode>{

	/** object to be initialized */
	private final ExpressionTypeInstr exprNew;
	/** arguments of constructor */
	private final ExpressionBase<?>[] exprArgs;

	/** owner-class */
	private final ClassNode classNode;

	/**
	 * Constructor
	 * @param insn INVOKESPECIAL-instruction
	 * @param classNode class-node of owner-class
	 * @param exprNew object to be initialized
	 * @param exprArgs arguments of constructor
	 */
	public ExpressionConstructor(final MethodInsnNode insn,
			final ClassNode classNode,
			final ExpressionTypeInstr exprNew,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.classNode = classNode;
		this.exprNew = exprNew;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new ");
		final String className = computeClassName();
		sb.append(className);
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

	private String computeClassName() {
		final String newTypeInternal = exprNew.insn.desc;
		final String name = newTypeInternal.replace('/', '.');
		final String packageThis = Utils.getPackage(classNode.name.replace('/', '.'));
		final String className = SourceFileWriter.simplifyClassName(name, packageThis);
		return className;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(new %s(...));",
				getClass().getSimpleName(), computeClassName());
	}

}
