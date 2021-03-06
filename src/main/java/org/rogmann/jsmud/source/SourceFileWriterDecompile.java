package org.rogmann.jsmud.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.visitors.InstructionVisitor;
import org.rogmann.jsmud.vm.JvmException;
import org.rogmann.jsmud.vm.OpcodeDisplay;
import org.rogmann.jsmud.vm.Utils;

/**
 * Source-file-writer which tries to decompile bytecode.
 * 
 * TODO This class is "work in progress".
 */
public class SourceFileWriterDecompile extends SourceFileWriter {
	/** <code>true</code> to display bytecode-instructions only (fallback-mode) */
	private boolean isDisplayInstructionsOnly = false;
	
	/** <code>true</code> if the decompiler should stop in case of an error */
	private boolean isFailFast = false;

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/**
	 * Constructor, writes the source-file.
	 * @param extension extension, e.g. "asm"
	 * @param node class-node
	 * @param classLoader class-loader to load inner classes
	 * @throws IOException in case of an IO-error
	 */
	public SourceFileWriterDecompile(final String extension, final ClassNode node, final ClassLoader classLoader) throws IOException {
		super(extension, node, createInnerClassesLoader(classLoader), false);
		sourceNameRenderer = new SourceNameRenderer(Utils.getPackage(node.name.replace('/', '.')),
				className -> !className.startsWith("com."));
		createBlockListAndWriteClasss();
		
		addImportStatements();
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
		sourceNameRenderer = new SourceNameRenderer(Utils.getPackage(node.name.replace('/', '.')),
				className -> !className.startsWith("com."));
		createBlockListAndWriteClasss();
		
		addImportStatements();
	}

	/**
	 * Add import-statements.
	 */
	private void addImportStatements() {
		final SourceLines header = sourceOuter.getHeader();
		final Collection<String> importedClassNames = sourceNameRenderer.getImportedClassNames();
		for (String className : importedClassNames) {
			final String line = "import " + className + ";";
			header.addLine(0, line);
		}
		if (importedClassNames.size() > 0) {
			header.addLine(0, "");
		}
	}

	/**
	 * Sets <code>true</code> if the decompiler should stop in case of an error.
	 * @param failFastFlag fail-fast-flag
	 */
	public void setIsFailFast(boolean failFastFlag) {
		isFailFast = failFastFlag;
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
		
		//System.out.println("Reordering");
		blockList.refreshSourceBlockStatistics(true);
		//final StringBuilder sb = new StringBuilder(500);
		//blockList.dumpStructure(sb, 0);
		//System.out.println(sb);
		
		final List<SourceLine> sourceLines = new ArrayList<>(100);
		blockList.collectLines(sourceLines, 0);
		blockList.lowerExpectedLines(0);
		blockList.lowerHeaderLines();
		final boolean respectSourceLineNumbers = true;
		final boolean showLineNumbers = false;
		sourceFile.writeLines(w, sourceLines, "    ", System.lineSeparator(),
				respectSourceLineNumbers, showLineNumbers);
	}

