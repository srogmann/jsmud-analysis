package org.rogmann.jsmud.source;

import java.util.Arrays;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Type-instruction ANEWARRAY.
 */
public class ExpressionTypeNewarray extends ExpressionBase<TypeInsnNode>{

	/** length of new array */
	private final ExpressionBase<?> exprCount;

	/** optional array containing initial values */
	private ExpressionBase<?>[] aExprInitial;

	/**
	 * Constructor
	 * @param insn type-instruction, ANEWARRAY
	 * @param exprCount length of new array
	 */
	public ExpressionTypeNewarray(final TypeInsnNode insn, ExpressionBase<?> exprCount) {
		super(insn);
		this.exprCount = exprCount;
	}

	/**
	 * Gets the count-expression.
	 * @return count-expression
	 */
	public ExpressionBase<?> getExprCount() {
		return exprCount;
	}

	/**
	 * Sets an initial value.
	 * 
	 * @param index index in array
	 * @param exprInitial initial value at given index
	 * @param len length of initial array (exprCount must evaluate to this length)
	 */
	public void setInitialValue(final int index, final ExpressionBase<?> exprInitial, final int len) {
		if (aExprInitial == null) {
			aExprInitial = new ExpressionBase<?>[len];
			final ExpressionBase<?> exprDefault = new ExpressionNull();
			Arrays.fill(aExprInitial, exprDefault);
		}
		aExprInitial[index] = exprInitial;	
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		if (opcode != Opcodes.ANEWARRAY) {
			throw new JvmException("Unexpected opcode " + opcode);
		}
		sb.append("new").append(' ');
		final String className = insn.desc.replace('/', '.');
		sb.append(SourceFileWriter.simplifyClassName(className));
		sb.append('[');
		exprCount.render(sb);
		sb.append(']');
		
		if (aExprInitial != null) {
			sb.append('{');
			for (int i = 0; i < aExprInitial.length; i++) {
				if (i > 0) {
					sb.append(", ");
				}
				aExprInitial[i].render(sb);
			}
			sb.append('}');
		}
	}
 
}
