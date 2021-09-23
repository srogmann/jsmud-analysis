package org.rogmann.jsmud.visitors;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Writes pseudo-code of a class into a file.
 */
public class SourceFileWriter {
	
	/** line-break in source-file */
	private final String crlf;

	/** number of lines */
	private int lineNum = 1;

	/** current line-number in class */
	private int lineNumClass;

	/** current indentation-level */
	private int level = 0;

	/** map from line-number in class to line-number in this pseudo-code */
	private final Map<Integer, Integer> mapLineClassToPseudo = new HashMap<>();

	/** internal-names of processed classes */
	private final Set<String> setInnerClassesProcessed = new HashSet<>();

	/** map from method-key to first line-number of body */
	private final Map<String, Integer> mapMethodFirstLine = new HashMap<>();

	/** map from method-key to map from instruction to line-number */
	private final Map<String, Map<Integer, Integer>> mapMethodInstr = new HashMap<>();

	/**
	 * Constructor, writes the source-file.
	 * @param bw output-writer
	 * @param crlf line-break
	 * @param node class-node
	 * @param innerClassesProvider function which returns a class-node corresponding to an internal-name
	 * @throws IOException in case of an IO-error
	 */
	public SourceFileWriter(final BufferedWriter bw, final String crlf, final ClassNode node,
			final Function<String, ClassNode> innerClassesProvider) throws IOException {
		this.crlf = crlf;
		final StringBuilder sb = new StringBuilder(100);
		final int lastSlash = node.name.lastIndexOf('/');
		if (lastSlash > 0) {
			final String packageName = node.name.substring(0, lastSlash).replace('/', '.');
			sb.append("package ").append(packageName).append(';');
			writeLine(bw, sb);
			writeLine(bw, "");
		}
		writeClass(bw, node, innerClassesProvider);
	}

	private void writeClass(final BufferedWriter bw, final ClassNode node,
			final Function<String, ClassNode> innerClassesProvider) throws IOException {
		if (!setInnerClassesProcessed.add(node.name)) {
			// This class has been processed already.
			return;
		}
		final StringBuilder sb = new StringBuilder(100);
		final int lastSlash = node.name.lastIndexOf('/');
		final String classSimpleName;
		if (lastSlash > 0) {
			classSimpleName = node.name.substring(lastSlash + 1);
		}
		else {
			classSimpleName = node.name;
		}

		writeLine(bw, "/**");
		sb.append(" * Pseudocode of class ").append(node.name.replace('/', '.')).append('.'); writeLine(bw, sb);
		writeLine(bw, " *");
		sb.append(" * <p>Generator: ").append(ClassRegistry.VERSION).append("</p>"); writeLine(bw, sb);
		writeLine(bw, " */");
		appendAccessClass(sb, node.access);
		sb.append(classSimpleName);
		if (node.superName != null && !"java/lang/Object".equals(node.superName)) {
			sb.append(" extends ").append(node.superName.replace('/', '.'));
		}
		if (node.interfaces.size() > 0) {
			sb.append(" implements ");
			for (int i = 0; i < node.interfaces.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(node.interfaces.get(i).replace('/', '.'));
			}
		}
		sb.append(" {"); writeLine(bw, sb);
		writeLine(bw, "");
		level++;

		for (final FieldNode fieldNode : node.fields) {
			appendAccessField(sb, fieldNode.access);
			final Type type = Type.getType(fieldNode.desc);
			sb.append(type.getClassName());
			sb.append(' ').append(fieldNode.name);
			sb.append(';'); writeLine(bw, sb);
		}

		for (final MethodNode methodNode : node.methods) {
			writeLine(bw, "");
			appendAccessMethod(sb, methodNode.access);
			final Type type = Type.getMethodType(methodNode.desc);
			if ("<init>".equals(methodNode.name)) {
				sb.append(classSimpleName);
			}
			else if ("<clinit>".equals(methodNode.name)) {
				// static initializer
			}
			else {
				sb.append(type.getReturnType().getClassName());
				sb.append(' ').append(methodNode.name);
			}
			sb.append('(');
			final Type[] argTypes = type.getArgumentTypes();
			for (int i = 0; i < argTypes.length; i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(argTypes[i].getClassName());
				sb.append(' '); sb.append("arg").append(i + 1);
			}
			sb.append(')');
			final List<String> exceptions = methodNode.exceptions;
			for (int i = 0; i < exceptions.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(exceptions.get(i));
			}
			sb.append(" {"); writeLine(bw, sb);
			level++;
			writeMethodBody(bw, sb, node, methodNode);
			level--;
			writeLine(bw, "}");
		}

		for (final InnerClassNode innerClassNode : node.innerClasses) {
			writeLine(bw, "");
			final ClassNode innerClass = innerClassesProvider.apply(innerClassNode.name);
			if (innerClass == null) {
				throw new JvmException(String.format("No class-node of inner-class (%s) of (%s)",
						innerClassNode.name, node.name));
			}
			writeClass(bw, innerClass, innerClassesProvider);
		}

		level--;
		writeLine(bw, "}");
	}

