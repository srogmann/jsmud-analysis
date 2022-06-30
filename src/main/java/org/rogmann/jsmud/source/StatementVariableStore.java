package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Variable-instruction which stores into variable.
 */
public class StatementVariableStore extends StatementInstr<VarInsnNode>{

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/** method-node */
	private final MethodNode method;
	/** type of expression */
	private final Type typeExpr;
	/** value to be stored */
	private final ExpressionBase<?> exprValue;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ALOAD_1
	 * @param method method-node
	 * @param typeExpr type of expression or <code>null</code> 
	 * @param exprValue value to be stored
	 * @param sourceNameRenderer source-name renderer
	 */
	public StatementVariableStore(VarInsnNode insn, MethodNode method,
			Type typeExpr, final ExpressionBase<?> exprValue, SourceNameRenderer sourceNameRenderer) {
		super(insn);
		this.method = method;
		this.typeExpr = typeExpr;
		this.exprValue = exprValue;
		this.sourceNameRenderer = sourceNameRenderer;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (typeExpr != null) {
			sb.append(sourceNameRenderer.renderType(typeExpr));
			sb.append(' ');
		}
		else {
			sb.append("__unknown_type").append(' ');
		}
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
		sb.append(" = ");
		exprValue.render(sb);
		sb.append(';');
	}

}
