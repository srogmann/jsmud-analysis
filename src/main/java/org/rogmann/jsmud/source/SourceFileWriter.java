package org.rogmann.jsmud.source;

import java.io.IOException;
import java.io.Writer;
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
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.replydata.LineTable;
import org.rogmann.jsmud.visitors.InstructionVisitor;
import org.rogmann.jsmud.vm.ClassRegistry;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.Utils;

/**
 * Writes pseudo-code of a class into a file.
 * 
 * <p>Lines are prefixed with "//"-comments. A 2021-06-eclipse started looping without these comments.</p>
 */
public class SourceFileWriter {

	/** Java-package "java.lang." */
	private static final String PKG_JAVA_LANG = "java.lang";

	/** extension of the generated file (e.g. "java" or "asm") */
	private final String extension;

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

	/** outer list of source-blocks */
	private final SourceBlockList sourceOuter;

	/**
	 * Constructor, writes the source-file.
	 * @param extension extension, e.g. "asm"
	 * @param node class-node
	 * @param innerClassesProvider function which returns a class-node corresponding to an internal-name
	 * @throws IOException in case of an IO-error
	 */
	public SourceFileWriter(final String extension, final ClassNode node,
			final Function<String, ClassNode> innerClassesProvider) throws IOException {
		this.extension = extension;
		sourceOuter = new SourceBlockList(0, "outer-class " + node.name.replace('/', '.'));
		
		final SourceLines header = new SourceLines(0);
		sourceOuter.setHeader(header);
		final StringBuilder sb = new StringBuilder(100);
		final int lastSlash = node.name.lastIndexOf('/');
		if (lastSlash > 0) {
			final String packageName = node.name.substring(0, lastSlash).replace('/', '.');
			sb.append("package ").append(packageName).append(';');
			writeLine(header, sb);
			writeLine(header, "");
		}
		
		final SourceBlockList sourceBlockList = new SourceBlockList(0,
				"class " + node.name.replaceFirst(".*[/]", ""));
		sourceOuter.getList().add(sourceBlockList);
		writeClass(sourceBlockList, node, innerClassesProvider);
	}

	/**
	 * Gets the generated blocks of source.
	 * @return source-block-list
	 */
	public SourceBlockList getSourceBlockList() {
		return sourceOuter;
	}

