package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Field-instruction which gets a static field.
 */
public class ExpressionGetStatic extends ExpressionBase<FieldInsnNode> {

	/** current class */
	protected final ClassNode classNode;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param classNode node of current class
	 */
	public ExpressionGetStatic(FieldInsnNode insn, ClassNode classNode) {
		super(insn);
		this.classNode = classNode;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (!insn.owner.equals(classNode.name)) {
			String name = insn.owner.replace('/', '.');
			sb.append(SourceFileWriter.simplifyClassName(name));
			sb.append('.');
		}
		sb.append(insn.name);
	}

}
