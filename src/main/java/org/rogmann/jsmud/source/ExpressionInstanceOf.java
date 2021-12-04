package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * instanceof-expression.
 */
public class ExpressionInstanceOf extends ExpressionBase<TypeInsnNode> {

	/** argument */
	private final ExpressionBase<?> exprRef;

	/**
	 * Constructor
	 * @param insn INSTANCEOF-instruction
	 * @param exprRef argument
	 */
	public ExpressionInstanceOf(final TypeInsnNode insn, final ExpressionBase<?> exprRef) {
		super(insn);
		this.exprRef = exprRef;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		exprRef.render(sb);
		sb.append(' ').append("instanceof").append(' ');
		sb.append(SourceFileWriter.simplifyClassName(Type.getObjectType(insn.desc)));
	}

}
