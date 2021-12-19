package org.rogmann.jsmud.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.OpcodeDisplay;
import org.rogmann.jsmud.vm.Utils;

/**
 * Type-instruction of an expression.
 */
public class ExpressionTypeInstr extends ExpressionBase<TypeInsnNode>{

	/** class-node */
	private final ClassNode classNode;

	/** <code>true</code> if a pseudo NEW-instruction may be rendered */
	private final boolean isNewRenderingAllowed;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. NEW
	 * @param classNode class-node
	 * @param isNewRenderingAllowed <code>true</code> if a pseudo NEW-instruction may be rendered (necessary at difficult constructor-arguments) 
	 */
	public ExpressionTypeInstr(final TypeInsnNode insn, final ClassNode classNode,
			final boolean isNewRenderingAllowed) {
		super(insn);
		this.classNode = classNode;
		this.isNewRenderingAllowed = isNewRenderingAllowed;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final int opcode = insn.getOpcode();
		if (opcode == Opcodes.NEW) {
			if (!isNewRenderingAllowed) {
				throw new JvmException("The NEW-instruction should be rendered at INVOKESPECIAL <init>");
			}
			sb.append("NEW").append('(');
			final Type type = Type.getObjectType(insn.desc);
			String packageThis = Utils.getPackage(classNode.name.replace('/', '.'));
			final String className = SourceFileWriter.simplifyClassName(type, packageThis);
			sb.append(className).append(')');
		}
		else {
			throw new JvmException("Unexpected opcode " + opcode);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(%s %s);",
				getClass().getSimpleName(),
				OpcodeDisplay.lookup(insn.getOpcode()), insn.desc);
	}

}