	/** {@inheritDoc} */
	@Override
	protected void writeMethodBody(final SourceLines block, final StringBuilder sb, final ClassNode classNode,
			final MethodNode methodNode, final FieldRegistry fieldRegistry) throws IOException {
		final LineManagement lineManagement = new LineManagement();
		List<StatementBase> statements;
		try {
			statements = parseMethodBody(classNode, methodNode, lineManagement, fieldRegistry);
		} catch (RuntimeException e) {
			if (isDisplayInstructionsOnly || isFailFast) {
				throw new SourceRuntimeException(String.format("Couldn't parse method %s%s of %s in instructions-only-mode",
						methodNode.name, methodNode.desc, classNode.name), e);
			}
			System.err.println(String.format("%s: Couldn't display method %s%s. Switching to instructions-only-mode.",
					classNode.name, methodNode.name, methodNode.desc));
			e.printStackTrace();
			isDisplayInstructionsOnly = true;
			statements = parseMethodBody(classNode, methodNode, lineManagement, fieldRegistry);
		}
		for (StatementBase stmt : statements) {
			int lineExpected = 0;
			if (stmt instanceof StatementInstr<?>) {
				final StatementInstr<?> stmtInstr = (StatementInstr<?>) stmt;
				final AbstractInsnNode insn = stmtInstr.getInsn();
				if (insn != null) {
					final int pc = methodNode.instructions.indexOf(insn);
					final Integer line = lineManagement.getLineNumberOfInstruction(pc);
					if (line != null) {
						lineExpected = line.intValue();
					}
				}
			}
			if (stmt.isVisible()) {
				try {
					stmt.render(sb);
				}
				catch (JvmException e) {
					final String errorMsg = String.format("Error while rendering line %d, method %s, class %s",
							Integer.valueOf(lineExpected), methodNode.name, classNode.name);
					if (isFailFast) {
						throw new SourceRuntimeException(errorMsg, e);
					}
					System.err.println(errorMsg);
					e.printStackTrace();
					sb.append("/* ").append(e.getMessage()).append("*/");
					return;
				}
				writeLine(block, lineExpected, sb);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	protected String renderClassName(final String className) {
		return sourceNameRenderer.renderClassName(className);
	}

	/** Management of the line-numbers of the source-lines of a method */
	static class LineManagement {
		/** map from index of instruction to line-number */
		private final Map<Integer, Integer> mapPcLine = new HashMap<>();
		/** current line */
		Integer currLine = Integer.valueOf(0);
		/** line-number of the first line in the current method */
		int firstLine = 0;

		/**
		 * Gets the line-number of an instruction.
		 * @param idx index of instruction
		 * @return line-number or <code>null</code>
		 */
		public Integer getLineNumberOfInstruction(final int idx) {
			return mapPcLine.get(Integer.valueOf(idx));
		}
		/**
		 * Sets the number of the current line.
		 * @param lineNo line-number
		 */
		public void setCurrentLine(int lineNo) {
			currLine = lineNo;
			if (firstLine == 0 && lineNo > 0) {
				firstLine = lineNo;
			}
		}

		/**
		 * Adds an instruction-index of the current line.
		 * @param idx index of instruction
		 */
		public void addInstructionIndexInCurrentLine(int idx) {
			mapPcLine.put(Integer.valueOf(idx), currLine);
		}

	}

	/**
	 * Parses the bytecode of a method and converts it into a list of statements.
	 * @param classNode class-node
	 * @param methodNode method-node
	 * @param lineManagement management of line-numbers
	 * @param fieldRegistry field-registry
	 * @return list of statements
	 */
	public List<StatementBase> parseMethodBody(final ClassNode classNode, final MethodNode methodNode,
			final LineManagement lineManagement, final FieldRegistry fieldRegistry) {
		final List<StatementBase> statements = new ArrayList<>();
		final Stack<ExpressionBase<?>> stack = new Stack<>();
		final InsnList instructions = methodNode.instructions;
		/** map from label to label-name */
		final Map<Label, String> mapUsedLabels = new IdentityHashMap<>();
		/** map from instruction to instruction-index (labels are inclusive) */
		final Map<AbstractInsnNode, Integer> mapInsnIndex = new IdentityHashMap<>();
		for (int i = 0; i < instructions.size(); i++) {
			mapInsnIndex.put(instructions.get(i), Integer.valueOf(i));
		}
		final AtomicInteger dupCounter = new AtomicInteger();
		LabelNode currentLabel = null;
		final Type[] typeLocals = new Type[methodNode.maxLocals];
		final Type[] argTypes = Type.getArgumentTypes(methodNode.desc);
		if (argTypes.length > 0 && methodNode.instructions.size() > 0) {
			final int offsetArg = ((Opcodes.ACC_STATIC & methodNode.access) != 0) ? 0 : 1;
			if (offsetArg + argTypes.length > typeLocals.length) {
				throw new SourceRuntimeException(String.format("More arguments than local variables: offsetArg=%d, argTypes.len=%d, typeLocals.len=%d",
						Integer.valueOf(offsetArg), Integer.valueOf(argTypes.length), Integer.valueOf(typeLocals.length)));
			}
			System.arraycopy(argTypes, 0, typeLocals, offsetArg, argTypes.length);
		}

		//System.out.println(String.format("Class %s, Method %s%s", classNode.name, methodNode.name, methodNode.desc));
		for (int i = 0; i < instructions.size(); i++) {
			final AbstractInsnNode instr = instructions.get(i);
			//System.out.println(String.format("%4d: %s", Integer.valueOf(i), InstructionVisitor.displayInstruction(instr, methodNode)));
			final int type = instr.getType();
			if (type == AbstractInsnNode.LINE) {
				if ((methodNode.access & Opcodes.ACC_SYNTHETIC) != 0) {
					// Line numbers of synthetic methods may be strange (e.g. in enumerations).
					continue;
				}
				final LineNumberNode lnn = (LineNumberNode) instr;
				lineManagement.setCurrentLine(lnn.line);
				lineManagement.addInstructionIndexInCurrentLine(i);
				// Is the previous line a label-node?
				if (i > 0 && instructions.get(i - 1).getType() == AbstractInsnNode.LABEL) {
					// The previous label should belong to the current line.
					lineManagement.addInstructionIndexInCurrentLine(i - 1);
				}
				continue;
			}
			else if (type == AbstractInsnNode.FRAME) {
				//final FrameNode fn = (FrameNode) instr;
				for (final TryCatchBlockNode tcb : methodNode.tryCatchBlocks) {
					if (tcb.type == null) {
						// e.g. finally-block
						stack.add(new ExpressionException(null, sourceNameRenderer));
						break;
					}
					if (tcb.handler.equals(currentLabel)) {
						// begin of a catch-block
						final Type typeException = Type.getObjectType(tcb.type);
						stack.add(new ExpressionException(typeException, sourceNameRenderer));
						break;
					}
				}

				continue;
			}
			if (type == AbstractInsnNode.LABEL) {
				currentLabel = (LabelNode) instr;
			}

			lineManagement.addInstructionIndexInCurrentLine(i);

			try {
				processInstruction(classNode, methodNode, instr, statements, stack,
						typeLocals, dupCounter, mapUsedLabels, mapInsnIndex, lineManagement, fieldRegistry);
			} catch (EmptyStackException e) {
				throw new SourceRuntimeException(String.format("Unexpected empty expression-stack at instruction %d (%s) of method %s%s",
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
	 * @param typeLocals types of local variables
	 * @param dupCounter counter of dup-statements (dummy dup-variables)
	 * @param mapUsedLabels map of used labels
	 * @param mapInsnIndex map from instruction-node to instruction-index (labels are instructions, too)
	 * @param lineManagement line-management 
	 * @param fieldRegistry field-registry
	 */
	public void processInstruction(final ClassNode classNode, final MethodNode methodNode,
			final AbstractInsnNode instr, final List<StatementBase> statements,
			final Stack<ExpressionBase<?>> stack, final Type[] typeLocals,
			final AtomicInteger dupCounter,
			final Map<Label, String> mapUsedLabels, final Map<AbstractInsnNode, Integer> mapInsnIndex,
			final LineManagement lineManagement, final FieldRegistry fieldRegistry) {
		final int opcode = instr.getOpcode();
		final int type = instr.getType();
		if (type == AbstractInsnNode.LABEL) {
			final LabelNode ln = (LabelNode) instr;
			checkAndProcessConditionalOperator(ln, statements, stack, mapUsedLabels);
			final StatementLabel stmtLabel = new StatementLabel(ln, mapUsedLabels);
			statements.add(stmtLabel);
		}
		else if (isDisplayInstructionsOnly && opcode >= 0) {
			statements.add(new StatementInstrPlain<>(instr, methodNode));
		}
		else if (type == AbstractInsnNode.INSN) {
			final InsnNode iz = (InsnNode) instr;
			processInstructionInsn(iz, methodNode, statements, stack, dupCounter);
		}
		else if (type == AbstractInsnNode.LDC_INSN) {
			stack.push(new ExpressionInstrConstant((LdcInsnNode) instr));
		}
		else if (type == AbstractInsnNode.TYPE_INSN) {
			final TypeInsnNode ti = (TypeInsnNode) instr;
			if (opcode == Opcodes.NEW) {
				final boolean isNewRenderingAllowed = !isFailFast;
				final ExpressionTypeInstr exprTi = new ExpressionTypeInstr(ti,
						classNode, isNewRenderingAllowed);
				stack.push(exprTi);
			}
			else if (opcode == Opcodes.ANEWARRAY) {
				final ExpressionBase<?> exprCount = stack.pop();
				final ExpressionTypeNewarray exprTi = new ExpressionTypeNewarray(ti, exprCount);
				stack.push(exprTi);
			}
			else if (opcode == Opcodes.CHECKCAST) {
				final ExpressionBase<?> expr = stack.pop();
				final String typeDest = sourceNameRenderer.renderClassName(ti.desc.replace('/', '.'));
				final String prefixCast = String.format("(%s) ", typeDest);
				final ExpressionPrefix<?> exprTi = new ExpressionPrefix<>(ti, prefixCast, expr);
				stack.push(exprTi);
			}
			else if (opcode == Opcodes.INSTANCEOF) {
				final ExpressionBase<?> exprRef = stack.pop();
				stack.push(new ExpressionInstanceOf(ti, exprRef, sourceNameRenderer));
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected type instruction (%s) in %s",
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
				final StatementBase lastStmt = peekLastAddedStatement(statements);
				ExpressionBase<?> exprVi = new ExpressionVariableLoad(vi, typeLocals[vi.var], methodNode);
				if (opcode == Opcodes.ILOAD && lastStmt instanceof StatementVariableIinc) {
					StatementVariableIinc stmtIinc = (StatementVariableIinc) lastStmt;
					if (stmtIinc.getInsn().var == vi.var) {
						popLastAddedStatement(statements);
						if (stmtIinc.getInsn().var == vi.var && stmtIinc.insn.incr == 1) {
							// We found a ++var-Expression.
							exprVi = new ExpressionPrefix<>(stmtIinc.getInsn(), "++", exprVi);
						}
						else if (stmtIinc.getInsn().var == vi.var && stmtIinc.insn.incr == -1) {
							// We found a --var-Expression.
							exprVi = new ExpressionPrefix<>(stmtIinc.getInsn(), "--", exprVi);
						}
						else {
							final String op = (stmtIinc.insn.incr < 0) ? "-=" : "+=";
							final int incr = Math.abs(stmtIinc.insn.incr);
							final IntInsnNode bipush = new IntInsnNode(Opcodes.BIPUSH, incr);
							final ExpressionInstrIntConstant exprIncr = new ExpressionInstrIntConstant(bipush);
							exprVi = new ExpressionInfixBinary<>(stmtIinc.getInsn(),
									op, exprVi, exprIncr);
						}
						
					}
				}
				stack.push(exprVi);
			}
			else if (opcode == Opcodes.ASTORE
					|| opcode == Opcodes.ISTORE
					|| opcode == Opcodes.LSTORE
					|| opcode == Opcodes.FSTORE
					|| opcode == Opcodes.DSTORE) {
				final ExpressionBase<?> exprValue = popExpressionAndMergeDuplicate(statements, stack);
				final List<LocalVariableNode> locVars = methodNode.localVariables;
				final Type typeExpr;
				if (locVars != null) {
					final LocalVariableNode locVar = findLocalVariable(vi.var, locVars, instr, mapInsnIndex);
					typeExpr = (locVar != null) ? Type.getType(locVar.desc) : null;
				}
				else {
					switch (opcode) {
					case Opcodes.ASTORE: typeExpr = (exprValue.getType() != null) ?
							exprValue.getType() : Type.getType("Ljava/lang/Object;"); break;
					case Opcodes.ISTORE: typeExpr = Type.INT_TYPE; break;
					case Opcodes.LSTORE: typeExpr = Type.LONG_TYPE; break;
					case Opcodes.FSTORE: typeExpr = Type.FLOAT_TYPE; break;
					case Opcodes.DSTORE: typeExpr = Type.DOUBLE_TYPE; break;
					default: throw new SourceRuntimeException("Unexpected opcode " + opcode);
					}
				}
				final StatementVariableStore stmtStore = new StatementVariableStore(vi,
						methodNode, typeExpr, exprValue, sourceNameRenderer);
				typeLocals[vi.var] = typeExpr;
				statements.add(stmtStore);
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected variable-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(vi, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.FIELD_INSN) {
			final FieldInsnNode fi = (FieldInsnNode) instr;
			if (opcode == Opcodes.PUTFIELD) {
				if ("<init>".equals(methodNode.name)
						&& lineManagement.firstLine > lineManagement.currLine.intValue()
						&& fi.owner.equals(classNode.name)) {
					// field fi may be initialized at line lineManagement.currLine
					final SourceLine sourceLine = fieldRegistry.getSourceLine(fi.name);
					if (sourceLine.getLineExpected() == 0) {
						sourceLine.setLineExpected(lineManagement.currLine.intValue());
					}
				}
				
				// Special case: new x(exprs2(y = exprs1(y))).
				// NEW x; DUP; GETFIELD y; exprs 1; DUP_X1; PUTFIELD y; exprs 2; INVOKE <init>
				// stack: ..., exprs 1, DUP NEW x, exprs 1
				// stmts: ..., DUP NEW x, DUP exprs 1
				// Problem: remaining __DUPs.
				final ExpressionBase<?> exprValue = stack.pop();
				final ExpressionBase<?> exprObjInstance = stack.pop();
				final StatementBase lastStmt = peekLastAddedStatement(statements);
				final boolean ignoreLabels = false;
				final StatementBase lastStmt2 = peekLastAddedStatement(statements, 2, ignoreLabels);
				boolean isProcessed = false;
				if (exprValue instanceof ExpressionDuplicate
						&& exprObjInstance instanceof ExpressionDuplicate
						&& lastStmt instanceof StatementExpressionDuplicated<?>
						&& lastStmt2 instanceof StatementExpressionDuplicated<?>
						&& stack.size() > 0) {
					final ExpressionBase<?> exprBefore = stack.peek();
					final ExpressionDuplicate<?> exprValDupl = (ExpressionDuplicate<?>) exprValue;
					final ExpressionDuplicate<?> exprObjDupl = (ExpressionDuplicate<?>) exprObjInstance;
					final StatementExpressionDuplicated<?> stmtExprValDupl = (StatementExpressionDuplicated<?>) lastStmt;
					final StatementExpressionDuplicated<?> stmtExprObjDupl = (StatementExpressionDuplicated<?>) lastStmt2;
					if (exprValDupl.getStatementExpressionDuplicated() == stmtExprValDupl
							&& exprObjDupl.getStatementExpressionDuplicated() == stmtExprObjDupl
							&& exprBefore == exprValDupl) {
						final ExpressionPutField exprPutField = new ExpressionPutField(fi,
								stmtExprObjDupl.getExpression(), stmtExprValDupl.getExpression());
						stack.pop(); // remove duplicate exprBefore
						stack.push(exprPutField);
						popLastAddedStatement(statements); // remove DUP exprs 1
						popLastAddedStatement(statements); // remove DUP NEW x
						isProcessed = true;
					}
				}
				if (!isProcessed) {
					final StatementPutField stmtPutField = new StatementPutField(fi, exprObjInstance, exprValue);
					statements.add(stmtPutField);
				}
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
				stack.push(new ExpressionGetStatic(fi, classNode, sourceNameRenderer));
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected field-instruction (%s) in (%s)",
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
				stack.push(new ExpressionInstrIntNewarray(iin, exprCount, sourceNameRenderer));
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected int-instruction (%s) in (%s)",
						InstructionVisitor.displayInstruction(iin, methodNode), methodNode.name));
			}
		}
		else if (type == AbstractInsnNode.JUMP_INSN) {
			final JumpInsnNode ji = (JumpInsnNode) instr;
			processInstructionJumpInsn(ji, methodNode, statements, stack, mapUsedLabels);
		}
		else if (type == AbstractInsnNode.METHOD_INSN) {
			final MethodInsnNode mi = (MethodInsnNode) instr;
			processInstructionMethodInsn(mi, classNode, methodNode, statements, stack);
		}
		else if (type == AbstractInsnNode.IINC_INSN) {
			final IincInsnNode ii = (IincInsnNode) instr;
			final ExpressionBase<?> exprLast = (stack.size() > 0) ? stack.peek() : null;
			ExpressionBase<?> exprNew = null;
			if (exprLast instanceof ExpressionVariableLoad) {
				final ExpressionVariableLoad exprVi = (ExpressionVariableLoad) exprLast;
				if (exprVi.insn.var == ii.var && (ii.incr == +1 || ii.incr == -1)) {
					// We found var++ or var--.
					stack.pop();
					exprNew = new ExpressionSuffix<AbstractInsnNode>(ii, exprLast);
					stack.push(exprNew);
				}
			}
			if (exprNew == null) {
				statements.add(new StatementVariableIinc(ii, methodNode));
			}
		}
		else if (type == AbstractInsnNode.INVOKE_DYNAMIC_INSN) {
			final InvokeDynamicInsnNode idi = (InvokeDynamicInsnNode) instr;
			final Type[] args = Type.getArgumentTypes(idi.desc);
			final ExpressionBase<?>[] exprArgs = new ExpressionBase[args.length];
			for (int j = args.length - 1; j >= 0; j--) {
				if (stack.size() == 0 || stack.peek() == null) {
					throw new SourceRuntimeException(String.format("Stack underflow while reading targs of %s", idi));
				}
				exprArgs[j] = stack.pop();
			}
			final Handle handle = (Handle) idi.bsmArgs[1];
			final Type[] handleArgs = Type.getArgumentTypes(handle.getDesc());
			final int anzObjInst = (handle.getTag() == Opcodes.H_INVOKESTATIC) ? 0 : 1;
			final String[] tempVars = new String[anzObjInst + handleArgs.length - args.length];
			for (int i = 0; i < tempVars.length; i++) {
				tempVars[i] = createTempName(dupCounter);
			}
			stack.push(new ExpressionInvokeDynamic(idi, classNode, exprArgs, tempVars));
		}
		else if (type == AbstractInsnNode.LOOKUPSWITCH_INSN) {
			final LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) instr;
			final ExpressionBase<?> exprKey = stack.pop();
			final int numCases = lsi.keys.size();
			final String nameDefault = computeLabelName(lsi.dflt.getLabel(), mapUsedLabels);
			final String[] aLabelName = new String[numCases];
			for (int i = 0; i < numCases; i++) {
				aLabelName[i] = computeLabelName(lsi.labels.get(i).getLabel(), mapUsedLabels);
			}
			statements.add(new StatementLookupSwitch(lsi, exprKey, nameDefault, aLabelName));
		}
		else if (type == AbstractInsnNode.MULTIANEWARRAY_INSN) {
			final MultiANewArrayInsnNode manai = (MultiANewArrayInsnNode) instr;
			final ExpressionBase<?>[] aExprDims = new ExpressionBase<?>[manai.dims];
			for (int i = aExprDims.length - 1; i >= 0; i--) {
				aExprDims[i] = stack.pop();
			}
			stack.push(new ExpressionMultiNewarray(manai, aExprDims));
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
			throw new SourceRuntimeException(String.format("Unexpected instruction %s in %s",
					InstructionVisitor.displayInstruction(instr, methodNode), methodNode));
		}
	}

	private void processInstructionInsn(final InsnNode iz, final MethodNode methodNode,
			final List<StatementBase> statements, final Stack<ExpressionBase<?>> stack, AtomicInteger dupCounter) {
		final int opcode = iz.getOpcode();
		if (opcode == Opcodes.ACONST_NULL
				|| opcode == Opcodes.ICONST_M1
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
			boolean merged = arrayInitialValueMerge(exprArray, exprIndex, exprValue, statements);
			if (!merged) {
				statements.add(new StatementArrayStore(iz, exprArray, exprIndex, exprValue));
			}
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
			boolean merged = arrayInitialValueMerge(exprArray, exprIndex, exprValue, statements);
			if (!merged) {
				statements.add(new StatementArrayStore(iz, exprArray, exprIndex, exprValue));
			}
		}
		else if (opcode == Opcodes.DUP
				|| (opcode == Opcodes.DUP2 && ValueCategory.isCat2(stack.peek()))) {
			if (opcode == Opcodes.DUP2) {
				// form 2 of DUP 2: long or double.
				statements.add(new StatementComment("DUP2(cat2)->"));
			}
			final ExpressionBase<?> expr = stack.pop();
			final ExpressionBase<?> exprDuplicated = createStatementExpressionDuplicated(expr,
					statements, stack, dupCounter);
			stack.push(exprDuplicated);
		}
		else if (opcode == Opcodes.DUP_X1
				|| opcode == Opcodes.DUP_X2
				|| (opcode == Opcodes.DUP2_X1 && ValueCategory.isCat2(stack.peek()))
				|| (opcode == Opcodes.DUP2_X2 && ValueCategory.isCat2(stack.peek()))) {
			final ExpressionBase<?> expr1 = stack.pop();
			final ExpressionBase<?> expr2 = stack.pop();
			final ValueCategory catExpr1 = ValueCategory.lookup(expr1);
			final ValueCategory catExpr2 = ValueCategory.lookup(expr2);
			ExpressionBase<?> expr3 = null;
			if (opcode == Opcodes.DUP_X2) {
				// category 2: long and double use two entries on stack.
				statements.add(new StatementComment(String.format("DUP_X2(expr2 is %s)->", catExpr2)));
				if (catExpr2 == ValueCategory.CAT1) {
					expr3 = stack.pop();
				}
			}
			else if (opcode == Opcodes.DUP2_X1 || opcode ==Opcodes.DUP2_X2) {
				statements.add(new StatementComment(String.format("%s(%s %s)->",
						OpcodeDisplay.lookup(opcode), catExpr2, catExpr1)));
				if (opcode == Opcodes.DUP2_X2 && catExpr2 == ValueCategory.CAT1) {
					expr3 = stack.pop();
				}
			}
			final ExpressionBase<?> exprDuplicated = createStatementExpressionDuplicated(expr1,
					statements, stack, dupCounter);
			if (expr3 != null) {
				stack.push(expr3);
			}
			stack.push(expr2);
			stack.push(exprDuplicated);
		}
		else if (opcode == Opcodes.DUP2) {
			// long and double use two entries on stack, see DUP2/cat2 above at DUP.
			statements.add(new StatementComment("DUP2(cat1)!->"));
			final ExpressionBase<?> expr1 = stack.pop();
			final ExpressionBase<?> expr2 = stack.pop();
			final ExpressionBase<?> expr2Dup = createStatementExpressionDuplicated(expr2,
					statements, stack, dupCounter);
			final ExpressionBase<?> expr1Dup = createStatementExpressionDuplicated(expr1,
					statements, stack, dupCounter);
			stack.push(expr2Dup);
			stack.push(expr1Dup);
		}
		else if (opcode == Opcodes.DUP2_X1
				|| opcode == Opcodes.DUP2_X2) {
			final ExpressionBase<?> expr1 = stack.pop();
			final ExpressionBase<?> expr2 = stack.pop();
			final ExpressionBase<?> expr3 = stack.pop();
			ExpressionBase<?> expr4 = null;
			// Category of expression 1 is CAT2 (CAT1 is above at DUP_X1).
			final ValueCategory catExpr1 = ValueCategory.lookup(expr1);
			final ValueCategory catExpr2 = ValueCategory.lookup(expr2);
			final ValueCategory catExpr3 = ValueCategory.lookup(expr3);
			statements.add(new StatementComment(String.format("%s(%s %s %s)->",
					OpcodeDisplay.lookup(opcode), catExpr3, catExpr2, catExpr1)));
			if (opcode == Opcodes.DUP2_X2 && catExpr3 == ValueCategory.CAT1) {
				expr4 = stack.pop();
			}
			
			final ExpressionBase<?> expr2Duplicated = createStatementExpressionDuplicated(expr2,
					statements, stack, dupCounter);
			final ExpressionBase<?> expr1Duplicated = createStatementExpressionDuplicated(expr1,
					statements, stack, dupCounter);

			if (expr4 != null) {
				stack.push(expr4);
			}
			stack.push(expr3);
			stack.push(expr2Duplicated);
			stack.push(expr1Duplicated);
		}
		else if (opcode == Opcodes.POP) {
			if (stack.size() == 0) {
				if (isFailFast) {
					throw new SourceRuntimeException("Empty stack at pop");
				}
				// TODO TABLESWITCH (POP after IF...) ...
				statements.add(new StatementComment("empty stack at pop"));
			}
			else {
				final ExpressionBase<?> expr = stack.pop();
				statements.add(new StatementExpression<>(expr));
			}
		}
		else if (opcode == Opcodes.POP2) {
			if (stack.size() == 0) {
				throw new SourceRuntimeException("Empty stack at POP2");
			}
			final ExpressionBase<?> expr1 = stack.pop();
			final ValueCategory catExpr1 = ValueCategory.lookup(expr1);
			statements.add(new StatementComment(String.format("POP2(%s)->", catExpr1)));
			if (catExpr1 == ValueCategory.CAT1) {
				final ExpressionBase<?> expr2 = stack.pop();
				statements.add(new StatementExpression<>(expr2));
			}
			statements.add(new StatementExpression<>(expr1));
		}
		else if (opcode == Opcodes.MONITORENTER
				|| opcode == Opcodes.MONITOREXIT) {
			final ExpressionBase<?> exprObj = stack.pop();
			statements.add(new StatementMonitor(iz, exprObj));
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
			default: throw new SourceRuntimeException("Unexpected opcode " + opcode);
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
				 || opcode == Opcodes.ISHL
				 || opcode == Opcodes.ISHR
				 || opcode == Opcodes.IUSHR
				 || opcode == Opcodes.LSHL
				 || opcode == Opcodes.LSHR
				 || opcode == Opcodes.LUSHR) {
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
			case Opcodes.LSHL: operator = "<<"; break;
			case Opcodes.ISHR: operator = ">>"; break;
			case Opcodes.LSHR: operator = ">>"; break;
			case Opcodes.IUSHR: operator = ">>>"; break;
			case Opcodes.LUSHR: operator = ">>>"; break;
			default: throw new SourceRuntimeException("Unexpected opcode " + opcode);
			}
			stack.push(new ExpressionInfixBinary<>(iz, operator, arg1, arg2));
		}
		else if (opcode == Opcodes.INEG
				|| opcode == Opcodes.LNEG) {
			final ExpressionBase<?> exprArg = stack.pop();
			stack.add(new ExpressionPrefix<>(iz, "-", exprArg));
		}
		else if (opcode == Opcodes.LCMP
				|| opcode == Opcodes.FCMPL
				|| opcode == Opcodes.FCMPG
				|| opcode == Opcodes.DCMPL
				|| opcode == Opcodes.DCMPG) {
			final ExpressionBase<?> arg2 = stack.pop();
			final ExpressionBase<?> arg1 = stack.pop();
			stack.add(new ExpressionCompare(iz, arg1, arg2));
		}
		else if (opcode == Opcodes.ATHROW) {
			final ExpressionBase<?> exprException = stack.pop();
			statements.add(new StatementThrow(iz, exprException));
		}
		else {
			throw new SourceRuntimeException(String.format("Unexpected zero-arg instruction (%s) in %s",
					InstructionVisitor.displayInstruction(iz, methodNode), methodNode.name));
		}
	}

	private static void processInstructionJumpInsn(final JumpInsnNode ji, final MethodNode methodNode,
			final List<StatementBase> statements, final Stack<ExpressionBase<?>> stack,
			final Map<Label, String> mapUsedLabels) {
		final int opcode = ji.getOpcode();
		final Label labelDest = ji.label.getLabel();
		final String labelName = computeLabelName(labelDest, mapUsedLabels);
		// Caveat: if x goto L1, value-1, goto L2,+ L1: value-2, L2 -> checkAndProcessConditionalOperator.
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
			default: throw new SourceRuntimeException("Unexpected opcode " + opcode);
			}
			final ExpressionInfixBinary<?> exprCond = new ExpressionInfixBinary<>(ji, operator, exprValue, exprZero);
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
				throw new SourceRuntimeException("Unexpected opcode " + opcode);
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
			throw new SourceRuntimeException(String.format("Unexpected jump-instruction (%s) in (%s)",
					InstructionVisitor.displayInstruction(ji, methodNode), methodNode.name));
		}
	}

	private void processInstructionMethodInsn(final MethodInsnNode mi, final ClassNode classNode,
			final MethodNode methodNode, final List<StatementBase> statements, final Stack<ExpressionBase<?>> stack) {
		final int opcode = mi.getOpcode();
		final Type[] args = Type.getArgumentTypes(mi.desc);
		final Type returnType = Type.getReturnType(mi.desc);
		final ExpressionBase<?>[] exprArgs = new ExpressionBase[args.length];
		for (int j = args.length - 1; j >= 0; j--) {
			if (stack.size() == 0 || stack.peek() == null) {
				throw new SourceRuntimeException(String.format("Stack underfow while reading constructor-args of %s", mi));
			}
			exprArgs[j] = popExpressionAndMergeDuplicate(statements, stack);
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
						throw new SourceRuntimeException(String.format("Unexpected stmt-dup-expression %s in %s before constructor %s. Expr-args: %s",
								stmtExprDuplicated, methodNode.name, mi.name, Arrays.toString(exprArgs)));
					}
					// We have NEW DUP INVOKESPECIAL.
					final ExpressionTypeInstr exprNew = (ExpressionTypeInstr) stmtExprDuplicated.getExpression();
					final ExpressionConstructor exprConstr = new ExpressionConstructor(mi, sourceNameRenderer, exprNew, exprArgs);
					stack.push(exprConstr);
				}
				else {
					if (isFailFast) {
						throw new SourceRuntimeException(String.format("Unexpected duplicated expression %s in %s before constructor %s. Expr-args: %s",
								exprObject, methodNode.name, mi.name, Arrays.toString(exprArgs)));
					}
					statements.add(new StatementInvoke(mi, sourceNameRenderer, exprDuplicate, exprArgs));
				}
			}
			else if (exprObject instanceof ExpressionVariableLoad) {
				final StatementConstructor stmtConstr = new StatementConstructor(mi, classNode, exprObject, exprArgs);
				statements.add(stmtConstr);
			}
			else {
				throw new SourceRuntimeException(String.format("Unexpected expression %s in %s before constructor %s. Expr-args: %s",
						exprObject, methodNode.name, mi.name, Arrays.toString(exprArgs)));
			}
		}
		else if (opcode == Opcodes.INVOKEVIRTUAL && Type.VOID_TYPE.equals(returnType)) {
			final ExpressionBase<?> exprObject = stack.pop();
			statements.add(new StatementInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else if (opcode == Opcodes.INVOKEVIRTUAL) {
			final ExpressionBase<?> exprObject = stack.pop();
			stack.add(new ExpressionInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else if (opcode == Opcodes.INVOKEINTERFACE && Type.VOID_TYPE.equals(returnType)) {
			final ExpressionBase<?> exprObject = stack.pop();
			statements.add(new StatementInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else if (opcode == Opcodes.INVOKEINTERFACE) {
			final ExpressionBase<?> exprObject = stack.pop();
			stack.add(new ExpressionInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else if (opcode == Opcodes.INVOKESTATIC && Type.VOID_TYPE.equals(returnType)) {

			statements.add(new StatementInvoke(mi, sourceNameRenderer, null, exprArgs));
		}
		else if (opcode == Opcodes.INVOKESTATIC) {
			stack.add(new ExpressionInvoke(mi, sourceNameRenderer, null, exprArgs));
		}
		else if (opcode == Opcodes.INVOKESPECIAL && Type.VOID_TYPE.equals(returnType)) {
			final ExpressionBase<?> exprObject = stack.pop();
			statements.add(new StatementInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else if (opcode == Opcodes.INVOKESPECIAL) {
			final ExpressionBase<?> exprObject = stack.pop();
			stack.add(new ExpressionInvoke(mi, sourceNameRenderer, exprObject, exprArgs));
		}
		else {
			throw new SourceRuntimeException(String.format("Unexpected method-instruction (%s) in (%s)",
					InstructionVisitor.displayInstruction(mi, methodNode), methodNode.name));
		}
	}

	/**
	 * Checks if an initial value of an array can placed into initial value-list.
	 * @param exprArray array-expression
	 * @param exprIndex index-expression
	 * @param exprValue value at index-expression
	 * @param statements list of statements
	 * @return <code>true</code> if the value had been merged into the initial list
	 */
	public static boolean arrayInitialValueMerge(final ExpressionBase<?> exprArray, final ExpressionBase<?> exprIndex,
			ExpressionBase<?> exprValue, final List<StatementBase> statements) {
		boolean merged = false;
		final Integer index = getIntegerConstant(exprIndex);
		final StatementBase lastStmt = peekLastAddedStatement(statements);
		if (index != null && index.intValue() >= 0
				&& exprIndex instanceof ExpressionInstrZeroConstant
				&& exprArray instanceof ExpressionDuplicate
				&& lastStmt instanceof StatementExpressionDuplicated) {
			final ExpressionInstrZeroConstant exprIndexConst = (ExpressionInstrZeroConstant) exprIndex;
			final ExpressionDuplicate<?> exprDupl = (ExpressionDuplicate<?>) exprArray;
			final StatementExpressionDuplicated<?> stmtDupl = (StatementExpressionDuplicated<?>) lastStmt;
			final ExpressionBase<?> exprBase = stmtDupl.getExpression();
			if (exprIndexConst.isIConst()
					&& exprDupl.getStatementExpressionDuplicated() == stmtDupl
					&& exprBase instanceof ExpressionInstrIntNewarray) {
				// array with primitive elements.
				final ExpressionInstrIntNewarray exprNewArray = (ExpressionInstrIntNewarray) exprBase;
				final ExpressionBase<?> exprCount = exprNewArray.getExprCount();
				final Integer aLength = getIntegerConstant(exprCount);
				if (aLength != null
						&& index.intValue() < aLength.intValue()) {
					exprNewArray.setInitialValue(index.intValue(), exprValue, aLength.intValue());
					merged = true;
				}
			}
			else if (exprIndexConst.isIConst()
					&& exprDupl.getStatementExpressionDuplicated() == stmtDupl
					&& exprBase instanceof ExpressionTypeNewarray) {
				final ExpressionTypeNewarray exprNewArray = (ExpressionTypeNewarray) exprBase;
				final ExpressionBase<?> exprCount = exprNewArray.getExprCount();
				final Integer aLength = getIntegerConstant(exprCount);
				if (aLength != null
						&& index.intValue() < aLength.intValue()) {
					exprNewArray.setInitialValue(index.intValue(), exprValue, aLength.intValue());
					merged = true;
				}
			}
		}
		return merged;
	}

	/**
	 * Checks for an conditional operator: IF cond GOTO L1, expr2, goto L2, L1: expr1, L2:.
	 * @param ln current label-node
	 * @param statements list of statements
	 * @param stack stack of expressions
	 * @param mapUsedLabels map from label to label-name
	 */
	private static void checkAndProcessConditionalOperator(final LabelNode ln, final List<StatementBase> statements,
			final Stack<ExpressionBase<?>> stack, final Map<Label, String> mapUsedLabels) {
		final String labelName = mapUsedLabels.get(ln.getLabel());
		final StatementBase stmtLast3 = peekLastAddedStatement(statements, 3, false);
		final StatementBase stmtLast2 = peekLastAddedStatement(statements, 2, false);
		final StatementBase stmtLast1 = peekLastAddedStatement(statements, 1, false);
		if (stmtLast1 instanceof StatementLabel
				&& stmtLast2 instanceof StatementGoto
				&& stmtLast3 instanceof StatementIf) {
			final StatementIf stmtIf = (StatementIf) stmtLast3;
			final StatementGoto stmtGoto = (StatementGoto) stmtLast2;
			final StatementLabel stmtLabel = (StatementLabel) stmtLast1;
			if (stmtIf.getLabelName().equals(stmtLabel.getLabelName())
					&& stmtGoto.getLabelName().equals(labelName)
					&& stack.size() >= 2) {
				statements.remove(statements.size() - 1);
				statements.remove(statements.size() - 1);
				statements.remove(statements.size() - 1);
				ExpressionBase<?> expr1 = stack.pop();
				ExpressionBase<?> expr2 = stack.pop();
				stack.push(new ExpressionConditionalOperator<>(stmtIf.getInsn(),
						stmtIf.getExprCond(), expr1, expr2));
			}
		}
	}

	/**
	 * If the expression is a already duplicated expression it is put on stack and returned.
	 * Otherwise a StatementExpressionDuplicated is created and added to the statements and put on stack.
	 * @param expr expression to be duplicated
	 * @param statements list of statements
	 * @param stack expression-stack
	 * @param dupCounter dup-counter
	 * @return (possibly duplicated) expression
	 */
	public static ExpressionBase<?> createStatementExpressionDuplicated(final ExpressionBase<?> expr,
			final List<StatementBase> statements, final Stack<ExpressionBase<?>> stack, AtomicInteger dupCounter) {
		final ExpressionBase<?> exprDuplicated;
		if (expr instanceof ExpressionDuplicate) {
			// There is a StatementExpressionDuplicated already.  
			stack.push(expr);
			exprDuplicated = expr;
		}
		else {
			final String dummyName = createTempName(dupCounter);
			final StatementExpressionDuplicated<?> stmtExprDuplicated = new StatementExpressionDuplicated<>(expr, dummyName);
			exprDuplicated = new ExpressionDuplicate<>(stmtExprDuplicated);
			statements.add(stmtExprDuplicated);
			stack.push(exprDuplicated);
		}
		return exprDuplicated;
	}

	/**
	 * Creates a name of a temporary variable.
	 * @param dupCounter counter
	 * @return name
	 */
	static String createTempName(AtomicInteger dupCounter) {
		return "__dup" + dupCounter.incrementAndGet();
	}

	/**
	 * Lookup a local variable of
	 * @param index of local variable (slot)
	 * @param locVars list of local variables
	 * @param instr instruction in the variable's frame or just before the frame
	 * @param mapInsnIndex map from instruction to instruction-index
	 * @return local variable, <code>null</code> if unknown (e.g. internal iterator in for-each-loop)
	 */
	static LocalVariableNode findLocalVariable(final int varIndex,
			final List<LocalVariableNode> locVars, final AbstractInsnNode instr,
			final Map<AbstractInsnNode, Integer> mapInsnIndex) {
		Integer idxInsn = mapInsnIndex.get(instr);
		Integer idxInsnNext = (instr.getNext() != null) ? mapInsnIndex.get(instr.getNext()) : null;
		if (idxInsn == null) {
			throw new SourceRuntimeException(String.format("Instruction (%s) is unknown in instruction-index-map", instr));
		}
		for (LocalVariableNode locVar : locVars) {
			if (varIndex != locVar.index) {
				continue;
			}
			final Integer idxStart = mapInsnIndex.get(locVar.start);
			final Integer idxEnd = mapInsnIndex.get(locVar.end);
			if (idxStart == null) {
				throw new SourceRuntimeException(String.format("Start-label-node (%s) of local-variable (%d / %s) is unknown",
						locVar.start, Integer.valueOf(locVar.index), locVar.name));
			}
			if (idxEnd == null) {
				throw new SourceRuntimeException(String.format("End-label-node (%s) of local-variable (%d / %s) is unknown",
						locVar.end, Integer.valueOf(locVar.index), locVar.name));
			}
			if (idxStart.intValue() <= idxInsn.intValue() && idxInsn.intValue() <= idxEnd.intValue()) {
				return locVar;
			}
			if (idxInsnNext != null && idxStart.intValue() <= idxInsnNext.intValue() && idxInsnNext.intValue() <= idxEnd.intValue()) {
				return locVar;
			}
		}
		return null;
	}

	/**
	 * Pops an expression from stack. In case of a duplicated expressions it may be merged
	 * with its statement-expression.
	 * This method is called before storing or using in a constructor.
	 * @param statements list of statements
	 * @param stack stack of expressions
	 * @return expression from stack
	 */
	private static ExpressionBase<?> popExpressionAndMergeDuplicate(final List<StatementBase> statements,
			final Stack<ExpressionBase<?>> stack) {
		final StatementBase stmtValue = stack.pop();
		ExpressionBase<?> exprValue = (ExpressionBase<?>) stmtValue;
		if (exprValue instanceof ExpressionDuplicate) {
			final ExpressionDuplicate<?> exprDupl = (ExpressionDuplicate<?>) exprValue;
			final StatementBase lastStmt = peekLastAddedStatement(statements);
			if (exprDupl.getStatementExpressionDuplicated() == lastStmt
					&& lastStmt instanceof StatementExpressionDuplicated<?>) {
				final StatementExpressionDuplicated<?> stmtDupl = (StatementExpressionDuplicated<?>) lastStmt;
				exprValue = stmtDupl.getExpression();
				popLastAddedStatement(statements);
			}
		}
		return exprValue;
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
	 * @return source-line added
	 * @throws IOException in case of an IO-error
	 */
	@Override
	protected SourceLine writeLine(final SourceLines lines, StringBuilder sb) throws IOException {
		return writeLine(lines, 0, sb);
	}

	/**
	 * Write a line and adds a line-break.
	 * The string-builder's length will be set to zero.
	 * @param lines current source-block
	 * @param sb content of the line (without line-break)
	 * @return source-line added
	 * @throws IOException in case of an IO-error
	 */
	private static SourceLine writeLine(final SourceLines lines, final int lineExpected, StringBuilder sb) throws IOException {
		final SourceLine sourceLine = lines.addLine(0, lineExpected, sb.toString());
		sb.setLength(0);
		return sourceLine;
	}

	/**
	 * Writes a list of source-lines, may respect expected line-numbers.
	 * @param bw writer
	 * @param sourceLines list of source-lines
	 * @param respectSourceLineNumbers respect line-numbers of in the class-file
	 * @param indentation optional indentation-string
	 * @param lineBreak line-break
	 * @param showLineNumbers <code>true</code> if line-numbers should be dumped
	 * @throws IOException in case of an IO-error
	 */
	public void writeLines(final Writer bw, final List<SourceLine> sourceLines,
			final String indentation, final String lineBreak,
			final boolean respectSourceLineNumbers, final boolean showLineNumbers) throws IOException {
		if (!respectSourceLineNumbers) {
			super.writeLines(bw, sourceLines, indentation, lineBreak);
			return;
		}
		int currentLine = 1;
		final StringBuilder sb = new StringBuilder(100);
		final int numLines = sourceLines.size();
		int lastPrintedLine = 0;
		for (int i = 0; i < numLines; i++) {
			final SourceLine sourceLine = sourceLines.get(i);
			final int lineExpected = sourceLine.getLineCurrent();
			int nextLineExpected = 0;
			if (i + 1 < numLines) {
				final SourceLine nextLine = sourceLines.get(i + 1);
				nextLineExpected = nextLine.getLineExpected();
			}
			while (lineExpected > 0 && currentLine < lineExpected) {
				bw.write(lineBreak);
				currentLine++;
			}
			if (showLineNumbers && sourceLine.getLineExpected() > 0 && lastPrintedLine != sourceLine.getLineExpected()) {
				sb.append("/* ").append(sourceLine.getLineExpected()).append(" */");
				lastPrintedLine = lineExpected;
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
			sb.append(sourceLine.getSourceLine());
			if (nextLineExpected == 0 || currentLine < nextLineExpected || currentLine > lineExpected) {
				sb.append(lineBreak);
				bw.write(sb.toString()); bw.flush();
				sb.setLength(0);
				currentLine++;
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

	/**
	 * Creates a class-reader.
	 * @param clazz class to be read
	 * @param cl class-loader to load the bytecode
	 * @return class-reader
	 */
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
				throw new SourceRuntimeException(String.format("Can't load inner-class (%s)", internalName), e);
			}
			final ClassReader innerReader = createClassReader(classInner, classLoader);
			final ClassNode innerClassNode = new ClassNode();
			innerReader.accept(innerClassNode, 0);
			return innerClassNode;
		});
		return innerClassProvider;
	}

	/**
	 * Checks if an expression evaluates to an integer-constant.
	 * @param expr expression
	 * @return integer-constant or <code>null</code>
	 */
	private static Integer getIntegerConstant(ExpressionBase<?> expr) {
		Integer iValue = null;
		if (expr instanceof ExpressionInstrZeroConstant) {
			ExpressionInstrZeroConstant exprConst = (ExpressionInstrZeroConstant) expr;
			if (exprConst.isIConst()) {
				iValue = (Integer) exprConst.getValue();
			}
		}
		else if (expr instanceof ExpressionInstrIntConstant) {
			final ExpressionInstrIntConstant exprInteger = (ExpressionInstrIntConstant) expr;
			iValue = Integer.valueOf(exprInteger.getIntValue());
		}
		return iValue;
	}

	/**
	 * Gets the last added non-label statement. The list of statements isn't modified.
	 * @param statements list of statements
	 * @return statement or <code>null</code>
	 */
	private static StatementBase peekLastAddedStatement(List<StatementBase> statements) {
		StatementBase lastStmt = null;
		final int numStmts = statements.size();
		int index = numStmts - 1;
		while (index >= 0) {
			lastStmt = statements.get(index);
			if (!(lastStmt instanceof StatementLabel)) {
				// We found a non-label statement.
				break;
			}
			index--;
		}
		return lastStmt;
	}

	/**
	 * Gets the last added non-label statement. The list of statements isn't modified.
	 * @param statements list of statements
	 * @param level 1 = last statement, 2 = statement before last statement, ...
	 * @param <code>true</code> if labels should be ignored
	 * @return statement or <code>null</code>
	 */
	private static StatementBase peekLastAddedStatement(List<StatementBase> statements,
			int level, boolean ignoreLabels) {
		StatementBase lastStmt = null;
		final int numStmts = statements.size();
		int index = numStmts - 1;
		int countStmt = 0;
		while (index >= 0) {
			lastStmt = statements.get(index);
			if (ignoreLabels && lastStmt instanceof StatementLabel) {
				index--;
				continue;
			}
			countStmt++;
			if (countStmt == level) {
				// We found a non-label statement.
				break;
			}
			index--;
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
			throw new SourceRuntimeException("The list of statements is empty, can't pop a statement.");
		}
		int index = numStmts - 1;
		while (index >= 0) {
			final StatementBase stmt = statements.get(index);
			if (!(stmt instanceof StatementLabel)) {
				// We found a non-label statement.
				return statements.remove(index);
			}
			index--;
		}
		throw new SourceRuntimeException("The list of statements is contains labels only, can't pop a statement.");
	}

}
