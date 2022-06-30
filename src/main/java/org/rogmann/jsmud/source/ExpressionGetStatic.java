package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Field-instruction which gets a static field.
 */
public class ExpressionGetStatic extends ExpressionBase<FieldInsnNode> {

	/** current class */
	protected final ClassNode classNode;

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param classNode node of current class
	 * @param sourceNameRenderer source-name renderer
	 */
	public ExpressionGetStatic(FieldInsnNode insn, ClassNode classNode,
			SourceNameRenderer sourceNameRenderer) {
		super(insn);
		this.classNode = classNode;
		this.sourceNameRenderer = sourceNameRenderer;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (!insn.owner.equals(classNode.name)) {
			String name = insn.owner.replace('/', '.');
			sb.append(sourceNameRenderer.renderClassName(name));
			sb.append('.');
		}
		sb.append(insn.name);
	}

}
