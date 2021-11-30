package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LdcInsnNode;
import org.rogmann.jsmud.vm.Utils;

/**
 * Expression which loads a constant value.
 */
public class ExpressionInstrConstant extends ExpressionBase<LdcInsnNode> {
	/**
	 * Constructor
	 * @param insn instruction
	 */
	public ExpressionInstrConstant(final LdcInsnNode insn) {
		super(insn);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final Object cst = insn.cst;
		if (cst instanceof Integer) {
			sb.append(((Integer) cst).intValue());
		}
		else if (cst instanceof String) {
			final String s = (String) cst;
			Utils.appendStringValue(sb, s);
		}
		else if (cst instanceof Long) {
			sb.append(((Long) cst).longValue());
		}
		else if (cst instanceof Float) {
			sb.append(((Float) cst).floatValue());
		}
		else if (cst instanceof Double) {
			sb.append(((Double) cst).doubleValue());
		}
		else if (cst instanceof Type) {
			final Type type = (Type) cst;
			sb.append(SourceFileWriter.simplifyClassName(type));
		}
		else {
			throw new RuntimeException(String.format("Unexpected type (%s): %s",
					cst.getClass(), cst));
		}
	}

}
