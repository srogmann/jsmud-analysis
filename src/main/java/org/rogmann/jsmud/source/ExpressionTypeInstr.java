package org.rogmann.jsmud.source;

import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Type-instruction of an expression on stack.
 */
public class ExpressionTypeInstr extends ExpressionBase<TypeInsnNode>{

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. NEW
	 */
	public ExpressionTypeInstr(TypeInsnNode insn) {
		super(insn);
	}

}
