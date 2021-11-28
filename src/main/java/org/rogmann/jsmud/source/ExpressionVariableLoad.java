package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Variable-instruction which loads a variable.
 */
public class ExpressionVariableLoad extends ExpressionBase<VarInsnNode> {

	/** method-node */
	private final MethodNode method;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param method method-node
	 */
	public ExpressionVariableLoad(VarInsnNode insn, MethodNode method) {
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
	}

}
