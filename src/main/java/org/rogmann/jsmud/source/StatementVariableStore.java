package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.vm.Utils;

/**
 * Variable-instruction which stores into variable.
 */
public class StatementVariableStore extends StatementInstr<VarInsnNode>{

	/** method-node */
	private final MethodNode method;
	/** type of expression */
	private final Type typeExpr;
	/** value to be stored */
	private final ExpressionBase<?> exprValue;

	/** this-class */
	private final ClassNode classNode;

	/**
	 * Constructor
	 * @param insn variable-instruction, e.g. ALOAD_1
	 * @param classNode class of this-class
	 * @param method method-node
	 * @param typeExpr type of expression or <code>null</code> 
	 * @param exprValue value to be stored
	 */
	public StatementVariableStore(VarInsnNode insn, ClassNode classNode, MethodNode method,
			Type typeExpr, final ExpressionBase<?> exprValue) {
		super(insn);
		this.classNode = classNode;
		this.method = method;
		this.typeExpr = typeExpr;
		this.exprValue = exprValue;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (typeExpr != null) {
			String packageThis = Utils.getPackage(classNode.name.replace('/', '.'));
			sb.append(SourceFileWriter.simplifyClassName(typeExpr, packageThis));
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