	protected void writeClass(final SourceBlockList blocks, final ClassNode node,
			final Function<String, ClassNode> innerClassesProvider) throws IOException {
		final StringBuilder sb = new StringBuilder(100);
		final int lastSlash = node.name.lastIndexOf('/');
		final String classSimpleName;
		if (lastSlash > 0) {
			classSimpleName = node.name.substring(lastSlash + 1);
		}
		else {
			classSimpleName = node.name;
		}

		SourceLines header = new SourceLines(blocks.getLevel());
		blocks.setHeader(header);
		if (!node.name.contains("$")) {
			writeLine(header, "/**");
			sb.append(" * Pseudocode of class ").append(node.name.replace('/', '.')).append('.'); writeLine(header, sb);
			writeLine(header, " *");
			sb.append(" * <p>Generator: ").append(ClassRegistry.VERSION).append("</p>"); writeLine(header, sb);
			writeLine(header, " */");
		}
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
		sb.append(" {"); writeLine(header, sb);
		writeLine(header, "");
		level++;
		
		final String packageThis = Utils.getPackage(node.name.replace('/', '.'));

		// Append fields.
		{
			final SourceLines blockFields = blocks.createSourceLines();
			for (final FieldNode fieldNode : node.fields) {
				appendAccessField(sb, fieldNode.access);
				final Type type = Type.getType(fieldNode.desc);
				String className = simplifyClassName(type, packageThis);
				sb.append(className);
				sb.append(' ').append(fieldNode.name);
				final Object initVal = fieldNode.value;
				if (initVal != null) {
					sb.append(' ').append('=').append(' ');
					Utils.appendConstant(sb, initVal);
				}
				sb.append(';'); writeLine(blockFields, sb);
			}
		}

		for (final MethodNode methodNode : node.methods) {
			SourceBlockList blockMethod = blocks.createSourceBlockList("method " + methodNode.name);
			final SourceLines methodHeader = new SourceLines(blockMethod.getLevel());
			final SourceLines methodTail = new SourceLines(blockMethod.getLevel());
			blockMethod.setHeader(methodHeader);
			blockMethod.setTail(methodTail);
			final SourceLines methodBody = blockMethod.createSourceLines();
			writeLine(methodHeader, "");
			appendAccessMethod(sb, methodNode.access);
			final Type type = Type.getMethodType(methodNode.desc);
			final Type returnType = type.getReturnType();
			if ("<init>".equals(methodNode.name)) {
				sb.append(simplifyClassName(classSimpleName));
			}
			else if ("<clinit>".equals(methodNode.name)) {
				// static initializer
			}
			else {
				sb.append(simplifyClassName(returnType.getClassName()));
				sb.append(' ').append(methodNode.name);
			}
			sb.append('(');
			final Type[] argTypes = type.getArgumentTypes();
			final int indexFirstArg = ((methodNode.access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;
			final List<LocalVariableNode> localVariables = methodNode.localVariables;
			final int anzLocVar = (localVariables != null) ? localVariables.size() : 0;
			for (int i = 0; i < argTypes.length; i++) {
				if (i > 0) {
					sb.append(", ");
				}
				String className = simplifyClassName(argTypes[i].getClassName());
				sb.append(className);
				final String argName;
				if (localVariables != null && indexFirstArg + i < anzLocVar) {
					argName = localVariables.get(indexFirstArg + i).name;
				}
				else {
					argName = "arg" + (i + 1);
				}
				sb.append(' ').append(argName);
			}
			sb.append(')');
			final List<String> exceptions = methodNode.exceptions;
			for (int i = 0; i < exceptions.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(exceptions.get(i));
			}
			sb.append(" {"); writeLine(methodHeader, sb);
			level++;
			writeMethodBody(methodBody, sb, node, methodNode);
			level--;
			writeLine(methodTail, "}");
		}

		final String packageClass = Utils.getPackage(node.name.replace('/', '.'));
		for (final InnerClassNode innerClassNode : node.innerClasses) {
			final String innerClassName = innerClassNode.name.replace('/', '.');
			final String innerPackage = Utils.getPackage(innerClassName);
			if (!packageClass.equals(innerPackage)) {
				continue;
			}
			final ClassNode innerClass = innerClassesProvider.apply(innerClassNode.name);
			if (innerClass == null) {
				throw new JvmException(String.format("No class-node of inner-class (%s) of (%s)",
						innerClassNode.name, node.name));
			}
			if (setInnerClassesProcessed.add(innerClass.name)) {
				SourceLines blockSep = blocks.createSourceLines();
				writeLine(blockSep, "");

				final SourceBlockList blockList = blocks.createSourceBlockList("inner class "
						+ innerClass.name.replaceFirst(".*[/]", ""));
				writeClass(blockList, innerClass, innerClassesProvider);
			}
		}
		
		level--;
		
		SourceLines tail = new SourceLines(blocks.getLevel());
		blocks.setTail(tail);
		writeLine(tail, "}");
	}

	/**
	 * Removes "java.lang." in a class-name.
	 * @param type type
	 * @return simplified class-name
	 */
	public static String simplifyClassName(final Type type) {
		String className = type.getClassName();
		return simplifyClassName(className, null);
	}

	/**
	 * Removes "java.lang." in a class-name.
	 * @param type type
	 * @param packageThis package of current class ("this")
	 * @return simplified class-name
	 */
	public static String simplifyClassName(final Type type, final String packageThis) {
		String className = type.getClassName();
		return simplifyClassName(className, packageThis);
	}

	/**
	 * Removes "java.lang." in a class-name.
	 * @param className class-name, e.g. "java.lang.System" or "org.apache.abc.Xyz"
	 * @return simplified class-name
	 */
	public static String simplifyClassName(final String className) {
		return simplifyClassName(className, null);
	}

	/**
	 * Removes "java.lang." in a class-name.
	 * @param className class-name, e.g. "java.lang.System" or "org.apache.abc.Xyz"
	 * @param packageThis package of current class ("this")
	 * @return simplified class-name
	 */
	public static String simplifyClassName(final String className, final String packageThis) {
		String name = className;
		final String classPackage = Utils.getPackage(className);
		if (PKG_JAVA_LANG.equals(classPackage)) {
			name = className.substring(PKG_JAVA_LANG.length()  + 1);
		}
		else if (classPackage.equals(packageThis)) {
			name = className.substring(packageThis.length() + 1);
		}
		return name;
	}

	protected void writeMethodBody(final SourceLines block, final StringBuilder sb, ClassNode classNode, MethodNode methodNode) throws IOException {
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
				sb.append(ln.getLabel().toString()).append(':'); writeLine(block, sb);
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
				writeLine(block, sInstruction);
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
			sb.append("final ");
		}
		if ((access & Opcodes.ACC_ABSTRACT) != 0) {
			sb.append("abstract ");
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
	static String buildMethodKey(ClassNode classNode, MethodNode methodNode) {
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

	public LineTable computeMethodLines(ClassNode classNode, MethodNode methodNode) {
		final List<LineCodeIndex> lines = new ArrayList<>();
		final String methodKey = buildMethodKey(classNode, methodNode);
		final Integer firstLine = mapMethodFirstLine.get(methodKey);
		if (firstLine == null) {
			throw new JvmException(String.format("No first line of method (%s)", methodKey));
		}

		int lineNo = firstLine.intValue();
		
		final InsnList instructions = methodNode.instructions;
		for (int i = 0; i < instructions.size(); i++) {
			final AbstractInsnNode instr = instructions.get(i);
			final int opcode = instr.getOpcode();
			final int type = instr.getType();
			if (type == AbstractInsnNode.LABEL) {
				lineNo++;
			}
			else if (opcode >= 0) {
				lineNo++;
				lines.add(new LineCodeIndex(i, lineNo));
			}
		}
		long start = 0;
		long end = instructions.size() - 1;

		return new LineTable(start, end, lines);
	}

	/**
	 * Write a line and adds a line-break.
	 * @param lines current source-block
	 * @param line content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	protected void writeLine(final SourceLines lines, String line) throws IOException {
		final StringBuilder sbLine = new StringBuilder(line.length() + 10);
		sbLine.append("//");
		for (int i = 0; i < level; i++) {
			sbLine.append("    ");
		}
		sbLine.append(line);
		lines.addLine(lineNum, sbLine.toString());
		lineNum++;
	}

	/**
	 * Write a line and adds a line-break.
	 * The string-builder's length will be set to zero.
	 * @param lines current source-block
	 * @param sb content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	protected void writeLine(final SourceLines lines, StringBuilder sb) throws IOException {
		final StringBuilder sbLine = new StringBuilder(sb.length() + 10);
		sbLine.append("//");
		for (int i = 0; i < level; i++) {
			sbLine.append("    ");
		}
		sbLine.append(sb);
		lines.addLine(lineNum, sbLine.toString());
		sb.setLength(0);
		lineNum++;
	}

	/**
	 * Writes a list of source-lines.
	 * @param bw writer
	 * @param sourceLines list of source-lines
	 * @param indentation optional indentation-string
	 * @param lineBreak line-break
	 * @throws IOException
	 */
	@SuppressWarnings("static-method")
	public void writeLines(final Writer bw, final List<SourceLine> sourceLines,
			final String indentation, final String lineBreak) throws IOException {
		final StringBuilder sb = new StringBuilder(100);
		for (final SourceLine sourceLine : sourceLines) {
			if (indentation != null) {
				for (int i = 0; i < sourceLine.getLevel(); i++) {
					sb.append(indentation);
				}
			}
			sb.append(sourceLine.getSourceLine());
			sb.append(lineBreak);
			bw.write(sb.toString());
			sb.setLength(0);
		}
	}

	/**
	 * Gets the extension of the generated file.
	 * @return extension, e.g. "asm"
	 */
	public String getExtension() {
		return extension;
	}

}