	private void writeMethodBody(final BufferedWriter bw, final StringBuilder sb, ClassNode classNode, MethodNode methodNode) throws IOException {
		final String methodKey = buildMethodKey(classNode, methodNode);
		mapMethodFirstLine.put(methodKey, Integer.valueOf(lineNum));
		final Map<Integer, Integer> mapInstrLine = new HashMap<>();
		mapMethodInstr.put(methodKey, mapInstrLine);

		final InsnList instructions = methodNode.instructions;
		for (int i = 0; i < instructions.size(); i++) {
			final AbstractInsnNode instr = instructions.get(i);
			final int opcode = instr.getOpcode();
			final int type = instr.getType();
			if (type == AbstractInsnNode.LABEL) {
				final LabelNode ln = (LabelNode) instr;
				level--;
				sb.append(ln.getLabel().toString()).append(':'); writeLine(bw, sb);
				level++;
			}
			else if (type == AbstractInsnNode.LINE) {
				final LineNumberNode lnn = (LineNumberNode) instr;
				//mapLineClassToPseudo.put(Integer.valueOf(lnn.line), Integer.valueOf(lineNum));
				lineNumClass = lnn.line;
			}
			else if (opcode >= 0) {
				final Integer iLineNum = Integer.valueOf(lineNum);
				final String sInstruction = InstructionVisitor.displayInstruction(instr, methodNode);
				writeLine(bw, sInstruction);
				mapLineClassToPseudo.putIfAbsent(Integer.valueOf(lineNumClass), iLineNum);
				mapInstrLine.put(Integer.valueOf(i), iLineNum);
			}
		}
	}

