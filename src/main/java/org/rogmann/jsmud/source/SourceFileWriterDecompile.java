package org.rogmann.jsmud.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.visitors.InstructionVisitor;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Source-file-writer which tries to decompile bytecode.
 * 
 * TODO This class is "work in progress".
 */
public class SourceFileWriterDecompile extends SourceFileWriter {


	/**
	 * Constructor, writes the source-file.
	 * @param extension extension, e.g. "asm"
	 * @param node class-node
	 * @param innerClassesProvider function which returns a class-node corresponding to an internal-name
	 * @param classLoader class-loader to load inner classes
	 * @throws IOException in case of an IO-error
	 */
	public SourceFileWriterDecompile(final String extension, final ClassNode node, final ClassLoader classLoader) throws IOException {
		super(extension, node, createInnerClassesLoader(classLoader));
	}

	/**
	 * Constructor, writes the source-file.
	 * @param extension extension, e.g. "asm"
	 * @param node class-node
	 * @param innerClassesProvider function which returns a class-node corresponding to an internal-name
	 * @throws IOException in case of an IO-error
	 */
	public SourceFileWriterDecompile(final String extension, final ClassNode node,
			final Function<String, ClassNode> innerClassesProvider) throws IOException {
		super(extension, node, innerClassesProvider);
	}

	/**
	 * Writes a source-file of a given class.
	 * @param clazz class
	 * @param cl class-loader to be used to read class
	 * @param w writer
	 * @throws IOException in case of an IO-error 
	 */
	public static void writeSource(final Class<?> clazz, final ClassLoader cl, final Writer w) throws IOException {
		final ClassReader classReader = createClassReader(clazz, cl);
		final ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);

