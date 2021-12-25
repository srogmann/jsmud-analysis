package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Instruction which changes a local variable (e.g. "++").
 */
public class StatementVariableIinc extends StatementInstr<IincInsnNode>{

	/** method */
	private final MethodNode method;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. IINC 1,1
	 * @param method method-node
	 */
	public StatementVariableIinc(IincInsnNode insn, final MethodNode method) {
		super(insn);
		this.method = method;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		String varName = null;
		if (method != null && method.localVariables != null) {
			LocalVariableNode varNode = null;
			for (LocalVariableNode varNodeLoop : method.localVariables) {
				if (varNodeLoop.index == insn.var) {
					varNode = varNodeLoop;
					break;
				}
			}
			if (varNode != null) {
				varName = varNode.name;
			}
		}
		if (varName == null) {
			varName = "__local" + insn.var;
		}

		sb.append(varName);
		final int incr = insn.incr;
		if (incr == -1) {
			sb.append("--");
		}
		else if (incr == +1) {
			sb.append("++");
		}
		else {
			sb.append(" = ");
			sb.append(varName);
			sb.append(' ');
			if (incr >= 0) {
				sb.append('+');
			}
			sb.append(incr);
		}
		sb.append(';');
	}

}
