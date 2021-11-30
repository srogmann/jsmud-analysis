package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Field-instruction which stores into a static field.
 */
public class StatementPutStatic extends StatementInstr<FieldInsnNode>{

	/** current class */
	protected final ClassNode classNode;

	/** value to be stored */
	private ExpressionBase<?> exprValue;

	/**
	 * Constructor
	 * @param insn field-instruction, PUTSTATIC
	 * @param classNode class-node of current class
	 * @param exprValue value to be stored
	 */
	public StatementPutStatic(final FieldInsnNode insn, final ClassNode classNode,
			final ExpressionBase<?> exprValue) {
		super(insn);
		this.classNode = classNode;
		this.exprValue = exprValue;
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
		sb.append(" = ");
		exprValue.render(sb);
		sb.append(';');
	}

}
