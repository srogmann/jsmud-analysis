package org.rogmann.jsmud.source;

import java.util.Arrays;

import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.rogmann.jsmud.vm.AtypeEnum;

/**
 * int-operand newarray-instruction.
 */
public class ExpressionInstrIntNewarray extends ExpressionBase<IntInsnNode>{

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/** length of new array */
	private ExpressionBase<?> exprCount;

	/** optional array containing initial values */
	private ExpressionBase<?>[] aExprInitial;

	/**
	 * Constructor
	 * @param insn type-instruction, NEWARRAY 
	 * @param exprCount length of new array
	 * @param sourceNameRenderer source-name renderer
	 */
	public ExpressionInstrIntNewarray(IntInsnNode insn, final ExpressionBase<?> exprCount,
			SourceNameRenderer sourceNameRenderer) {
		super(insn);
		this.exprCount = exprCount;
		this.sourceNameRenderer = sourceNameRenderer;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("new").append(' ');
		final String displayName = computeDisplayName();
		sb.append(sourceNameRenderer.renderClassName(displayName));
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

	protected String computeDisplayName() {
		final Class<?> classPrimitive = AtypeEnum.lookupAtypeClass(insn.operand);
		final String displayName = classPrimitive.getSimpleName();
		return displayName;
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
			final ExpressionBase<?> exprDefault;
			final Class<?> classPrimitive = AtypeEnum.lookupAtypeClass(insn.operand);
			if (int.class.equals(classPrimitive)) {
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Integer.valueOf(0)));
			}
			else if (long.class.equals(classPrimitive)) {
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Long.valueOf(0)));
			}
			else if (String.class.equals(classPrimitive)) {
				exprDefault = new ExpressionNull();
			}
			else if (float.class.equals(classPrimitive)) {
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Float.valueOf(0)));
			}
			else if (double.class.equals(classPrimitive)) {
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Double.valueOf(0)));
			}
			else if (boolean.class.equals(classPrimitive)) {
				// boolean is an int on stack, display only.
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Boolean.FALSE));
			}
			else if (byte.class.equals(classPrimitive)) {
				// byte is an int on stack, display only.
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Byte.valueOf((byte) 0)));
			}
			else if (char.class.equals(classPrimitive)) {
				// char is an int on stack, display only.
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Character.valueOf((char) 0)));
			}
			else if (short.class.equals(classPrimitive)) {
				// short is an int on stack, display only.
				exprDefault = new ExpressionInstrConstant(new LdcInsnNode(Short.valueOf((short) 0)));
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected primitive type %s", classPrimitive));
			}
			Arrays.fill(aExprInitial, exprDefault);
		}
		aExprInitial[index] = exprInitial;	
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(new %s[%s]);",
				getClass().getSimpleName(), computeDisplayName(), exprCount);
	}

}
