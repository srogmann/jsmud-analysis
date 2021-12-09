package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.rogmann.jsmud.vm.OpcodeDisplay;

/**
 * monitor-statement (synchronized).
 */
public class StatementMonitor extends StatementInstr<InsnNode> {

	/** monitor-object */
	private final ExpressionBase<?> exprObj;

	/**
	 * Constructor
	 * @param iz 
	 * @param exprObj expression
	 */
	public StatementMonitor(final InsnNode iz, final ExpressionBase<?> exprObj) {
		super(iz);
		this.exprObj = exprObj;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("synchronized").append('(');
		if (insn.getOpcode() == Opcodes.MONITORENTER) {
			sb.append("\"enter\"").append(", ");
		}
		else {
			sb.append("\"exit\"").append(", ");
		}
		exprObj.render(sb);
		sb.append(')');
		sb.append(';');
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s, %s);",
				getClass().getSimpleName(),
				OpcodeDisplay.lookup(insn.getOpcode()), exprObj);
	}

}
