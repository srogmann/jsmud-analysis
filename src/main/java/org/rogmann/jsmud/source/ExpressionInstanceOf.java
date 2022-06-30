package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * instanceof-expression.
 */
public class ExpressionInstanceOf extends ExpressionBase<TypeInsnNode> {

	/** argument */
	private final ExpressionBase<?> exprRef;

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/**
	 * Constructor
	 * @param insn INSTANCEOF-instruction
	 * @param exprRef argument
	 * @param sourceNameRenderer source-name renderer
	 */
	public ExpressionInstanceOf(final TypeInsnNode insn, final ExpressionBase<?> exprRef,
			final SourceNameRenderer sourceNameRenderer) {
		super(insn);
		this.exprRef = exprRef;
		this.sourceNameRenderer = sourceNameRenderer;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		exprRef.render(sb);
		sb.append(' ').append("instanceof").append(' ');
		sb.append(sourceNameRenderer.renderType(Type.getObjectType(insn.desc)));
	}

}