	/**
	 * Appends access-modifiers of a class and "class / interface".
	 * @param sb string-builder to be used
	 * @param access access-modifiers
	 */
	static void appendAccessClass(final StringBuilder sb, final int access) {
		if ((access & Opcodes.ACC_PUBLIC) != 0) {
			sb.append("public ");
		}
		if ((access & Opcodes.ACC_PROTECTED) != 0) {
			sb.append("protected ");
		}
		if ((access & Opcodes.ACC_PRIVATE) != 0) {
			sb.append("private ");
		}
		if ((access & Opcodes.ACC_STATIC) != 0) {
			sb.append("static ");
		}
		if ((access & Opcodes.ACC_FINAL) != 0) {
			sb.append("final  ");
		}
		if ((access & Opcodes.ACC_ABSTRACT) != 0) {
			sb.append("abstract  ");
		}
		if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
			sb.append("synthetic ");
		}
		if ((access & Opcodes.ACC_INTERFACE) != 0) {
			sb.append("interface ");
		}
		else {
			sb.append("class ");
		}
	}

	/**
	 * Appends access-modifiers of a field.
	 * @param sb string-builder to be used
	 * @param access access-modifiers
	 */
	static void appendAccessField(final StringBuilder sb, final int access) {
		if ((access & Opcodes.ACC_PUBLIC) != 0) {
			sb.append("public ");
		}
		if ((access & Opcodes.ACC_PROTECTED) != 0) {
			sb.append("protected ");
		}
		if ((access & Opcodes.ACC_PRIVATE) != 0) {
			sb.append("private ");
		}
		if ((access & Opcodes.ACC_STATIC) != 0) {
			sb.append("static ");
		}
		if ((access & Opcodes.ACC_FINAL) != 0) {
			sb.append("final ");
		}
		if ((access & Opcodes.ACC_VOLATILE) != 0) {
			sb.append("volatile ");
		}
		if ((access & Opcodes.ACC_TRANSIENT) != 0) {
			sb.append("transient ");
		}
		if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
			sb.append("synthetic ");
		}
	}

	/**
	 * Appends access-modifiers of a field.
	 * @param sb string-builder to be used
	 * @param access access-modifiers
	 */
	static void appendAccessMethod(final StringBuilder sb, final int access) {
		if ((access & Opcodes.ACC_PUBLIC) != 0) {
			sb.append("public ");
		}
		if ((access & Opcodes.ACC_PROTECTED) != 0) {
			sb.append("protected ");
		}
		if ((access & Opcodes.ACC_PRIVATE) != 0) {
			sb.append("private ");
		}
		if ((access & Opcodes.ACC_STATIC) != 0) {
			sb.append("static ");
		}
		if ((access & Opcodes.ACC_FINAL) != 0) {
			sb.append("final ");
		}
		if ((access & Opcodes.ACC_ABSTRACT) != 0) {
			sb.append("abstract ");
		}
		if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
			sb.append("synchronized ");
		}
		if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
			sb.append("synthetic ");
		}
	}

	/**
	 * Builds a key consisting of internal class-name and method-signature.
	 * @param classNode class-node
	 * @param methodNode method-node
	 * @return internal lookup-key
	 */
	private static String buildMethodKey(ClassNode classNode, MethodNode methodNode) {
		final String methodKey = classNode.name + '#' + methodNode.name + methodNode.desc;
		return methodKey;
	}

	/**
	 * Gets a map from instruction-index to line-number in the generated source-file
	 * @param clazz class
	 * @param methodNode method of class
	 * @return map from instruction-index to line-number or <code>null</code>
	 */
	public Map<Integer, Integer> getMethodMapInstrLine(final Class<?> clazz, final MethodNode methodNode) {
		final String methodKey = Type.getInternalName(clazz) + '#' + methodNode.name + methodNode.desc;
		return mapMethodInstr.get(methodKey);
	}

	public List<LineCodeIndex> computeMethodLines(ClassNode classNode, MethodNode methodNode) {
		final List<LineCodeIndex> lines = new ArrayList<>();
		final String methodKey = buildMethodKey(classNode, methodNode);
		final Integer firstLine = mapMethodFirstLine.get(methodKey);
		if (firstLine == null) {
			throw new JvmException(String.format("No first line of method (%s)", methodKey));
		}

		int lineNo = firstLine.intValue();
		lines.add(new LineCodeIndex(0, lineNo));
		
		final InsnList instructions = methodNode.instructions;
		for (int i = 0; i < instructions.size(); i++) {
			final AbstractInsnNode instr = instructions.get(i);
			final int opcode = instr.getOpcode();
			final int type = instr.getType();
			if (type == AbstractInsnNode.LABEL) {
				lineNo++;
			}
			else if (opcode >= 0) {
				lines.add(new LineCodeIndex(i, lineNo));
				lineNo++;
			}
		}

		return lines;
	}

	/**
	 * Write a line and adds a line-break.
	 * @param bw writer
	 * @param line content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	private void writeLine(BufferedWriter bw, String line) throws IOException {
		for (int i = 0; i < level; i++) {
			bw.write("    ");
		}
		bw.write(line);
		bw.write(crlf);
		lineNum++;
	}

	/**
	 * Write a line and adds a line-break.
	 * The string-builder's length will be set to zero.
	 * @param bw writer
	 * @param sb content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	private void writeLine(BufferedWriter bw, StringBuilder sb) throws IOException {
		sb.append(crlf);
		for (int i = 0; i < level; i++) {
			bw.write("    ");
		}
		bw.write(sb.toString());
		sb.setLength(0);
		lineNum++;
	}

}
