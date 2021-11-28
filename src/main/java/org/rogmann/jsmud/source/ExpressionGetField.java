package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Field-instruction which gets a field of an object-instance.
 */
public class ExpressionGetField extends ExpressionBase<FieldInsnNode> {

	/** current class */
	protected final ClassNode classNode;

	/** expression of object-instance */
	private ExpressionBase<?> exprObj;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param classNode node of current class
	 * @param exprObj expression of object-instance
	 */
	public ExpressionGetField(FieldInsnNode insn, ClassNode classNode, final ExpressionBase<?> exprObj) {
		super(insn);
		this.classNode = classNode;
		this.exprObj = exprObj;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		exprObj.render(sb);
		sb.append('.');
		sb.append(insn.name);
	}

}
