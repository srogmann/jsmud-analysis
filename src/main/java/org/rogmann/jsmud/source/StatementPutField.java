package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Field-instruction which stores into field.
 */
public class StatementPutField extends StatementInstr<FieldInsnNode>{

	/** object-instance */
	private ExpressionBase<?> expObject;
	/** value to be stored */
	private ExpressionBase<?> exprValue;

	/**
	 * Constructor
	 * @param insn field-instruction, e.g. PUTFIELD
	 * @param exprValue value to be stored
	 */
	public StatementPutField(FieldInsnNode insn,
			final ExpressionBase<?> expObject,
			final ExpressionBase<?> exprValue) {
		super(insn);
		this.expObject = expObject;
		this.exprValue = exprValue;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		expObject.render(sb);
		sb.append('.');
		sb.append(insn.name);
		sb.append(" = ");
		exprValue.render(sb);
		sb.append(';');
	}

}