		SourceFileWriterDecompile sourceFile = new SourceFileWriterDecompile("java", classNode, cl);
		final SourceBlockList blockList = sourceFile.getSourceBlockList();
		final List<SourceLine> sourceLines = new ArrayList<>(100);
		blockList.collectLines(sourceLines, 0);
		final boolean respectSourceLineNumbers = true;
		sourceFile.writeLines(w, sourceLines, "    ", System.lineSeparator(), respectSourceLineNumbers);
	}

	/**
	 * Writes a method-body.
	 * @param block current source-block
	 * @param sb string-builder
	 * @param classNode class-node
	 * @param methodNode method-node
	 * @throws IOException in case of an IO-error
	 */
	@Override
	protected void writeMethodBody(final SourceLines block, final StringBuilder sb, ClassNode classNode, MethodNode methodNode) throws IOException {
		final Map<Integer, Integer> mapPcLine = new HashMap<>();
		final List<StatementBase> statements = parseMethodBody(classNode, methodNode, mapPcLine);
		for (StatementBase stmt : statements) {
			int lineExpected = 0;
			if (stmt instanceof StatementInstr<?>) {
				final StatementInstr<?> stmtInstr = (StatementInstr<?>) stmt;
				final int pc = methodNode.instructions.indexOf(stmtInstr.getInsn());
				final Integer line = mapPcLine.get(Integer.valueOf(pc));
				if (line != null) {
					lineExpected = line.intValue();
				}
			}
			if (stmt.isVisible()) {
				stmt.render(sb);
				writeLine(block, lineExpected, sb);
			}
		}
	}

	/**
	 * Parses the bytecode of a method and converts it into a list of statements.
	 * @param classNode class-node
	 * @param methodNode method-node
	 * @param mapPcLine map from instruction-index to line
	 * @return list of statements
	 */
	public List<StatementBase> parseMethodBody(final ClassNode classNode, final MethodNode methodNode, final Map<Integer, Integer> mapPcLine) {
		final List<StatementBase> statements = new ArrayList<>();
		Integer currLine = Integer.valueOf(0);
		final Stack<ExpressionBase<?>> stack = new Stack<>();
		/** map from label to label-name */
		final Map<Label, String> mapUsedLabels = new IdentityHashMap<>();
		final AtomicInteger dupCounter = new AtomicInteger();
		FrameNode currentFrame = null;

		final InsnList instructions = methodNode.instructions;
		for (int i = 0; i < instructions.size(); i++) {
			final AbstractInsnNode instr = instructions.get(i);
			final int type = instr.getType();
			if (type == AbstractInsnNode.LINE) {
				final LineNumberNode lnn = (LineNumberNode) instr;
				currLine = Integer.valueOf(lnn.line);
				mapPcLine.put(Integer.valueOf(i), currLine);
				continue;
			}
			else if (type == AbstractInsnNode.FRAME) {
				final FrameNode fn = (FrameNode) instr;
				currentFrame = fn;
				continue;
			}

			mapPcLine.put(Integer.valueOf(i), currLine);

			try {
				processInstruction(classNode, methodNode, instr, statements, stack, dupCounter, mapUsedLabels);
			} catch (EmptyStackException e) {
				throw new JvmException(String.format("Unexpected empty expression-stack at instruction %d (%s) of method %s%s",
						Integer.valueOf(methodNode.instructions.indexOf(instr)),
						InstructionVisitor.displayInstruction(instr, methodNode),
						methodNode.name, methodNode.desc), e);
			}
		}
		return statements;
	}

	/**
	 * Processes an instruction of a method.
	 * @param classNode class-node
	 * @param methodNode method-node
	 * @param instr current instruction
	 * @param statements list of statements
	 * @param stack stack of expressions
	 * @param dupCounter counter of dup-statements (dummy dup-variables)
	 * @param mapUsedLabels map of used labels
	 */
	@SuppressWarnings("static-method")
	public void processInstruction(final ClassNode classNode, final MethodNode methodNode,
			final AbstractInsnNode instr, final List<StatementBase> statements,
			final Stack<ExpressionBase<?>> stack, AtomicInteger dupCounter, final Map<Label, String> mapUsedLabels) {
		final int opcode = instr.getOpcode();
		final int type = instr.getType();
		if (type == AbstractInsnNode.LABEL) {
			final LabelNode ln = (LabelNode) instr;
			final StatementLabel stmtLabel = new StatementLabel(ln, mapUsedLabels);
			statements.add(stmtLabel);
		}
		else if (type == AbstractInsnNode.INSN) {
			final InsnNode iz = (InsnNode) instr;
			if (opcode == Opcodes.ICONST_M1
					|| opcode == Opcodes.ICONST_0
					|| opcode == Opcodes.ICONST_1
					|| opcode == Opcodes.ICONST_2
					|| opcode == Opcodes.ICONST_3
					|| opcode == Opcodes.ICONST_4
					|| opcode == Opcodes.ICONST_5
					|| opcode == Opcodes.LCONST_0
					|| opcode == Opcodes.LCONST_1
					|| opcode == Opcodes.FCONST_0
					|| opcode == Opcodes.FCONST_1
					|| opcode == Opcodes.DCONST_0
					|| opcode == Opcodes.DCONST_1) {
				final ExpressionInstrZeroConstant exprConst = new ExpressionInstrZeroConstant(iz);
				stack.push(exprConst);
			}
			else if (opcode == Opcodes.AALOAD
					|| opcode == Opcodes.BALOAD
					|| opcode == Opcodes.CALOAD
					|| opcode == Opcodes.SALOAD
					|| opcode == Opcodes.IALOAD
					|| opcode == Opcodes.LALOAD
					|| opcode == Opcodes.FALOAD
					|| opcode == Opcodes.DALOAD) {
				final ExpressionBase<?> exprIndex = stack.pop();
				final ExpressionBase<?> exprArray = stack.pop();
				final ExpressionArrayLoad exprAaload = new ExpressionArrayLoad(iz, exprArray, exprIndex);
				stack.push(exprAaload);
			}
			else if (opcode == Opcodes.ARRAYLENGTH) {
				final ExpressionBase<?> exprArray = stack.pop();
				stack.push(new ExpressionSuffix<>(iz, exprArray));
			}
			else if (opcode == Opcodes.AASTORE) {
				final ExpressionBase<?> exprValue = stack.pop();
				final ExpressionBase<?> exprIndex = stack.pop();
				final ExpressionBase<?> exprArray = stack.pop();
				statements.add(new StatementArrayStore(iz, exprArray, exprIndex, exprValue));
			}
			else if (opcode == Opcodes.BASTORE
					|| opcode == Opcodes.CASTORE
					|| opcode == Opcodes.SASTORE
					|| opcode == Opcodes.IASTORE
					|| opcode == Opcodes.LASTORE
					|| opcode == Opcodes.FASTORE
					|| opcode == Opcodes.DASTORE) {
				final ExpressionBase<?> exprValue = stack.pop();
				final ExpressionBase<?> exprIndex = stack.pop();
				final ExpressionBase<?> exprArray = stack.pop();
				statements.add(new StatementArrayStore(iz, exprArray, exprIndex, exprValue));
			}
			else if (opcode == Opcodes.DUP) {
				final ExpressionBase<?> expr = stack.pop();
				if (expr instanceof ExpressionDuplicate) {
					// There is a StatementExpressionDuplicated already.  
					stack.push(expr);
					stack.push(expr);
				}
				else {
					final String dummyName = "__dup" + dupCounter.incrementAndGet();
					final StatementExpressionDuplicated<?> stmtExprDuplicated = new StatementExpressionDuplicated<>(expr, dummyName);
					final ExpressionDuplicate<?> exprDuplicate = new ExpressionDuplicate<>(stmtExprDuplicated);
					statements.add(stmtExprDuplicated);
					stack.push(exprDuplicate);
					stack.push(exprDuplicate);
				}
			}
			else if (opcode == Opcodes.POP) {
				if (stack.size() == 0) {
					// TODO TABLESWITCH (POP after IF...) ...
					statements.add(new StatementComment("empty stack at pop"));
				}
				else {
					final ExpressionBase<?> expr = stack.pop();
					statements.add(new StatementExpression<>(expr));
				}
			}
			else if (opcode == Opcodes.RETURN) {
				final StatementReturn stmt = new StatementReturn(iz);
				statements.add(stmt);
			}
			else if (opcode == Opcodes.ARETURN
					|| opcode == Opcodes.IRETURN
					|| opcode == Opcodes.LRETURN
					|| opcode == Opcodes.FRETURN
					|| opcode == Opcodes.DRETURN) {
				final ExpressionBase<?> exprObj = stack.pop();
				final StatementReturn stmt = new StatementReturn(iz, exprObj);
				statements.add(stmt);
			}
			else if (opcode == Opcodes.I2B
					|| opcode == Opcodes.I2C
					|| opcode == Opcodes.I2S
					|| opcode == Opcodes.I2L
					|| opcode == Opcodes.I2F
					|| opcode == Opcodes.I2D
					|| opcode == Opcodes.L2I
					|| opcode == Opcodes.L2F
					|| opcode == Opcodes.L2D
					|| opcode == Opcodes.F2I
					|| opcode == Opcodes.F2L
					|| opcode == Opcodes.F2D
					|| opcode == Opcodes.D2I
					|| opcode == Opcodes.D2L
					|| opcode == Opcodes.D2F) {
				final ExpressionBase<?> exprObj = stack.pop();
				final Type primitiveType;
				switch (opcode) {
				case Opcodes.I2B: primitiveType = Type.BYTE_TYPE; break;
				case Opcodes.I2C: primitiveType = Type.CHAR_TYPE; break;
				case Opcodes.I2S: primitiveType = Type.SHORT_TYPE; break;
				case Opcodes.I2L: primitiveType = Type.LONG_TYPE; break;
				case Opcodes.I2F: primitiveType = Type.FLOAT_TYPE; break;
				case Opcodes.I2D: primitiveType = Type.DOUBLE_TYPE; break;
				case Opcodes.L2I: primitiveType = Type.INT_TYPE; break;
				case Opcodes.L2F: primitiveType = Type.FLOAT_TYPE; break;
				case Opcodes.L2D: primitiveType = Type.DOUBLE_TYPE; break;
				case Opcodes.F2I: primitiveType = Type.INT_TYPE; break;
				case Opcodes.F2L: primitiveType = Type.LONG_TYPE; break;
				case Opcodes.F2D: primitiveType = Type.DOUBLE_TYPE; break;
				case Opcodes.D2I: primitiveType = Type.INT_TYPE; break;
				case Opcodes.D2F: primitiveType = Type.FLOAT_TYPE; break;
				case Opcodes.D2L: primitiveType = Type.DOUBLE_TYPE; break;
				default: throw new JvmException("Unexpected opcode " + opcode);
				}
				stack.add(new ExpressionCastPrimitive(iz, primitiveType, exprObj));
			}
			else if (opcode == Opcodes.IADD
					 || opcode == Opcodes.LADD
					 || opcode == Opcodes.FADD
					 || opcode == Opcodes.DADD
					 || opcode == Opcodes.ISUB
					 || opcode == Opcodes.LSUB
					 || opcode == Opcodes.FSUB
					 || opcode == Opcodes.DSUB
					 || opcode == Opcodes.IMUL
					 || opcode == Opcodes.LMUL
					 || opcode == Opcodes.FMUL
					 || opcode == Opcodes.DMUL
					 || opcode == Opcodes.IDIV
					 || opcode == Opcodes.LDIV
					 || opcode == Opcodes.FDIV
					 || opcode == Opcodes.DDIV
					 || opcode == Opcodes.IREM
					 || opcode == Opcodes.LREM
					 || opcode == Opcodes.FREM
					 || opcode == Opcodes.DREM
					 || opcode == Opcodes.IAND
					 || opcode == Opcodes.LAND
					 || opcode == Opcodes.IOR
					 || opcode == Opcodes.LOR
					 || opcode == Opcodes.IXOR
					 || opcode == Opcodes.LXOR
					 || opcode == Opcodes.ISHL) {
				final ExpressionBase<?> arg2 = stack.pop();
				final ExpressionBase<?> arg1 = stack.pop();
				final String operator;
				switch (opcode) {
				case Opcodes.IADD: operator = "+"; break;
				case Opcodes.LADD: operator = "+"; break;
				case Opcodes.FADD: operator = "+"; break;
				case Opcodes.DADD: operator = "+"; break;
				case Opcodes.ISUB: operator = "-"; break;
				case Opcodes.LSUB: operator = "-"; break;
				case Opcodes.FSUB: operator = "-"; break;
				case Opcodes.DSUB: operator = "-"; break;
				case Opcodes.IMUL: operator = "*"; break;
				case Opcodes.LMUL: operator = "*"; break;
				case Opcodes.FMUL: operator = "*"; break;
				case Opcodes.DMUL: operator = "*"; break;
				case Opcodes.IDIV: operator = "/"; break;
				case Opcodes.LDIV: operator = "/"; break;
				case Opcodes.FDIV: operator = "/"; break;
				case Opcodes.DDIV: operator = "/"; break;
				case Opcodes.IREM: operator = "%"; break;
				case Opcodes.LREM: operator = "%"; break;
				case Opcodes.FREM: operator = "%"; break;
				case Opcodes.DREM: operator = "%"; break;
				case Opcodes.IAND: operator = "&"; break;
				case Opcodes.LAND: operator = "&"; break;
				case Opcodes.IOR: operator = "|"; break;
				case Opcodes.LOR: operator = "|"; break;
				case Opcodes.IXOR: operator = "^"; break;
				case Opcodes.LXOR: operator = "^"; break;
				case Opcodes.ISHL: operator = "<<"; break;
				default: throw new JvmException("Unexpected opcode " + opcode);
				}
				stack.push(new ExpressionInfixBinary<>(iz, operator, arg1, arg2));
			}
			else {
				throw new JvmException(String.format("Unexpected zero-arg instruction (%s) in %s",
						InstructionVisitor.displayInstruction(iz, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.LDC_INSN) {
			stack.push(new ExpressionInstrConstant((LdcInsnNode) instr));
		}
		else if (type == AbstractInsnNode.TYPE_INSN) {
			final TypeInsnNode ti = (TypeInsnNode) instr;
			if (opcode == Opcodes.NEW) {
				final ExpressionTypeInstr exprTi = new ExpressionTypeInstr(ti);
				stack.push(exprTi);
			}
			else if (opcode == Opcodes.ANEWARRAY) {
				final ExpressionBase<?> exprCount = stack.pop();
				final ExpressionTypeNewarray exprTi = new ExpressionTypeNewarray(ti, exprCount);
				stack.push(exprTi);
			}
			else if (opcode == Opcodes.CHECKCAST) {
				final ExpressionBase<?> expr = stack.pop();
				final String typeDest = ti.desc.replace('/', '.');
				final String prefixCast = String.format("(%s) ", typeDest);
				final ExpressionPrefix<?> exprTi = new ExpressionPrefix<>(ti, prefixCast, expr);
				stack.push(exprTi);
			}
			else {
				throw new JvmException(String.format("Unexpected type instruction (%s) in %s",
						InstructionVisitor.displayInstruction(ti, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.VAR_INSN) {
			final VarInsnNode vi = (VarInsnNode) instr;
			if (opcode == Opcodes.ALOAD
					|| opcode == Opcodes.ILOAD
					|| opcode == Opcodes.LLOAD
					|| opcode == Opcodes.FLOAD
					|| opcode == Opcodes.DLOAD) {
				final ExpressionVariableLoad exprVi = new ExpressionVariableLoad(vi, methodNode);
				stack.push(exprVi);
			}
			else if (opcode == Opcodes.ASTORE
					|| opcode == Opcodes.ISTORE
					|| opcode == Opcodes.LSTORE
					|| opcode == Opcodes.FSTORE
					|| opcode == Opcodes.DSTORE) {
				final StatementBase stmtValue = stack.pop();
				final ExpressionBase<?> exprValue = (ExpressionBase<?>) stmtValue;
				final StatementVariableStore stmtStore = new StatementVariableStore(vi, methodNode, exprValue);
				statements.add(stmtStore);
			}
			else {
				throw new JvmException(String.format("Unexpected variable-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(vi, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.FIELD_INSN) {
			final FieldInsnNode fi = (FieldInsnNode) instr;
			if (opcode == Opcodes.PUTFIELD) {
				final ExpressionBase<?> exprValue = stack.pop();
				final ExpressionBase<?> exprObjInstance = stack.pop();
				final StatementPutField stmtPutField = new StatementPutField(fi, exprObjInstance, exprValue);
				statements.add(stmtPutField);
			}
			else if (opcode == Opcodes.PUTSTATIC) {
				final ExpressionBase<?> exprValue = stack.pop();
				final StatementPutStatic stmtPutStatic = new StatementPutStatic(fi, classNode, exprValue);
				statements.add(stmtPutStatic);
			}
			else if (opcode == Opcodes.GETFIELD) {
				final ExpressionBase<?> exprObjInstance = stack.pop();
				stack.push(new ExpressionGetField(fi, classNode, exprObjInstance));
			}
			else if (opcode == Opcodes.GETSTATIC) {
				stack.push(new ExpressionGetStatic(fi, classNode));
			}
			else {
				throw new JvmException(String.format("Unexpected field-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(fi, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.INT_INSN) {
			final IntInsnNode iin = (IntInsnNode) instr;
			if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
				stack.push(new ExpressionInstrIntConstant(iin));
			}
			else if (opcode == Opcodes.NEWARRAY) {
				final ExpressionBase<?> exprCount = stack.pop();
				stack.push(new ExpressionInstrIntNewarray(iin, exprCount));
			}
			else {
				throw new JvmException(String.format("Unexpected int-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(iin, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.JUMP_INSN) {
			final JumpInsnNode ji = (JumpInsnNode) instr;
			final Label labelDest = ji.label.getLabel();
			final String labelName = computeLabelName(labelDest, mapUsedLabels);
			if (opcode == Opcodes.GOTO) {
				statements.add(new StatementGoto(ji, labelDest, labelName));
			}
			else if (opcode == Opcodes.IFEQ
					|| opcode == Opcodes.IFNE
					|| opcode == Opcodes.IFLE	
					|| opcode == Opcodes.IFLT
					|| opcode == Opcodes.IFGE
					|| opcode == Opcodes.IFGT) {
				final ExpressionBase<?> exprValue = stack.pop();
				final ExpressionInstrZeroConstant exprZero = new ExpressionInstrZeroConstant(new InsnNode(Opcodes.ICONST_0));
				String operator;
				switch (opcode) {
				case Opcodes.IFEQ: operator = "=="; break;
				case Opcodes.IFNE: operator = "!="; break;
				case Opcodes.IFLE: operator = "<="; break;
				case Opcodes.IFLT: operator = "<"; break;
				case Opcodes.IFGE: operator = ">="; break;
				case Opcodes.IFGT: operator = ">"; break;
				default: throw new JvmException("Unexpected opcode " + opcode);
				}
				final ExpressionInfixBinary<?> exprCond = new ExpressionInfixBinary<>(instr, operator, exprValue, exprZero);
				statements.add(new StatementIf(ji, exprCond, labelDest, labelName));
			}
			else if (opcode == Opcodes.IF_ACMPEQ
					|| opcode == Opcodes.IF_ACMPNE
					|| opcode == Opcodes.IF_ICMPEQ
					|| opcode == Opcodes.IF_ICMPNE
					|| opcode == Opcodes.IF_ICMPLE
					|| opcode == Opcodes.IF_ICMPLT
					|| opcode == Opcodes.IF_ICMPGE
					|| opcode == Opcodes.IF_ICMPGT) {
				final ExpressionBase<?> expr2 = stack.pop();
				final ExpressionBase<?> expr1 = stack.pop();
				final String operator;
				if (opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ICMPEQ) {
					operator = "==";
				}
				else if (opcode == Opcodes.IF_ACMPNE || opcode == Opcodes.IF_ICMPNE) {
					operator = "!=";
				}
				else if (opcode == Opcodes.IF_ICMPLE) {
					operator = "<=";
				}
				else if (opcode == Opcodes.IF_ICMPLT) {
					operator = "<";
				}
				else if (opcode == Opcodes.IF_ICMPGE) {
					operator = ">=";
				}
				else if (opcode == Opcodes.IF_ICMPGT) {
					operator = ">";
				}
				else {
					throw new JvmException("Unexpected opcode " + opcode);
				}
				final ExpressionInfixBinary<JumpInsnNode> exprCond = new ExpressionInfixBinary<>(ji, operator, expr1, expr2);
				statements.add(new StatementIf(ji, exprCond, labelDest, labelName));
			}
			else if (opcode == Opcodes.IFNULL
					|| opcode == Opcodes.IFNONNULL) {
				final ExpressionBase<?> expr = stack.pop();
				final String operator = (opcode == Opcodes.IFNULL) ? "==" : "!=";
				final ExpressionNull exprNull = new ExpressionNull();
				final ExpressionInfixBinary<JumpInsnNode> exprCond = new ExpressionInfixBinary<>(ji, operator, expr, exprNull);
				statements.add(new StatementIf(ji, exprCond, labelDest, labelName));
			}
			else {
				throw new JvmException(String.format("Unexpected jump-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(ji, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.METHOD_INSN) {
			final MethodInsnNode mi = (MethodInsnNode) instr;
			final Type[] args = Type.getArgumentTypes(mi.desc);
			final Type returnType = Type.getReturnType(mi.desc);
			final ExpressionBase<?>[] exprArgs = new ExpressionBase[args.length];
			for (int j = args.length - 1; j >= 0; j--) {
				if (stack.size() == 0 || stack.peek() == null) {
					throw new JvmException(String.format("Stack underfow while reading constructor-args of %s", mi));
				}
				exprArgs[j] = stack.pop();
			}
			if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(mi.name)) {
				ExpressionBase<?> exprObject = stack.pop();
				if (exprObject instanceof ExpressionDuplicate) {
					final ExpressionDuplicate<?> exprDuplicate = (ExpressionDuplicate<?>) exprObject;
					final ExpressionBase<?> exprBeforeDuplicate = (stack.size() > 0) ? stack.peek() : null;
					final StatementBase lastStmt = peekLastAddedStatement(statements);
					if (lastStmt == exprDuplicate.getStatementExpressionDuplicated()
							&& exprBeforeDuplicate == exprDuplicate) {
						stack.pop();
						final StatementExpressionDuplicated<?> stmtExprDuplicated = (StatementExpressionDuplicated<?>) popLastAddedStatement(statements);
						if (stmtExprDuplicated.getInsn().getOpcode() != Opcodes.NEW) {
							throw new JvmException(String.format("Unexpected stmt-dup-expression %s in %s before constructor %s. Expr-args: %s",
									stmtExprDuplicated, methodNode.name, mi.name, Arrays.toString(exprArgs)));
						}
						// We have NEW DUP INVOKESPECIAL.
						final ExpressionTypeInstr exprNew = (ExpressionTypeInstr) stmtExprDuplicated.getExpression();
						final ExpressionConstructor exprConstr = new ExpressionConstructor(mi, exprNew, exprArgs);
						stack.push(exprConstr);
					}
				}
				else if (exprObject instanceof ExpressionVariableLoad) {
					final StatementConstructor stmtConstr = new StatementConstructor(mi, classNode, exprObject, exprArgs);
					statements.add(stmtConstr);
				}
				else {
					throw new JvmException(String.format("Unexpected expression %s in %s before constructor %s. Expr-args: %s",
							exprObject, methodNode.name, mi.name, Arrays.toString(exprArgs)));
				}
			}
			else if (opcode == Opcodes.INVOKEVIRTUAL && Type.VOID_TYPE.equals(returnType)) {
				final ExpressionBase<?> exprObject = stack.pop();
				statements.add(new StatementInvoke(mi, classNode, exprObject, exprArgs));
			}
			else if (opcode == Opcodes.INVOKEVIRTUAL) {
				final ExpressionBase<?> exprObject = stack.pop();
				stack.add(new ExpressionInvoke(mi, classNode, exprObject, exprArgs));
			}
			else if (opcode == Opcodes.INVOKEINTERFACE && Type.VOID_TYPE.equals(returnType)) {
				final ExpressionBase<?> exprObject = stack.pop();
				statements.add(new StatementInvoke(mi, classNode, exprObject, exprArgs));
			}
			else if (opcode == Opcodes.INVOKEINTERFACE) {
				final ExpressionBase<?> exprObject = stack.pop();
				stack.add(new ExpressionInvoke(mi, classNode, exprObject, exprArgs));
			}
			else if (opcode == Opcodes.INVOKESTATIC && Type.VOID_TYPE.equals(returnType)) {
				statements.add(new StatementInvoke(mi, classNode, null, exprArgs));
			}
			else if (opcode == Opcodes.INVOKESTATIC) {
				stack.add(new ExpressionInvoke(mi, classNode, null, exprArgs));
			}
			else {
				throw new JvmException(String.format("Unexpected method-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(mi, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.IINC_INSN) {
			final IincInsnNode ii = (IincInsnNode) instr;
			statements.add(new StatementVariableIinc(ii, methodNode));
		}
		else if (type == AbstractInsnNode.TABLESWITCH_INSN) {
			final TableSwitchInsnNode tsi = (TableSwitchInsnNode) instr;
			final ExpressionBase<?> exprIndex = stack.pop();
			final int lMin = tsi.min;
			final int lMax = tsi.max;
			final int num = lMax - lMin + 1;
			final String nameDefault = computeLabelName(tsi.dflt.getLabel(), mapUsedLabels);
			final String[] aLabelName = new String[num];
			for (int i = 0; i < num; i++) {
				aLabelName[i] = computeLabelName(tsi.labels.get(i).getLabel(), mapUsedLabels);
			}
			statements.add(new StatementTableSwitch(tsi, exprIndex, nameDefault, aLabelName));
		}
		else {
			throw new JvmException(String.format("Unexpected instruction %s in %s",
					InstructionVisitor.displayInstruction(instr, methodNode), methodNode));
		}
	}

	/**
	 * Write a line and adds a line-break.
	 * @param lines current source-block
	 * @param line content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	@Override
	protected void writeLine(final SourceLines lines, String line) throws IOException {
		lines.addLine(0, line);
	}

	/**
	 * Write a line and adds a line-break.
	 * The string-builder's length will be set to zero.
	 * @param lines current source-block
	 * @param sb content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	@Override
	protected void writeLine(final SourceLines lines, StringBuilder sb) throws IOException {
		writeLine(lines, 0, sb);
	}

	/**
	 * Write a line and adds a line-break.
	 * The string-builder's length will be set to zero.
	 * @param lines current source-block
	 * @param sb content of the line (without line-break)
	 * @throws IOException in case of an IO-error
	 */
	private static void writeLine(final SourceLines lines, final int lineExpected, StringBuilder sb) throws IOException {
		lines.addLine(0, lineExpected, sb.toString());
		sb.setLength(0);
	}

	/**
	 * Writes a list of source-lines, may respect expected line-numbers.
	 * @param bw writer
	 * @param sourceLines list of source-lines
	 * @param indentation optional indentation-string
	 * @param lineBreak line-break
	 * @throws IOException
	 */
	public void writeLines(final Writer bw, final List<SourceLine> sourceLines,
			final String indentation, final String lineBreak,
			final boolean respectSourceLineNumbers) throws IOException {
		if (!respectSourceLineNumbers) {
			super.writeLines(bw, sourceLines, indentation, lineBreak);
			return;
		}
		int currentLine = 1;
		final StringBuilder sb = new StringBuilder(100);
		final int numLines = sourceLines.size();
		for (int i = 0; i < numLines; i++) {
			final SourceLine sourceLine = sourceLines.get(i);
			int nextLineExpected = 0;
			if (i + 1 < numLines) {
				final SourceLine nextLine = sourceLines.get(i + 1);
				nextLineExpected = nextLine.getLineExpected();
			}
			int nextNextLineExp = 0;
			if (i + 2 < numLines) {
				final SourceLine nextNextLine = sourceLines.get(i + 2);
				nextNextLineExp = nextNextLine.getLineExpected();
			}
			final int lineExpected = sourceLine.getLineExpected();
			while (lineExpected > 0 && currentLine < lineExpected) {
				bw.write(lineBreak);
				currentLine++;
			}
			if (sb.length() == 0 && indentation != null) {
				for (int l = 0; l < sourceLine.getLevel(); l++) {
					sb.append(indentation);
				}
			}
			else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
				// We are appending the current line.
				sb.append(' ');
			}
			//sb.append("/* ").append(currentLine).append(':').append(sourceLine.getLineExpected()).append("*/");
			sb.append(sourceLine.getSourceLine());
			if (nextLineExpected == 0 || currentLine < nextLineExpected
					|| currentLine > nextLineExpected + 10) {
				if (nextLineExpected == 0 && nextNextLineExp == currentLine + 1) {
					// The next next line should start without the next line in front.
				}
				else {
					sb.append(lineBreak);
					bw.write(sb.toString());
					sb.setLength(0);
					currentLine++;
				}
			}
		}
		if (sb.length() > 0) {
			bw.write(sb.toString());
			bw.write(lineBreak);
		}
	}

	/**
	 * Computes the display-name of a label.
	 * @param labelDest label
	 * @param mapUsedLabels map from label to display-name
	 * @return display-name
	 */
	private static String computeLabelName(final Label labelDest, final Map<Label, String> mapUsedLabels) {
		return mapUsedLabels.computeIfAbsent(labelDest, l -> "L" + (mapUsedLabels.size() + 1));
	}

	public static ClassReader createClassReader(final Class<?> clazz, final ClassLoader cl) {
		final String resName = '/' + clazz.getName().replace('.', '/') + ".class";
		final ClassReader classReader;
		try (final InputStream is = clazz.getResourceAsStream(resName)) {
			if (is == null) {
				throw new IllegalArgumentException(String.format("Resource (%s) not found", resName));
			}
			classReader = new ClassReader(is);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("IO-error while reading class (%s) in class-loader (%s)",
					clazz.getName(), cl), e);
		}
		return classReader;
	}

	/**
	 * Creates inner-classes-loader.
	 * @param classLoader class-loader to be used
	 * @return inner-classes-loader
	 */
	public static Function<String, ClassNode> createInnerClassesLoader(final ClassLoader classLoader) {
		final Function<String, ClassNode> innerClassProvider = (internalName -> {
			final Class<?> classInner;
			try {
				classInner = classLoader.loadClass(internalName.replace('/', '.'));
			} catch (ClassNotFoundException e) {
				throw new JvmException(String.format("Can't load inner-class (%s)", internalName), e);
			}
			final ClassReader innerReader = createClassReader(classInner, classLoader);
			final ClassNode innerClassNode = new ClassNode();
			innerReader.accept(innerClassNode, 0);
			return innerClassNode;
		});
		return innerClassProvider;
	}

	/**
	 * Gets the last added statement. The list of statements isn't modified.
	 * @param statements list of statements
	 * @return statement or <code>null</code>
	 */
	private static StatementBase peekLastAddedStatement(List<StatementBase> statements) {
		StatementBase lastStmt = null;
		final int numStmts = statements.size();
		if (numStmts > 0) {
			lastStmt = statements.get(numStmts - 1);
		}
		return lastStmt;
	}

	/**
	 * Gets the last added statement and removes it from the list.
	 * @param statements list of statements
	 * @return statement
	 */
	private static StatementBase popLastAddedStatement(List<StatementBase> statements) {
		final int numStmts = statements.size();
		if (numStmts == 0) {
			throw new JvmException("The list of statements is empty, can't pop a statement.");
		}
		return statements.remove(numStmts - 1);
	}

}
