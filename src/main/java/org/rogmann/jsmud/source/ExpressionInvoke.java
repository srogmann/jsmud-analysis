package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Method-instruction which executes a method returning a result.
 */
public class ExpressionInvoke extends ExpressionBase<MethodInsnNode> {

	/** current class */
	protected final ClassNode classNode;

	/** expression of object-instance */
	private final ExpressionBase<?> exprObj;
	/** arguments of constructor */
	private final ExpressionBase<?>[] exprArgs;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param classNode node of current class
	 * @param exprObj expression of object-instance (<code>null</code> in case of INVOKESTATIC)
	 * @param exprArgs arguments of method
	 */
	public ExpressionInvoke(MethodInsnNode insn, ClassNode classNode, final ExpressionBase<?> exprObj,
			final ExpressionBase<?>... exprArgs) {
		super(insn);
		this.classNode = classNode;
		this.exprObj = exprObj;
		this.exprArgs = exprArgs;
	}

	/** {@inheritDoc} */
	@Override
	public Type getType() {
		return Type.getReturnType(insn.desc);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
			final String className = insn.owner.replace('/', '.');
			sb.append(SourceFileWriter.simplifyClassName(className));
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
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s%s);",
				getClass().getSimpleName(),
				insn.name, insn.desc);
	}

}
