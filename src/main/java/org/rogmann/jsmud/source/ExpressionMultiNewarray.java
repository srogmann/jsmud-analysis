package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

/**
 * Type-instruction ANEWARRAY.
 */
public class ExpressionMultiNewarray extends ExpressionBase<MultiANewArrayInsnNode>{

	/** dimensions */
	private final ExpressionBase<?>[] aExprDims;

	/**
	 * Constructor
	 * @param manai MULTIANEWARRAY-instruction
	 * @param aExprDims dimensions of new array
	 */
	public ExpressionMultiNewarray(final MultiANewArrayInsnNode manai, ExpressionBase<?>[] aExprDims) {
		super(manai);
		this.aExprDims = aExprDims;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new").append(' ');
		final Type aType = Type.getObjectType(insn.desc);
		final Type elType = aType.getElementType();
		sb.append(SourceFileWriter.simplifyClassName(elType));
		for (ExpressionBase<?> exprDim : aExprDims) {
			sb.append('[');
			exprDim.render(sb);
			sb.append(']');
		}
	}

	

}
