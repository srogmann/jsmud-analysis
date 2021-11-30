package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Type-instruction ANEWARRAY.
 */
public class ExpressionTypeNewarray extends ExpressionBase<TypeInsnNode>{

	/** length of new array */
	private final ExpressionBase<?> exprCount;

	/**
	 * Constructor
	 * @param insn type-instruction, ANEWARRAY
	 * @param exprCount length of new array
	 */
	public ExpressionTypeNewarray(final TypeInsnNode insn, ExpressionBase<?> exprCount) {
		super(insn);
		this.exprCount = exprCount;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.ANEWARRAY) {
			sb.append("new").append(' ');
			final String className = insn.desc.replace('/', '.');
			sb.append(SourceFileWriter.simplifyClassName(className));
			sb.append('[');
			exprCount.render(sb);
			sb.append(']');
		}
		else {
			throw new JvmException("Unexpected opcode " + opcode);
		}
	}

	

}
