package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Variable-instruction which loads a variable.
 */
public class ExpressionVariableLoad extends ExpressionBase<VarInsnNode> {

	/** method-node */
	private final MethodNode method;
	private Type typeVar;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ASTORE_1
	 * @param typeVar type of variable (if known)
	 * @param method method-node
	 */
	public ExpressionVariableLoad(VarInsnNode insn, Type typeVar, MethodNode method) {
		super(insn);
		this.typeVar = typeVar;
		this.method = method;
	}

	/** {@inheritDoc} */
	@Override
	public Type getType() {
		return typeVar;
	}
	
	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		String varName = computeVarName();

		sb.append(varName);
	}

	/**
	 * Computes the name of the variable.
	 * @return name
	 */
	private String computeVarName() {
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
		return varName;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s);",
				getClass().getSimpleName(), computeVarName());
	}

}
