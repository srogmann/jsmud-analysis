package org.rogmann.jsmud.source;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Method-instruction which executes a method returning a result.
 */
public class ExpressionInvokeDynamic extends ExpressionBase<InvokeDynamicInsnNode> {

	/** current class */
	protected final ClassNode classNode;

	/** arguments */
	private final ExpressionBase<?>[] exprArgs;

	/** temporarily variables */
	private final String[] tempVars;

	/**
	 * Constructor
	 * @param insn INVOKEDYNAMIC-instruction
	 * @param classNode node of current class
	 * @param exprArgs arguments of method
	 * @param tempVars names of temporary variables
	 */
	public ExpressionInvokeDynamic(InvokeDynamicInsnNode insn, ClassNode classNode,
			final ExpressionBase<?>[] exprArgs, final String[] tempVars) {
		super(insn);
		this.classNode = classNode;
		this.exprArgs = exprArgs;
		this.tempVars = tempVars;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final Handle bsmHandle = insn.bsm;
		final Handle handle;
		if ("java/lang/invoke/LambdaMetafactory".equals(bsmHandle.getOwner())
				&& "metafactory".equals(bsmHandle.getName())
				&& "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;".equals(bsmHandle.getDesc())) {
			// lambda-expression, insn.bsmArgs = {Type, Handle, Type}.
			handle = (Handle) insn.bsmArgs[1];
		}
		else {
			throw new JvmException(String.format("Unexpected invokedynamic-bootstrap-handle: %s.%s%s",
					bsmHandle.getOwner(), bsmHandle.getName(), bsmHandle.getDesc()));
		}
		sb.append('(');
		boolean isFirst = true;
		for (final String tempVar : tempVars) {
			if (isFirst) {
				isFirst = false;
			}
			else {
				sb.append(", ");
			}
			sb.append(tempVar);
		}
		sb.append(')');
		sb.append(" -> ");
		sb.append(SourceFileWriter.simplifyClassName(handle.getOwner().replace('/', '.')));
		sb.append('.');
		sb.append(handle.getName());
		isFirst = true;
		sb.append('(');
		for (final ExpressionBase<?> arg : exprArgs) {
			if (isFirst) {
				isFirst = false;
			}
			else {
				sb.append(", ");
			}
			arg.render(sb);
		}
		for (final String tempVar : tempVars) {
			if (isFirst) {
				isFirst = false;
			}
			else {
				sb.append(", ");
			}
			sb.append(tempVar);
		}
		sb.append(')');
	}

}
