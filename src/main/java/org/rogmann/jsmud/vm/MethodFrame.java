package org.rogmann.jsmud.vm;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.visitors.SourceFileWriter;

/**
 * Frame of a method at execution time.
 * 
 * <p>This central class is used to execute the bytecode-instructions.</p>
 */
public class MethodFrame {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmInvocationHandlerReflection.class);

	/** <code>true</code>, if {@link AccessController} should be executed by underlying JVM (default is <code>false</code>) */
	static final boolean EXEC_ACCESS_CONTR_NATIVE = Boolean.getBoolean(MethodFrame.class.getName() + "executeAccessControllerNative"); 

	/** <code>true</code>, if {@link Thread}-classes should not be patched */
	static final boolean DONT_PATCH_THREAD_CLASSES = Boolean.getBoolean(MethodFrame.class.getName() + "dontPatchThreadClasses");

	/** maximum level of stacked method-references on an object-instance */
	final int maxCallSiteLevel = Integer.getInteger(MethodFrame.class.getName() + "maxCallSiteLevel", 20).intValue();

	/** <code>true</code> if a call-site should be simulated via proxy, <code>false</code> if a call-site should get a generated class */
	private final boolean callsiteViaProxy = Boolean.getBoolean(MethodFrame.class.getName() + ".executeAccessControllerNative");

	/** class-registry */
	public final ClassRegistry registry;

	/** class-loader of the method's class */
	public final Class<?> clazz;

	/** source-file-writer or <code>null</code> */
	private final SourceFileWriter sourceFileWriter;

	/** map from instruction-index to line-number in generated source (or <code>null</code>) */
	private final Map<Integer, Integer> sourceFileMapInstrLine;

	/** name of the method */
	private final String methodName;
	
	/** reflection-description of the method or constructor */
	private final Executable pMethod;
	
	/** ASM-description of the method */
	private final MethodNode method;

	/** type-descriptions of arguments */
	private final Type[] argDefs;

	/** instructions of the method */
	private final InsnList instructions;

	/** stack in the frame */
	private final OperandStack stack;
	
	/** local variables in the stack */
	private final Object[] aLocals;
	
	/** map from label to ASM-index */
	private final Map<Label, Integer> mapLabel;

	/** visitor */
	private final JvmExecutionVisitor visitor;

	/** invocation-handler used for modifying method-executions */
	private final JvmInvocationHandler invocationHandler;

	/** current instruction number */
	public int instrNum;
	/** current line number in source (if known) */
	private int currLineNum;

	/**
	 * Constructor
	 * @param registry class-registry
	 * @param pMethod reflection-description of the method
	 * @param method ASM-description of the method
	 * @param argDefs type-descriptions of arguments
	 * @param visitor JVM-visitor
	 * @param invocationHandler invocation-handler
	 */
	public MethodFrame(final ClassRegistry registry,
			final Executable pMethod, final MethodNode method, final Type[] argsDefs,
			final JvmExecutionVisitor visitor, final JvmInvocationHandler invocationHandler) {
		this.registry = registry;
		this.clazz = pMethod.getDeclaringClass();
		this.sourceFileWriter = registry.getSourceFileWriter(clazz);
		this.methodName = pMethod.getName();
		this.pMethod = pMethod;
		this.method = method;
		if (sourceFileWriter != null) {
			sourceFileMapInstrLine = sourceFileWriter.getMethodMapInstrLine(clazz, method);
			LOG.debug("line-map: " + sourceFileMapInstrLine);
		}
		else {
			sourceFileMapInstrLine = null;
		}
		this.argDefs = argsDefs;
		this.instructions = method.instructions;
		this.stack = new OperandStack(method.maxStack);
		this.aLocals = new Object[method.maxLocals];
		this.mapLabel = createLabelInstructionIndex(instructions);
		
		this.visitor = visitor;
		this.invocationHandler = invocationHandler;
	}
	
	/**
	 * Gets the frame's class.
	 * @return class
	 */
	public Class<?> getFrameClass() {
		return clazz;
	}
	
	/**
	 * Gets a array of the local-variables.
	 * @return locals
	 */
	public Object[] getLocals() {
		return aLocals;
	}
	
	/**
	 * Gets the frame's method.
	 * @return method
	 */
	public Executable getMethod() {
		return pMethod;
	}

	/**
	 * Gets the frame's method-node.
	 * @return method-node
	 */
	public MethodNode getMethodNode() {
		return method;
	}

	/**
	 * Gets the current line-number.
	 * @return line-number
	 */
	public int getCurrLineNum() {
		int lineNum;
		if (sourceFileMapInstrLine != null) {
			// In generated source-files the instructions are counted.
			final Integer iLineNum = sourceFileMapInstrLine.get(Integer.valueOf(instrNum));
			lineNum = (iLineNum != null) ? iLineNum.intValue() : 0;
		}
		else {
			lineNum = currLineNum;
		}
		return lineNum;
	}
	/**
	 * Converts an object from declared type into an integer -- if necessary.
	 * @param curType declared type
	 * @param stackValue object
	 * @return converted object
	 */
	static Object convertDeclTypeIntoJvmType(final Type curType, Object stackValue) {
		final Object objConv;
		if (Type.BOOLEAN_TYPE.equals(curType) && stackValue instanceof Boolean) {
			objConv = Integer.valueOf(((Boolean) stackValue).booleanValue() ? 1 : 0);
		}
		else if (Type.BYTE_TYPE.equals(curType) && stackValue instanceof Byte) {
			objConv = Integer.valueOf(((Byte) stackValue).byteValue());
		}
		else if (Type.CHAR_TYPE.equals(curType) && stackValue instanceof Character) {
			objConv = Integer.valueOf(((Character) stackValue).charValue());
		}
		else if (Type.SHORT_TYPE.equals(curType) && stackValue instanceof Short) {
			objConv = Integer.valueOf(((Short) stackValue).intValue());
		}
		else {
			objConv = stackValue;
		}
		return objConv;
	}

	/**
	 * Converts a field-value into an integer -- if necessary.
	 * @param fieldType field-type
	 * @param objField field-value
	 * @return converted object
	 */
	static Object convertFieldTypeIntoJvmType(final Class<?> fieldType, final Object objField) {
		final Object objConv;
		if (boolean.class.equals(fieldType) && objField instanceof Boolean) {
			final Boolean b = (Boolean) objField;
			objConv = Integer.valueOf(b.booleanValue() ? 1 : 0);
		}
		else if (byte.class.equals(fieldType) && objField instanceof Byte) {
			final Byte b = (Byte) objField;
			objConv = Integer.valueOf(b.intValue());
		}
		else if (char.class.equals(fieldType) && objField instanceof Character) {
			final Character c = (Character) objField;
			objConv = Integer.valueOf(c.charValue());
		}
		else if (short.class.equals(fieldType) && objField instanceof Short) {
			final Short s = (Short) objField;
			objConv = Integer.valueOf(s.intValue());
		}
		else {
			objConv = objField;
		}
		return objConv;
	}

	/**
	 * Converts an object into the declared type.
	 * 
	 * @param obj object
	 * @param typeDecl declared type
	 * @return converted object
	 */
	static Object convertJvmTypeIntoDeclType(Object obj, Type typeDecl) {
		final Object objConv;
		if (typeDecl == Type.BOOLEAN_TYPE && obj instanceof Integer) {
			final Integer bInt = (Integer) obj;
			objConv = Boolean.valueOf(bInt.intValue() != 0);
		}
		else if (typeDecl == Type.BYTE_TYPE && obj instanceof Integer) {
			final Integer bInt = (Integer) obj;
			objConv = Byte.valueOf(bInt.byteValue());
		}
		else if (typeDecl == Type.CHAR_TYPE && obj instanceof Integer) {
			final Integer bInt = (Integer) obj;
			objConv = Character.valueOf((char) bInt.intValue());
		}
		else if (typeDecl == Type.SHORT_TYPE && obj instanceof Integer) {
			final Integer bInt = (Integer) obj;
			objConv = Short.valueOf(bInt.shortValue());
		}
		else {
			objConv = obj;
		}
		return objConv;
	}

	/**
	 * Converts a (stack-)object into a field-object
	 * @param fieldType declared type of the field
	 * @param objValue value
	 * @return converted object
	 */
	static Object convertJvmTypeIntoFieldType(final Class<?> fieldType, final Object objValue) {
		final Object objField;
		if (objValue instanceof Integer && boolean.class.equals(fieldType)) {
			final int iValue = ((Integer) objValue).intValue();
			objField = Boolean.valueOf(iValue != 0);
		}
		else if (objValue instanceof Integer && byte.class.equals(fieldType)) {
			final byte bValue = ((Integer) objValue).byteValue();
			objField = Byte.valueOf(bValue);
		}
		else if (objValue instanceof Integer && char.class.equals(fieldType)) {
			final int iValue = ((Integer) objValue).intValue();
			objField = Character.valueOf((char) iValue);
		}
		else if (objValue instanceof Integer && short.class.equals(fieldType)) {
			final short sValue = ((Integer) objValue).shortValue();
			objField = Short.valueOf(sValue);
		}
		else {
			objField = objValue;
		}
		return objField;
	}

	/**
	 * Executes the method.
	 * <p>This rather monolithic method contains the execution of the different instructions.</p>  
	 * @param args arguments on caller's stack
	 * @return result or <code>null</code>
	 */
	public Object execute(final OperandStack args) throws Throwable {
		stack.clear();
		readArgsIntoLocals(args);

		instrNum = 0;
		currLineNum = 0;

		/** method-return-type (as delivered by method) */
		Object methodReturnObj = null;
		/** JVM-return-type (as in stack) */ 
		Object methodReturnObjJvm = null;
		visitor.visitMethodEnter(clazz, pMethod, this);
		try {

whileInstr:
			while (true) {
				final AbstractInsnNode instr = instructions.get(instrNum);
				final int opcode = instr.getOpcode();
				visitor.visitInstruction(instr, stack, aLocals);
				
				switch (opcode) {
				case Opcodes.NOP: // 0x00
					break;
				case Opcodes.ACONST_NULL: // 0x01
					stack.push(null);
					break;
				case Opcodes.ICONST_M1: // 0x02
					stack.push(Integer.valueOf(-1));
					break;
				case Opcodes.ICONST_0: // 0x03
					stack.push(Integer.valueOf(0));
					break;
				case Opcodes.ICONST_1: // 0x04
					stack.push(Integer.valueOf(1));
					break;
				case Opcodes.ICONST_2: // 0x05
					stack.push(Integer.valueOf(2));
					break;
				case Opcodes.ICONST_3: // 0x06
					stack.push(Integer.valueOf(3));
					break;
				case Opcodes.ICONST_4: // 0x07
					stack.push(Integer.valueOf(4));
					break;
				case Opcodes.ICONST_5: // 0x08
					stack.push(Integer.valueOf(5));
					break;
				case Opcodes.LCONST_0: // 0x09
					stack.push(Long.valueOf(0));
					break;
				case Opcodes.LCONST_1: // 0x0a
					stack.push(Long.valueOf(1));
					break;
				case Opcodes.FCONST_0: // 0x0b
					stack.push(Float.valueOf(0f));
					break;
				case Opcodes.FCONST_1: // 0x0c
					stack.push(Float.valueOf(1f));
					break;
				case Opcodes.FCONST_2: // 0x0d
					stack.push(Float.valueOf(2f));
					break;
				case Opcodes.DCONST_0: // 0x0e
					stack.push(Double.valueOf(0f));
					break;
				case Opcodes.DCONST_1: // 0x0f
					stack.push(Double.valueOf(1f));
					break;
				case Opcodes.BIPUSH: // 0x10
				{
					final IntInsnNode ii = (IntInsnNode) instr;
					final byte b = (byte) ii.operand;
					stack.push(Integer.valueOf(b));
					break;
				}
				case Opcodes.SIPUSH: // 0x11
				{
					final IntInsnNode ii = (IntInsnNode) instr;
					final short b = (short) ii.operand;
					stack.push(Integer.valueOf(b));
					break;
				}
				case Opcodes.LDC: // 0x12
				{
					final LdcInsnNode li = (LdcInsnNode) instr;
					Object obj = li.cst;
					if (li.cst instanceof Type) {
						final Type type = (Type) li.cst;
						if (type.getSort() == Type.ARRAY) {
							final Class<?> classArray = getClassArrayViaType(type, registry, clazz);
							obj = classArray;
						}
						else {
							final Class<?> liClass = registry.loadClass(type.getClassName(), clazz);
							obj = liClass;
						}
					}
					stack.push(obj);
					break;
				}
				case 0x13: // 0x13, via LDC in ASM
					throw new JvmException("Opcode 0x13 (Opcode_13) not yet supported, expected LDC.");
				case 0x14: // 0x14, via LDC in ASM
					throw new JvmException("Opcode 0x14 (Opcode_14) not yet supported, expected LDC.");
				case Opcodes.ILOAD: // 0x15
					stack.push(aLocals[((VarInsnNode) instr).var]);
					break;
				case Opcodes.LLOAD: // 0x16
					stack.push(aLocals[((VarInsnNode) instr).var]);
					break;
				case Opcodes.FLOAD: // 0x17
					stack.push(aLocals[((VarInsnNode) instr).var]);
					break;
				case Opcodes.DLOAD: // 0x18
					stack.push(aLocals[((VarInsnNode) instr).var]);
					break;
				case Opcodes.ALOAD: // 0x19
					stack.push(aLocals[((VarInsnNode) instr).var]);
					break;
				case 0x1a: // 0x1a, via ILOAD in ASM
					throw new JvmException("Opcode 0x1a (Opcode_1a) not yet supported, expected ILOAD.");
				case 0x1b: // 0x1b, via ILOAD in ASM
					throw new JvmException("Opcode 0x1b (Opcode_1b) not yet supported, expected ILOAD.");
				case 0x1c: // 0x1c, via ILOAD in ASM
					throw new JvmException("Opcode 0x1c (Opcode_1c) not yet supported, expected ILOAD.");
				case 0x1d: // 0x1d, via ILOAD in ASM
					throw new JvmException("Opcode 0x1d (Opcode_1d) not yet supported, expected ILOAD.");
				case 0x1e: // 0x1e, via LLOAD in ASM
					throw new JvmException("Opcode 0x1e (Opcode_1e) not yet supported, expected LLOAD.");
				case 0x1f: // 0x1f, via LLOAD in ASM
					throw new JvmException("Opcode 0x1f (Opcode_1f) not yet supported, expected LLOAD.");
				case 0x20: // 0x20, via LLOAD in ASM
					throw new JvmException("Opcode 0x20 (Opcode_20) not yet supported, expected LLOAD.");
				case 0x21: // 0x21, via LLOAD in ASM
					throw new JvmException("Opcode 0x21 (Opcode_21) not yet supported, expected LLOAD.");
				case 0x22: // 0x22, via FLOAD in ASM
					throw new JvmException("Opcode 0x22 (Opcode_22) not yet supported, expected FLOAD.");
				case 0x23: // 0x23, via FLOAD in ASM
					throw new JvmException("Opcode 0x23 (Opcode_23) not yet supported, expected FLOAD.");
				case 0x24: // 0x24, via FLOAD in ASM
					throw new JvmException("Opcode 0x24 (Opcode_24) not yet supported, expected FLOAD.");
				case 0x25: // 0x25, via FLOAD in ASM
					throw new JvmException("Opcode 0x25 (Opcode_25) not yet supported, expected FLOAD.");
				case 0x26: // 0x26, via DLOAD in ASM
					throw new JvmException("Opcode 0x26 (Opcode_26) not yet supported, expected DLOAD.");
				case 0x27: // 0x27, via DLOAD in ASM
					throw new JvmException("Opcode 0x27 (Opcode_27) not yet supported, expected DLOAD.");
				case 0x28: // 0x28, via DLOAD in ASM
					throw new JvmException("Opcode 0x28 (Opcode_28) not yet supported, expected DLOAD.");
				case 0x29: // 0x29, via DLOAD in ASM
					throw new JvmException("Opcode 0x29 (Opcode_29) not yet supported, expected DLOAD.");
				case 0x2a: // 0x2a, via ALOAD in ASM
					throw new JvmException("Opcode 0x2a (Opcode_2a) not yet supported, expected ALOAD.");
				case 0x2b: // 0x2b, via ALOAD in ASM
					throw new JvmException("Opcode 0x2b (Opcode_2b) not yet supported, expected ALOAD.");
				case 0x2c: // 0x2c, via ALOAD in ASM
					throw new JvmException("Opcode 0x2c (Opcode_2c) not yet supported, expected ALOAD.");
				case 0x2d: // 0x2d, via ALOAD in ASM
					throw new JvmException("Opcode 0x2d (Opcode_2d) not yet supported, expected ALOAD.");
				case Opcodes.IALOAD: // 0x2e
					{
						final int index = ((Integer) stack.pop()).intValue();
						final int[] aPrimitives = (int[]) stack.pop();
						try {
							stack.push(Integer.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("IALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.LALOAD: // 0x2f
					{
						final int index = ((Integer) stack.pop()).intValue();
						final long[] aPrimitives = (long[]) stack.pop();
						try {
							stack.push(Long.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("LALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.FALOAD: // 0x30
					{
						final int index = ((Integer) stack.pop()).intValue();
						final float[] aPrimitives = (float[]) stack.pop();
						try {
							stack.push(Float.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("FALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.DALOAD: // 0x31
					{
						final int index = ((Integer) stack.pop()).intValue();
						final double[] aPrimitives = (double[]) stack.pop();
						try {
							stack.push(Double.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("DALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.AALOAD: // 0x32
					{
						final int index = ((Integer) stack.pop()).intValue();
						final Object[] aRefs = (Object[]) stack.pop();
						try {
							stack.push(aRefs[index]);
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("AALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.BALOAD: // 0x33
					{
						final int index = ((Integer) stack.pop()).intValue();
						final Object arrB = stack.pop();
						try {
							if (arrB instanceof boolean[]) {
								final boolean[] aPrimitives = (boolean[]) arrB;
								final boolean v = aPrimitives[index];
								stack.push(Integer.valueOf(v ? 1 : 0));
							}
							else {
								final byte[] aPrimitives = (byte[]) arrB;
								// The byte value will be sign-extended to an int value.
								final int v = aPrimitives[index];
								stack.push(Integer.valueOf(v));
							}
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("BALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.CALOAD: // 0x34
					{
						final int index = ((Integer) stack.pop()).intValue();
						final char[] aPrimitives = (char[]) stack.pop();
						try {
							stack.push(Integer.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("CALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.SALOAD: // 0x35
					{
						final int index = ((Integer) stack.pop()).intValue();
						final short[] aPrimitives = (short[]) stack.pop();
						try {
							stack.push(Integer.valueOf(aPrimitives[index]));
						}
						catch (ArrayIndexOutOfBoundsException e) {
							final boolean doContinueWhile = handleCatchException(e);
							if (doContinueWhile) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("SALOAD-AIOOBE", e);
						}
					}
					break;
				case Opcodes.ISTORE: // 0x36
					aLocals[((VarInsnNode) instr).var] = stack.pop();
					break;
				case Opcodes.LSTORE: // 0x37
					aLocals[((VarInsnNode) instr).var] = stack.pop();
					break;
				case Opcodes.FSTORE: // 0x38
					aLocals[((VarInsnNode) instr).var] = stack.pop();
					break;
				case Opcodes.DSTORE: // 0x39
					aLocals[((VarInsnNode) instr).var] = stack.pop();
					break;
				case Opcodes.ASTORE: // 0x3a
					aLocals[((VarInsnNode) instr).var] = stack.pop();
					break;
				case 0x3b: // 0x3b, via ISTORE in ASM
					throw new JvmException("Opcode 0x3b (Opcode_3b) not yet supported, expected ISTORE.");
				case 0x3c: // 0x3c, via ISTORE in ASM
					throw new JvmException("Opcode 0x3c (Opcode_3c) not yet supported, expected ISTORE.");
				case 0x3d: // 0x3d, via ISTORE in ASM
					throw new JvmException("Opcode 0x3d (Opcode_3d) not yet supported, expected ISTORE.");
				case 0x3e: // 0x3e, via ISTORE in ASM
					throw new JvmException("Opcode 0x3e (Opcode_3e) not yet supported, expected ISTORE.");
				case 0x3f: // 0x3f, via LSTORE in ASM
					throw new JvmException("Opcode 0x3f (Opcode_3f) not yet supported, expected LSTORE.");
				case 0x40: // 0x40, via LSTORE in ASM
					throw new JvmException("Opcode 0x40 (Opcode_40) not yet supported, expected LSTORE.");
				case 0x41: // 0x41, via LSTORE in ASM
					throw new JvmException("Opcode 0x41 (Opcode_41) not yet supported, expected LSTORE.");
				case 0x42: // 0x42, via LSTORE in ASM
					throw new JvmException("Opcode 0x42 (Opcode_42) not yet supported, expected LSTORE.");
				case 0x43: // 0x43, via FSTORE in ASM
					throw new JvmException("Opcode 0x43 (Opcode_43) not yet supported, expected FSTORE.");
				case 0x44: // 0x44, via FSTORE in ASM
					throw new JvmException("Opcode 0x44 (Opcode_44) not yet supported, expected FSTORE.");
				case 0x45: // 0x45, via FSTORE in ASM
					throw new JvmException("Opcode 0x45 (Opcode_45) not yet supported, expected FSTORE.");
				case 0x46: // 0x46, via FSTORE in ASM
					throw new JvmException("Opcode 0x46 (Opcode_46) not yet supported, expected FSTORE.");
				case 0x47: // 0x47, via DSTORE in ASM
					throw new JvmException("Opcode 0x47 (Opcode_47) not yet supported, expected DSTORE.");
				case 0x48: // 0x48, via DSTORE in ASM
					throw new JvmException("Opcode 0x48 (Opcode_48) not yet supported, expected DSTORE.");
				case 0x49: // 0x49, via DSTORE in ASM
					throw new JvmException("Opcode 0x49 (Opcode_49) not yet supported, expected DSTORE.");
				case 0x4a: // 0x4a, via DSTORE in ASM
					throw new JvmException("Opcode 0x4a (Opcode_4a) not yet supported, expected DSTORE.");
				case 0x4b: // 0x4b, via ASTORE in ASM
					throw new JvmException("Opcode 0x4b (Opcode_4b) not yet supported, expected ASTORE.");
				case 0x4c: // 0x4c, via ASTORE in ASM
					throw new JvmException("Opcode 0x4c (Opcode_4c) not yet supported, expected ASTORE.");
				case 0x4d: // 0x4d, via ASTORE in ASM
					throw new JvmException("Opcode 0x4d (Opcode_4d) not yet supported, expected ASTORE.");
				case 0x4e: // 0x4e, via ASTORE in ASM
					throw new JvmException("Opcode 0x4e (Opcode_4e) not yet supported, expected ASTORE.");
				case Opcodes.IASTORE: // 0x4f
					{
						final int value = ((Integer) stack.pop()).intValue();
						final int index = ((Integer) stack.pop()).intValue();
						final int[] aPrimitives = (int[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.LASTORE: // 0x50
					{
						final long value = ((Long) stack.pop()).longValue();
						final int index = ((Integer) stack.pop()).intValue();
						final long[] aPrimitives = (long[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.FASTORE: // 0x51
					{
						final float value = ((Float) stack.pop()).floatValue();
						final int index = ((Integer) stack.pop()).intValue();
						final float[] aPrimitives = (float[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.DASTORE: // 0x52
					{
						final double value = ((Double) stack.pop()).doubleValue();
						final int index = ((Integer) stack.pop()).intValue();
						final double[] aPrimitives = (double[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.AASTORE: // 0x53
					{
						final Object value = stack.pop();
						final int index = ((Integer) stack.pop()).intValue();
						final Object[] aRefs = (Object[]) stack.pop();
						aRefs[index] = value;
					}
					break;
				case Opcodes.BASTORE: // 0x54
					{
						final Object oVal = stack.pop();
						final int index = ((Integer) stack.pop()).intValue();
						final Object oArr = stack.pop();
						if (oArr instanceof boolean[]) {
							final boolean[] aPrimitives = (boolean[]) oArr;
							final int bVal = ((Integer) oVal).intValue() & 1;
							aPrimitives[index] = (bVal == 0) ? false : true;
						}
						else {
							assert oArr instanceof byte[];
							final byte[] aPrimitives = (byte[]) oArr;
							final byte bVal = ((Integer) oVal).byteValue();
							aPrimitives[index] = bVal;
						}
					}
					break;
				case Opcodes.CASTORE: // 0x55
					{
						final char value = (char) ((Integer) stack.pop()).intValue();
						final int index = ((Integer) stack.pop()).intValue();
						final char[] aPrimitives = (char[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.SASTORE: // 0x56
					{
						final short value = ((Integer) stack.pop()).shortValue();
						final int index = ((Integer) stack.pop()).intValue();
						final short[] aPrimitives = (short[]) stack.pop();
						aPrimitives[index] = value;
					}
					break;
				case Opcodes.POP: // 0x57
					stack.pop();
					break;
				case Opcodes.POP2: // 0x58
					final Object oStack = stack.pop();
					if (!(oStack instanceof Long || oStack instanceof Double)) {
						stack.pop();
					}
					break;
				case Opcodes.DUP: // 0x59
					stack.push(stack.peek());
					break;
				case Opcodes.DUP_X1: // 0x5a
				{
					final Object o1 = stack.pop();
					final Object o2 = stack.pop();
					stack.push(o1);
					stack.push(o2);
					stack.push(o1);
					break;
				}
				case Opcodes.DUP_X2: // 0x5b
				{
					final Object o1 = stack.pop();
					final Object o2 = stack.pop();
					if (o2 instanceof Long || o2 instanceof Double) {
						// Form 2: o2 is of category 2 computational type.
						stack.push(o1);
					}
					else {
						final Object o3 = stack.pop();
						stack.push(o1);
						stack.push(o3);
					}
					stack.push(o2);
					stack.push(o1);
					break;
				}
				case Opcodes.DUP2: // 0x5c
				{
					final Object o1 = stack.peek();
					if (o1 instanceof Long || o1 instanceof Double) {
						stack.push(o1);
					}
					else {
						final Object o2 = stack.peek(1);
						stack.push(o2);
						stack.push(o1);
					}
					break;
				}
				case Opcodes.DUP2_X1: // 0x5d
				{
					final Object o1 = stack.pop();
					final Object o2 = stack.pop();
					if (o1 instanceof Long || o1 instanceof Double) {
						// Form 2: o1 is of category 2 computational type.
						stack.push(o1);
					}
					else {
						final Object o3 = stack.pop();
						stack.push(o2);
						stack.push(o1);
						stack.push(o3);
					}
					stack.push(o2);
					stack.push(o1);
					break;
				}
				case Opcodes.DUP2_X2: // 0x5e
				{
					final Object o1 = stack.pop();
					final Object o2 = stack.pop();
					if (o1 instanceof Long || o1 instanceof Double) {
						// o1 is of category 2 computational type.
						if (o2 instanceof Long || o2 instanceof Double) {
							// Form 4: o2 is of category 2 computational type.
							stack.push(o1);
						}
						else {
							// Form 2
							final Object o3 = stack.pop();
							stack.push(o1);
							stack.push(o3);
						}
					}
					else {
						final Object o3 = stack.pop();
						if (o3 instanceof Long || o3 instanceof Double) {
							// Form 3: o3 ist of category 2 computational type.
							stack.push(o2);
							stack.push(o1);
						}
						else {
							// Form 1
							final Object o4 = stack.pop();
							stack.push(o2);
							stack.push(o1);
							stack.push(o4);
						}
						stack.push(o3);
					}
					stack.push(o2);
					stack.push(o1);
					break;
				}
				case Opcodes.SWAP: // 0x5f
				{
					Object o1 = stack.pop();
					Object o2 = stack.pop();
					stack.push(o1);
					stack.push(o2);
					break;
				}
				case Opcodes.IADD: // 0x60
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a + b));
					break;
				}
				case Opcodes.LADD: // 0x61
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a + b));
					break;
				}
				case Opcodes.FADD: // 0x62
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(a + b));
					break;
				}
				case Opcodes.DADD: // 0x63
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(a + b));
					break;
				}
				case Opcodes.ISUB: // 0x64
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a - b));
				}
				break;
				case Opcodes.LSUB: // 0x65
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a - b));
					break;
				}
				case Opcodes.FSUB: // 0x66
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(a - b));
					break;
				}
				case Opcodes.DSUB: // 0x67
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(a - b));
					break;
				}
				case Opcodes.IMUL: // 0x68
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a * b));
					break;
				}
				case Opcodes.LMUL: // 0x69
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a * b));
					break;
				}
				case Opcodes.FMUL: // 0x6a
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(a * b));
					break;
				}
				case Opcodes.DMUL: // 0x6b
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(a * b));
					break;
				}
				case Opcodes.IDIV: // 0x6c
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a / b));
					break;
				}
				case Opcodes.LDIV: // 0x6d
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a / b));
					break;
				}
				case Opcodes.FDIV: // 0x6e
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(a / b));
					break;
				}
				case Opcodes.DDIV: // 0x6f
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(a / b));
					break;
				}
				case Opcodes.IREM: // 0x70
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a % b));
					break;
				}
				case Opcodes.LREM: // 0x71
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a % b));
					break;
				}
				case Opcodes.FREM: // 0x72
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(a % b));
					break;
				}
				case Opcodes.DREM: // 0x73
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(a % b));
					break;
				}
				case Opcodes.INEG: // 0x74
				{
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(-a));
					break;
				}
				case Opcodes.LNEG: // 0x75
				{
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(-a));
					break;
				}
				case Opcodes.FNEG: // 0x76
				{
					final float a = ((Float) stack.pop()).floatValue();
					stack.push(Float.valueOf(-a));
					break;
				}
				case Opcodes.DNEG: // 0x77
				{
					final double a = ((Double) stack.pop()).doubleValue();
					stack.push(Double.valueOf(-a));
					break;
				}
				case Opcodes.ISHL: // 0x78
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a << b));
					break;
				}
				case Opcodes.LSHL: // 0x79
				{
					final int b = ((Integer) stack.pop()).intValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a << b));
					break;
				}
				case Opcodes.ISHR: // 0x7a
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a >> b));
					break;
				}
				case Opcodes.LSHR: // 0x7b
				{
					final int b = ((Integer) stack.pop()).intValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a >> b));
					break;
				}
				case Opcodes.IUSHR: // 0x7c
				{
					final int b = ((Integer) stack.pop()).intValue();
					final int a = ((Integer) stack.pop()).intValue();
					stack.push(Integer.valueOf(a >>> b));
					break;
				}
				case Opcodes.LUSHR: // 0x7d
				{
					final int b = ((Integer) stack.pop()).intValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a >>> b));
					break;
				}
				case Opcodes.IAND: // 0x7e
				{
					final Object ob = stack.pop();
					final Object oa = stack.pop();
					final int b = ((Integer) ob).intValue();
					final int a = ((Integer) oa).intValue();
					stack.push(Integer.valueOf(a & b));
					break;
				}
				case Opcodes.LAND: // 0x7f
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a & b));
					break;
				}
				case Opcodes.IOR: // 0x80
				{
					final Object ob = stack.pop();
					final Object oa = stack.pop();
					final int b = ((Integer) ob).intValue();
					final int a = ((Integer) oa).intValue();
					stack.push(Integer.valueOf(a | b));
					break;
				}
				case Opcodes.LOR: // 0x81
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a | b));
					break;
				}
				case Opcodes.IXOR: // 0x82
				{
					final Object ob = stack.pop();
					final Object oa = stack.pop();
					final int b = ((Integer) ob).intValue();
					final int a = ((Integer) oa).intValue();
					stack.push(Integer.valueOf(a ^ b));
					break;
				}
				case Opcodes.LXOR: // 0x83
				{
					final long b = ((Long) stack.pop()).longValue();
					final long a = ((Long) stack.pop()).longValue();
					stack.push(Long.valueOf(a ^ b));
					break;
				}
				case Opcodes.IINC: // 0x84
					{
						final IincInsnNode ii = (IincInsnNode) instr;
						final int c = ii.incr;
						final int index = ii.var;
						aLocals[index] = Integer.valueOf(((Integer) aLocals[index]).intValue() + c);
					}
					break;
				case Opcodes.I2L: // 0x85
					stack.push(Long.valueOf(((Integer) stack.pop()).longValue()) );
					break;
				case Opcodes.I2F: // 0x86
					stack.push(Float.valueOf(((Integer) stack.pop()).floatValue()) );
					break;
				case Opcodes.I2D: // 0x87
					stack.push(Double.valueOf(((Integer) stack.pop()).doubleValue()) );
					break;
				case Opcodes.L2I: // 0x88
					stack.push(Integer.valueOf(((Long) stack.pop()).intValue()) );
					break;
				case Opcodes.L2F: // 0x89
					stack.push(Float.valueOf(((Long) stack.pop()).floatValue()) );
					break;
				case Opcodes.L2D: // 0x8a
					stack.push(Double.valueOf(((Long) stack.pop()).doubleValue()) );
					break;
				case Opcodes.F2I: // 0x8b
					stack.push(Integer.valueOf(((Float) stack.pop()).intValue()) );
					break;
				case Opcodes.F2L: // 0x8c
					stack.push(Long.valueOf(((Float) stack.pop()).longValue()) );
					break;
				case Opcodes.F2D: // 0x8d
					stack.push(Double.valueOf(((Float) stack.pop()).doubleValue()) );
					break;
				case Opcodes.D2I: // 0x8e
					stack.push(Integer.valueOf(((Double) stack.pop()).intValue()) );
					break;
				case Opcodes.D2L: // 0x8f
					stack.push(Long.valueOf(((Double) stack.pop()).longValue()) );
					break;
				case Opcodes.D2F: // 0x90
					stack.push(Float.valueOf(((Double) stack.pop()).floatValue()) );
					break;
				case Opcodes.I2B: // 0x91
					stack.push(Integer.valueOf(((Integer) stack.pop()).byteValue()) );
					break;
				case Opcodes.I2C: // 0x92
					stack.push(Integer.valueOf((char) ((Integer) stack.pop()).intValue()) );
					break;
				case Opcodes.I2S: // 0x93
					// truncation of int into short and sign-extension to an int-result.
					stack.push(Integer.valueOf(((Integer) stack.pop()).shortValue()) );
					break;
				case Opcodes.LCMP: // 0x94
				{
					final long l2 = ((Long) stack.pop()).longValue();
					final long l1 = ((Long) stack.pop()).longValue();
					final int sgn = (l1 == l2) ? 0 : ((l1 > l2) ? 1 : -1);
					stack.push(Integer.valueOf(sgn));
					break;
				}
				case Opcodes.FCMPL: // 0x95
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					final int res;
					if (a == Float.NaN || b == Float.NaN) {
						res = -1;
					}
					else {
						res = Float.compare(a, b);
					}
					stack.push(Integer.valueOf(res));
					break;
				}
				case Opcodes.FCMPG: // 0x96
				{
					final float b = ((Float) stack.pop()).floatValue();
					final float a = ((Float) stack.pop()).floatValue();
					final int res;
					if (a == Float.NaN || b == Float.NaN) {
						res = 1;
					}
					else {
						res = Float.compare(a, b);
					}
					stack.push(Integer.valueOf(res));
					break;
				}
				case Opcodes.DCMPL: // 0x97
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					final int res;
					if (a == Double.NaN || b == Double.NaN) {
						res = -1;
					}
					else {
						res = Double.compare(a, b);
					}
					stack.push(Integer.valueOf(res));
					break;
				}
				case Opcodes.DCMPG: // 0x98
				{
					final double b = ((Double) stack.pop()).doubleValue();
					final double a = ((Double) stack.pop()).doubleValue();
					final int res;
					if (a == Double.NaN || b == Double.NaN) {
						res = +1;
					}
					else {
						res = Double.compare(a, b);
					}
					stack.push(Integer.valueOf(res));
					break;
				}
				case Opcodes.IFEQ: // 0x99
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v == 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IFNE: // 0x9a
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v != 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IFLT: // 0x9b
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v < 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IFGE: // 0x9c
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v >= 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IFGT: // 0x9d
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v > 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IFLE: // 0x9e
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object oValue = stack.pop();
					final int v = ((Integer) oValue).intValue();
					if (v <= 0) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
	
					break;
				}
				case Opcodes.IF_ICMPEQ: // 0x9f
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 == v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
		
						break;
					}
				case Opcodes.IF_ICMPNE: // 0xa0
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 != v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ICMPLT: // 0xa1
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 < v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ICMPGE: // 0xa2
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 >= v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ICMPGT: // 0xa3
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 > v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ICMPLE: // 0xa4
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
		
						final int v2 = getIntBooleanAsInt(stack);
						final int v1 = getIntBooleanAsInt(stack);
						if (v1 <= v2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ACMPEQ: // 0xa5
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
						
						final Object oValue1 = stack.pop();
						final Object oValue2 = stack.pop();
						if (oValue1 == oValue2) {
							instrNum = instrLabel.intValue();
							continue whileInstr;
						}
						break;
					}
				case Opcodes.IF_ACMPNE: // 0xa6
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
					
					final Object oValue1 = stack.pop();
					final Object oValue2 = stack.pop();
					if (oValue1 != oValue2) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
					break;
				}
				case Opcodes.GOTO: // 0xa7
					{
						final JumpInsnNode ji = (JumpInsnNode) instr;
						final Label labelDest = ji.label.getLabel();
						final Integer instrLabel = mapLabel.get(labelDest);
						assert instrLabel != null : "unknown label " + labelDest;
						
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
				case Opcodes.JSR: // 0xa8
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final JvmReturnAddress returnAddress = new JvmReturnAddress(instrNum + 1);
					stack.push(returnAddress);

					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
					
					instrNum = instrLabel.intValue();
					continue whileInstr;
				}
				case Opcodes.RET: // 0xa9
				{
					final VarInsnNode vi = (VarInsnNode) instr;
					final JvmReturnAddress returnAddress;
					final Object oReturnAddress;
					try {
						oReturnAddress = aLocals[vi.var];
					} catch (ArrayIndexOutOfBoundsException e) {
						throw new JvmException(String.format("Unexpected local-var-index (%d) in RET-instruction, #local=%d",
								Integer.valueOf(vi.var), Integer.valueOf(aLocals.length)));
					}
					try {
						returnAddress = (JvmReturnAddress) oReturnAddress;
					} catch (ClassCastException e) {
						throw new JvmException(String.format("Unexpected value (%s) instead of returnAddress in RET %d-instruction",
								oReturnAddress, Integer.valueOf(vi.var)));
					}
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("RET %d: jump to instr 0x%02x",
								Integer.valueOf(vi.var), Integer.valueOf(returnAddress.getAddress())));
					}
					instrNum = returnAddress.getAddress();
					continue whileInstr;
				}
				case Opcodes.TABLESWITCH: // 0xaa
				{
					final TableSwitchInsnNode tsi = (TableSwitchInsnNode) instr;
					final int lMin = tsi.min;
					final int lMax = tsi.max;
					final int idx = ((Integer) stack.pop()).intValue();
					final Label labelDest;
					if (lMin <= idx && idx <= lMax) {
						labelDest = tsi.labels.get(idx - lMin).getLabel();
					}
					else {
						labelDest = tsi.dflt.getLabel();
					}
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
					
					instrNum = instrLabel.intValue();
					continue whileInstr;
				}
				case Opcodes.LOOKUPSWITCH: // 0xab
				{
					final int idx = ((Integer) stack.pop()).intValue();
					final LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) instr;
					final List<Integer> keys = lsi.keys;
					final List<LabelNode> labels = lsi.labels;
					final int numCases = keys.size();
					Label labelDest = lsi.dflt.getLabel();
					for (int i = 0; i < numCases; i++) {
						if (keys.get(i).intValue() == idx) {
							labelDest = labels.get(i).getLabel();
							break;
						}
					}
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
					
					instrNum = instrLabel.intValue();
					continue whileInstr;
				}
				case Opcodes.IRETURN: // 0xac
					methodReturnObjJvm = stack.pop();
					stack.clear();
					break whileInstr;
				case Opcodes.LRETURN: // 0xad
					methodReturnObjJvm = stack.pop();
					stack.clear();
					break whileInstr;
				case Opcodes.FRETURN: // 0xae
					methodReturnObjJvm = stack.pop();
					stack.clear();
					break whileInstr;
				case Opcodes.DRETURN: // 0xaf
					methodReturnObjJvm = stack.pop();
					stack.clear();
					break whileInstr;
				case Opcodes.ARETURN: // 0xb0
					methodReturnObjJvm = stack.pop();
					stack.clear();
					break whileInstr;
				case Opcodes.RETURN: // 0xb1
					stack.clear();
					break whileInstr;
				case Opcodes.GETSTATIC: // 0xb2
				{
					final FieldInsnNode fi = (FieldInsnNode) instr;
					final String nameFiOwner = fi.owner.replace('/', '.');
					Object objField;
					if ("java.lang.System".equals(nameFiOwner) && "security".equals(fi.name)) {
						objField = System.getSecurityManager();
					}
					else if (CallSiteGenerator.FIELD_IS_EXECUTED_BY_JSMUD.equals(fi.name) && "Z".equals(fi.desc)) {
						// JSMUD-internal field: class is executed by JSMUD.
						objField = Integer.valueOf(1);
					}
					else {
						try {
							final Class<?> classFieldOwner = registry.loadClass(nameFiOwner, clazz);
							final Field field = findDeclaredField(classFieldOwner, fi);
							field.setAccessible(true);
							objField = field.get(classFieldOwner);
							objField = visitor.visitFieldAccess(opcode, classFieldOwner, field, objField);
							objField = convertFieldTypeIntoJvmType(field.getType(), objField);
						} catch (ClassNotFoundException | NoSuchFieldException | SecurityException
								| IllegalArgumentException | IllegalAccessException e) {
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								continue whileInstr;
							}
							throw new JvmUncaughtException(String.format("Error while reading field (%s) of (%s) in method (%s) of class (%s) in class-loader (%s)",
									fi.name, nameFiOwner, methodName, clazz, clazz.getClassLoader()), e);
						}
					}
					stack.push(objField);
					break;
				}
				case Opcodes.PUTSTATIC: // 0xb3
				{
					final FieldInsnNode fi = (FieldInsnNode) instr;
					final String nameFiOwner = fi.owner.replace('/', '.');
					try {
						final Class<?> classFieldOwner = registry.loadClass(nameFiOwner, clazz);
						final Field field = findDeclaredField(classFieldOwner, fi);
						field.setAccessible(true);
						if (Modifier.isFinal(field.getModifiers())
								&& JsmudClassLoader.InitializerAdapter.METHOD_JSMUD_CLINIT.equals(methodName)) {
							// We want to set a final field while executing a constructor.
							final Field fieldMods = Field.class.getDeclaredField("modifiers");
							fieldMods.setAccessible(true);
							final Integer modifiers = (Integer) fieldMods.get(field);
							fieldMods.set(field, Integer.valueOf(modifiers.intValue() & ~Modifier.FINAL));
						}
						final Object vFieldStack = stack.pop();
						final Class<?> fieldType = field.getType();
						Object vField = convertJvmTypeIntoFieldType(fieldType, vFieldStack);
						vField = visitor.visitFieldAccess(opcode, classFieldOwner, field, vField);
						field.set(classFieldOwner, vField);
					} catch (ClassNotFoundException | NoSuchFieldException | SecurityException
							| IllegalArgumentException | IllegalAccessException e) {
						final boolean doContinueWhileE = handleCatchException(e);
						if (doContinueWhileE) {
							continue whileInstr;
						}
						throw new JvmUncaughtException(String.format("Error while setting field (%s) of (%s) in method (%s) of class (%s)",
								fi.name, nameFiOwner, methodName, clazz), e);
					}
					break;
				}
				case Opcodes.GETFIELD: // 0xb4
				{
					final FieldInsnNode fi = (FieldInsnNode) instr;
					final Object fieldInstance = stack.pop();
					if (fieldInstance == null) {
						throw new NullPointerException();
					}
					final Class<?> classFieldOwner = fieldInstance.getClass();
					try {
						final Field field = findDeclaredField(classFieldOwner, fi);
						assert field != null;
						field.setAccessible(true);
						Object fieldValue = field.get(fieldInstance);
						fieldValue = visitor.visitFieldAccess(opcode, fieldInstance, field, fieldValue);
						fieldValue = convertFieldTypeIntoJvmType(field.getType(), fieldValue);
						stack.push(fieldValue);
					} catch (NoSuchFieldException | SecurityException
							| IllegalArgumentException | IllegalAccessException e) {
						final boolean doContinueWhileE = handleCatchException(e);
						if (doContinueWhileE) {
							continue whileInstr;
						}
						throw new JvmUncaughtException(String.format("Error while reading field (%s) of (%s) in method (%s) of class (%s)",
								fi.name, fi.owner, methodName, classFieldOwner), e);
					}
					break;
				}
				case Opcodes.PUTFIELD: // 0xb5
				{
					final FieldInsnNode fi = (FieldInsnNode) instr;
					final Object oValue = stack.pop();
					final Object fieldInstance = stack.pop();
					if (fieldInstance == null) {
						throw new NullPointerException();
					}
					Class<?> classFieldOwner = fieldInstance.getClass(); // fi.owner?
					final String fiOwnerName = fi.owner.replace('/', '.');
					if (!classFieldOwner.getName().equals(fiOwnerName)) {
						classFieldOwner = registry.loadClass(fiOwnerName, clazz);
					}
					try {
						final Field field = findDeclaredField(classFieldOwner, fi);
						field.setAccessible(true);
						Object oValueField = convertJvmTypeIntoFieldType(field.getType(), oValue);
						oValueField = visitor.visitFieldAccess(opcode, fieldInstance, field, oValueField);
						if (Modifier.isFinal(field.getModifiers()) && pMethod instanceof Constructor<?>) {
							// We want to set a final field while executing a constructor.
							final Field fieldMods = Field.class.getDeclaredField("modifiers");
							fieldMods.setAccessible(true);
							final Integer modifiers = (Integer) fieldMods.get(field);
							fieldMods.set(field, Integer.valueOf(modifiers.intValue() & ~Modifier.FINAL));
						}
						try {
							field.set(fieldInstance, oValueField);
						} catch (IllegalArgumentException e) {
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								continue whileInstr;
							}
							final Class<?> classValue = (oValueField != null) ? oValueField.getClass() : null;
							final ClassLoader clValue = (classValue != null) ? classValue.getClassLoader() : null;
							throw new JvmUncaughtException(String.format("Argument-error while setting field (%s) of (%s) of class (%s) of class-loader (%s) to object of class (%s) of (%s) in method (%s)",
									fi.name, fi.owner, classFieldOwner, classFieldOwner.getClassLoader(),
									classValue, clValue,
									methodName), e);
						}
					}
					catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
						final boolean doContinueWhileE = handleCatchException(e);
						if (doContinueWhileE) {
							continue whileInstr;
						}
						throw new JvmUncaughtException(String.format("Error while setting field (%s) of (%s) for (%s) in method (%s)",
								fi.name, fi.owner, classFieldOwner, methodName), e);
					}
					break;
				}
				case Opcodes.INVOKEVIRTUAL: // 0xb6
				{
					final MethodInsnNode mi = (MethodInsnNode) instr;
					final boolean exceptionHandled = executeInvokeVirtualOrInterface(mi, false, false, true);
					if (exceptionHandled) {
						continue whileInstr;
					}
					if (invocationHandler.postprocessCall(this, mi, stack)) {
						continue whileInstr;
					}
					break;
				}
				case Opcodes.INVOKESPECIAL: // 0xb7
				{
					final MethodInsnNode mi = (MethodInsnNode) instr;
					final boolean exceptionHandled;
					if ("<init>".equals(mi.name)) {
						exceptionHandled = executeInvokeSpecial(mi);
					}
					else {
						exceptionHandled = executeInvokeVirtualOrInterface(mi, false, false, false);
					}
					if (exceptionHandled) {
						continue whileInstr;
					}
					if (invocationHandler.postprocessCall(this, mi, stack)) {
						continue whileInstr;
					}
					break;
				}
				case Opcodes.INVOKESTATIC: // 0xb8
				{
					final MethodInsnNode mi = (MethodInsnNode) instr;
					final boolean exceptionHandled = executeInvokeVirtualOrInterface(mi, false, true, false);
					if (exceptionHandled) {
						continue whileInstr;
					}
					if (invocationHandler.postprocessCall(this, mi, stack)) {
						continue whileInstr;
					}
					break;
				}
				case Opcodes.INVOKEINTERFACE: // 0xb9
				{
					final MethodInsnNode mi = (MethodInsnNode) instr;
					final boolean exceptionHandled = executeInvokeVirtualOrInterface(mi, true, false, false);
					if (exceptionHandled) {
						continue whileInstr;
					}
					if (invocationHandler.postprocessCall(this, mi, stack)) {
						continue whileInstr;
					}
					break;
				}
				case Opcodes.INVOKEDYNAMIC: // 0xba
				{
					final InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) instr;
					final Object callSiteInstance;
					if (callsiteViaProxy) {
						final CallSiteSimulation jvmCallSite = executeInvokeDynamic(idin);
						callSiteInstance = jvmCallSite.getProxy();
					}
					else {
						callSiteInstance = registry.getCallSiteGenerator().createCallSite(registry, clazz, idin, stack);
					}
					stack.push(callSiteInstance);
					break;
				}
				case Opcodes.NEW: // 0xbb
				{
					final TypeInsnNode ti = (TypeInsnNode) instr;
					final String nameNew = ti.desc.replace('/', '.');
					Class<?> classNew;
					try {
						classNew = registry.loadClass(nameNew, clazz);
					} catch (ClassNotFoundException e) {
						final boolean doContinueWhileE = handleCatchException(e);
						if (doContinueWhileE) {
							continue whileInstr;
						}
						throw new JvmUncaughtException(String.format("Error while loading class (%s) in method (%s)",
								nameNew, methodName), e);
					}
					stack.push(new UninitializedInstance(classNew));
	
					break;
				}
				case Opcodes.NEWARRAY: // 0xbc
				{
					final IntInsnNode ii = (IntInsnNode) instr;
					final int atype = ii.operand;
					final int len = ((Integer) stack.pop()).intValue();
					final Object oArray = Array.newInstance(AtypeEnum.lookupAtypeClass(atype), len);
					stack.push(oArray);
					break;
				}
				case Opcodes.ANEWARRAY: // 0xbd
				{
					final TypeInsnNode ti = (TypeInsnNode) instr;
					final Type type = Type.getObjectType(ti.desc);
					final Class<?> classArray;
					if (type.getSort() == Type.ARRAY) {
						final int dims = type.getDimensions();
						final int[] aDims = new int[dims];
						Class<?> elClass;
						try {
							elClass = getClassArrayViaType(type, registry, clazz);
						} catch (ClassNotFoundException e) {
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								continue whileInstr;
							}
							throw new JvmUncaughtException(String.format("Error while loading array-class (%s) in method (%s)",
									type, methodName), e);
						}
						final Object oArray = Array.newInstance(elClass, aDims);
						classArray = oArray.getClass();
					}
					else {
						final String nameNew = ti.desc.replace('/', '.');
						try {
							classArray = registry.loadClass(nameNew, clazz);
						} catch (ClassNotFoundException e) {
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								continue whileInstr;
							}
							throw new JvmUncaughtException(String.format("Error while loading class (%s) in method (%s)",
									nameNew, methodName), e);
						}
					}
					final int len = ((Integer) stack.pop()).intValue();
					final Object oArray = Array.newInstance(classArray, len);
					stack.push(oArray);
					break;
				}
				case Opcodes.ARRAYLENGTH: // 0xbe
					final int length = Array.getLength(stack.pop());
					stack.push(Integer.valueOf(length));
					break;
				case Opcodes.ATHROW: // 0xbf
				{
					final Throwable e = (Throwable) stack.pop();
					final boolean doContinueWhileE = handleCatchException(e);
					if (doContinueWhileE) {
						continue whileInstr;
					}
					throw new JvmUncaughtException("ATHROW in " + getMethod() + "at " + getCurrLineNum(),
							e);
				}
				case Opcodes.CHECKCAST: // 0xc0
				{
					final TypeInsnNode tin = (TypeInsnNode) instr;
					final Object obj = stack.peek();
					if (obj != null) {
						boolean canCast = handleCheckcast(tin.desc, obj);
						if (!canCast) {
							final ClassCastException e = new ClassCastException(String.format("Can't convert object of type (%s) to (%s)",
									obj.getClass(), tin.desc));
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								continue whileInstr;
							}
							throw new JvmUncaughtException("CHECKCAST in " + getMethod() + "at " + getCurrLineNum(),
									e);
						}
					}
					break;
				}
				case Opcodes.INSTANCEOF: // 0xc1
				{
					final TypeInsnNode tin = (TypeInsnNode) instr;
					final Object obj = stack.pop();
					if (obj == null) {
						stack.push(Integer.valueOf(0));
					}
					else {
						final boolean canCast = handleCheckcast(tin.desc, obj);
						stack.push(Integer.valueOf(canCast ? 1 : 0));
					}
					break;
				}
				case Opcodes.MONITORENTER: // 0xc2
				{
					final Object objMonitor = stack.pop();
					if (objMonitor == null) {
						throw new NullPointerException("monitor-enter: no monitor-object");
					}
					visitor.visitMonitorEnter(objMonitor);
					final int currCounter = registry.enterMonitor(objMonitor);
					visitor.visitMonitorEntered(objMonitor, Integer.valueOf(currCounter));
					break;
				}
				case Opcodes.MONITOREXIT: // 0xc3
				{
					final Object objMonitor = stack.pop();
					if (objMonitor == null) {
						throw new NullPointerException("monitor-exit: no monitor-object");
					}
					final int currCounter = registry.exitMonitor(objMonitor); 
					visitor.visitMonitorExit(objMonitor, Integer.valueOf(currCounter));
					break;
				}
				case 0xc4: // 0xc4
					throw new JvmException("Opcode 0xc4 (Opcode_c4) not yet supported.");
				case Opcodes.MULTIANEWARRAY: // 0xc5
				{
					final MultiANewArrayInsnNode manai = (MultiANewArrayInsnNode) instr;
					final Type aType = Type.getType(manai.desc);
					final Class<?> classArray = getClassArrayViaType(aType, registry, clazz);
					final int[] dims = new int[manai.dims];
					for (int i = 0; i < dims.length; i++) {
						dims[dims.length - 1- i] = ((Integer) stack.pop()).intValue();
					}
					final Object oArray = Array.newInstance(classArray, dims);
					stack.push(oArray);
					break;
				}
				case Opcodes.IFNULL: // 0xc6
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object value = stack.pop();
					if (value == null) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
					break;
				}
				case Opcodes.IFNONNULL: // 0xc7
				{
					final JumpInsnNode ji = (JumpInsnNode) instr;
					final Label labelDest = ji.label.getLabel();
					final Integer instrLabel = mapLabel.get(labelDest);
					assert instrLabel != null : "unknown label " + labelDest;
	
					final Object value = stack.pop();
					if (value != null) {
						instrNum = instrLabel.intValue();
						continue whileInstr;
					}
					break;
				}
				default:
					if (instr instanceof LineNumberNode) {
						final LineNumberNode lnn = (LineNumberNode) instr;
						currLineNum = lnn.line;
					}
					//else if (instr instanceof LabelNode) {
					//	final LabelNode label = (LabelNode) instr;
					//}
					//else if (instr instanceof FrameNode) {
					//	final FrameNode fn = (FrameNode) instr;
					//}
					else if (!(instr instanceof LabelNode || instr instanceof FrameNode)) {
						throw new JvmException(String.format("Unsupported instruction: %02x at instruction %d of type %s in %s",
								Integer.valueOf(opcode), Integer.valueOf(instrNum), instr, methodName));
					}
					break;
				}
				
				instrNum++;
			}
		}
		finally {
			if (pMethod instanceof Method) {
				final Method mMethod = (Method) pMethod;
				methodReturnObj = convertJvmTypeIntoFieldType(mMethod.getReturnType(), methodReturnObjJvm);
			}
			else {
				methodReturnObj = methodReturnObjJvm;
			}
			visitor.visitMethodExit(clazz, pMethod, this, methodReturnObj);
		}

		return methodReturnObj;
	}

	/**
	 * Executes an INVOKEDYNAMIC-instruction
	 * @param instr INVOKEDYNAMIC
	 * @return <code>true</code> for next step in while, <code>false</code> leave switch only (and increment instr-idx)
	 * @throws NoSuchMethodError in case of an unknown method
	 * @throws ClassNotFoundException in case of an unknown interface-class
	 */
	private CallSiteSimulation executeInvokeDynamic(final InvokeDynamicInsnNode idi) throws NoSuchMethodError, ClassNotFoundException {
		final CallSiteSimulation jvmCallSite;
		final Handle bsm = idi.bsm;
		if ("java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())
			&& "metafactory".equals(bsm.getName())
			&& "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;".equals(bsm.getDesc())) {
			// Desc:
			// (Ljava/lang/invoke/MethodHandles$Lookup;
			//  Ljava/lang/String;
			//  Ljava/lang/invoke/MethodType;
			//  Ljava/lang/invoke/MethodType;
			//  Ljava/lang/invoke/MethodHandle;
			//  Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
			// 
			// final Lookup publicLookup = MethodHandles.publicLookup();
			
			// call MethodHandleNatives#linkCallSite:
			//   arg0: Class<T>: Class of caller
			//   arg1: DirectMethodHandle: MethodHandle(Lookup,String,MethodType,MethodType,MethodHandle,MethodType)CallSite
			//   arg2: "apply"
			//   arg3: MethodType: ()IntFunction
			//   arg4: Object[3]: [(int)Object, MethodHandle(int)Integer, (int)Integer]
			//   arg5: Object[1]: [null]
			
			final Object[] bsmArgs = idi.bsmArgs;
			if (bsmArgs.length == 3 && bsmArgs[1] instanceof Handle) {
				final Handle lambdaHandle = (Handle) bsmArgs[1];
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("INVOKEDYNAMIC: tag=%d, owner=%s, name=%s, desc=%s for name=%s, desc=%s, bsmArgs[0]=%s, bsmArgs[2]=%s, handle=%s",
							Integer.valueOf(lambdaHandle.getTag()), lambdaHandle.getOwner(), lambdaHandle.getName(), lambdaHandle.getDesc(),
							idi.name, idi.desc, bsmArgs[0], bsmArgs[2], idi.bsm));
				}
				final Type[] argumentTypes = Type.getArgumentTypes(idi.desc);
				final Object[] aArguments = new Object[argumentTypes.length];
				for (int i = argumentTypes.length - 1; i >= 0; i--) {
					aArguments[i] = stack.pop();
				}
				
				final Object caller = null;
				jvmCallSite = registry.getCallSiteRegistry().buildCallSite(caller, clazz, pMethod, idi,
						lambdaHandle, aArguments);
				//final MethodInsnNode insnNode = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, lambdaOwner, lambdaName, lamdaDesc, false);
				//isException = executeInvokeVirtualOrInterface(insnNode, false);
				//final Class<?> classMHN = registry.loadClass("java.lang.invoke.MethodHandleNatives");
				//    static MemberName linkCallSite(Object callerObj,
				//            Object bootstrapMethodObj,
				//            Object nameObj, Object typeObj,
				//            Object staticArguments,
				//            Object[] appendixResult) {
				//Method methodLinkCallSite;
				//try {
				//	methodLinkCallSite = classMHN.getDeclaredMethod("linkCallSite", Object.class, Object.class, Object.class,
				//			Object.class, Object.class, Object[].class);
				//} catch (NoSuchMethodException e) {
				//	throw new JvmException("no such method linkCallSite in " + classMHN, e);
				//} catch (SecurityException e) {
				//	throw new JvmException("can't access method linkCallSite in " + classMHN, e);
				//}
				//methodLinkCallSite.setAccessible(true);
				//methodLinkCallSite.invoke(classMHN, clazz, 
			}
			else {
				throw new JvmException("Unexpected args for LambdaMetafactory: " + Arrays.toString(bsmArgs));
			}
		}
		else {
			LOG.info("Handle: " + bsm);
			Object[] args = idi.bsmArgs;
			LOG.info("Args: " + Arrays.toString(args));
			LOG.info("args1: " + args[1].getClass());
			String desc = idi.desc;
			LOG.info("Desc: " + desc);
			String name = idi.name;
			LOG.info("name : " + name);
			throw new JvmException("Unexpected bootstrap method: " + bsm);
		}	 
		return jvmCallSite;
	}

	/**
	 * Executes an INVOKEVIRTUAL-instruction
	 * @param mi INVOKEVIRTUAL or INVOKEINTERFACE
	 * @param isInterface <code>true</code> in case of INVOKEINTERFACE
	 * @param isStatic <code>true</code> in case of static method
	 * @param isVirtual <code>true</code> in case of INVOKEVIRTUAL
	 * @return <code>true</code> for next step in while, <code>false</code> leave switch only (and increment instr-idx)
	 * @throws Throwable in case of an exception 
	 */
	private boolean executeInvokeVirtualOrInterface(final MethodInsnNode mi, boolean isInterface,
			boolean isStatic, boolean isVirtual) throws Throwable {
		final Type[] origTypes = Type.getArgumentTypes(mi.desc);
		int numArgs = origTypes.length;
		
		Object objRef;
		Class<?> classOwner;
		String miOwnerName = mi.owner.replace('/', '.');
		String lMethodName = mi.name;
		String methodDesc = mi.desc;
		Type[] types = origTypes;
		Type returnType = Type.getReturnType(mi.desc);
		boolean isCallSite = false;
		boolean isCheckClassMethods = true;
		boolean isMethodOverriden = false;
		boolean lIsStatic = isStatic;
		if (lIsStatic) {
			Boolean doContinueWhile = invocationHandler.preprocessStaticCall(this, mi, stack);
			if (doContinueWhile != null) {
				return doContinueWhile.booleanValue();
			}
			if (EXEC_ACCESS_CONTR_NATIVE && "java/security/AccessController".equals(mi.owner)
					&& "doPrivileged".equals(mi.name)) {
				lMethodName = "run";
				types = new Type[0];
				methodDesc = "()Ljava/lang/Object;";
				returnType = Type.getReturnType(methodDesc);
				objRef = stack.peek();
				classOwner = objRef.getClass(); //registry.loadClass("java.security.PrivilegedAction");
				numArgs = 0;
				isMethodOverriden = true;
			}
			else {
				try {
					classOwner = registry.loadClass(miOwnerName, clazz);
				} catch (ClassNotFoundException e) {
					throw new ClassNotFoundException(String.format("no class (%s) in context-class (%s) of (%s)",
							miOwnerName, clazz, clazz.getClassLoader()));
				}
				objRef = classOwner;
			}
		}
		else {
			final Object objRefStack = stack.peek(numArgs);
			if (objRefStack == null) {
				throw new NullPointerException("invokevirtual: NPE at " + mi.name + " with " + mi.desc);
			}
			Boolean doContinueWhile = invocationHandler.preprocessInstanceCall(this, mi, objRefStack, stack);
			if (doContinueWhile != null) {
				visitor.visitMethodExitBack(clazz, pMethod, this, null);
				return doContinueWhile.booleanValue();
			}
			objRef = objRefStack;
			classOwner = objRefStack.getClass();
			CallSiteSimulation callSite = null;
			int callSiteLevel = 0;
			while (objRef instanceof JvmCallSiteMarker && !"java/lang/Object".equals(mi.owner)) {
				callSiteLevel++;
				if (callSiteLevel > maxCallSiteLevel) {
					throw new JvmException(String.format("call-site with too depth level (>%s): mi.owner=%s, mi.name=%s, mi.desc=%s, objRefStack.class=%s, callSite=%s",
							Integer.valueOf(maxCallSiteLevel), mi.owner, mi.name, mi.desc, objRefStack.getClass(), callSite));
				}
				if (objRef instanceof JvmCallSiteMarker && !"java/lang/Object".equals(mi.owner)) {
					callSite = registry.getCallSiteRegistry().checkForCallSite(objRef);
				}
				else {
					callSite = null;
				}
				if (callSite != null && callSite.getName().equals(lMethodName)) {
					if (!callSite.getDesc().equals(mi.desc)) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("call-site: parameter-types? method.name=%s, method.desc=%s but call-site.desc=%s",
									mi.name, mi.desc, callSite.getDesc()));
						}
					}
					// remove proxy-object from stack.
					stack.pop(numArgs);
	
					// proxy-result of an previous INVOKEDYNAMIC-call.
					// We retrieved the stored CallSiteSimulation-object.
					// This object contains the details of the corresponding INVOKEDYNAMIC-bootstrap-method.
					// final InvokeDynamicInsnNode idi = callSite.getInstruction();
					//final Object[] bsmArgs = idi.bsmArgs;
					//final Handle handle = (Handle) bsmArgs[1];
					final String lambdaOwner = callSite.getLambdaOwner();
					final String lambdaName = callSite.getLambdaName();
					final String lamdaDesc = callSite.getLambdaDesc();
					final Object[] dynamicArgs = callSite.getArguments();
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("INVOKEDYNAMIC-result-object: class (%s), bsmTag (%d), method (%s -> %s), desc (%s -> %s), dynamicArgs=%s",
								callSite.getOwnerClazz(), Integer.valueOf(callSite.getBsmTag()),
								lMethodName, lambdaName, methodDesc, lamdaDesc, Arrays.toString(dynamicArgs)));
					}
					miOwnerName = lambdaOwner.replace('/', '.');
					lMethodName = lambdaName;
					methodDesc = lamdaDesc;
					types = Type.getArgumentTypes(lamdaDesc);
					returnType = Type.getReturnType(lamdaDesc);
					numArgs = types.length;
					final int objOffset;
					final int bsmTag = callSite.getBsmTag();
					lIsStatic = (bsmTag == Opcodes.H_INVOKESTATIC);
					if ((bsmTag == Opcodes.H_INVOKEVIRTUAL
								|| bsmTag == Opcodes.H_INVOKESPECIAL
								|| bsmTag == Opcodes.H_INVOKEINTERFACE)
							&& dynamicArgs.length > 0) {
						objRef = dynamicArgs[0];
						// The object-reference doesn't belong to the dynamic-method-arguments.
						objOffset = 1;
					}
					else if (lIsStatic || bsmTag == Opcodes.H_NEWINVOKESPECIAL) {
						objRef = callSite.getOwnerClazz();
						objOffset = 0;
					}
					else {
						// dynamicArgs.length is expected to be 0.
						try {
							objRef = stack.peek(numArgs - dynamicArgs.length);
						} catch (ArrayIndexOutOfBoundsException e) {
							throw new JvmException(String.format("Unexpected stack (%s) and types (%s)", stack, Arrays.toString(types)), e);
						}
						objOffset = 0;
					}
					if (bsmTag == Opcodes.H_INVOKEINTERFACE) {
						classOwner = objRef.getClass();
					}
					else {
						// We should use class miOwnerName.
						try {
							// The context-class is important in OSGi-environments to use a knowing class-loader.
							classOwner = registry.loadClass(miOwnerName, objRef.getClass());
						} catch (ClassNotFoundException e) {
							final boolean doContinueWhileE = handleCatchException(e);
							if (doContinueWhileE) {
								return true;
							}
							throw e;
						}
					}

					if (dynamicArgs.length > 0) {
						// The dynamicArgs had been given the INVOKEDYNAMIC-instruction.
						// We have to place them before the other method's arguments into the stack.
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("Stack before inserting dynamic arguments (types.length=%d, dynamicArgs.length=%d): %s",
									Integer.valueOf(types.length), Integer.valueOf(dynamicArgs.length), stack));
						}
						stack.pushAndResize(types.length - dynamicArgs.length + objOffset, dynamicArgs);
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("Stack after inserting dynamic arguments (types.length=%d, dynamicArgs.length=%d): %s",
									Integer.valueOf(types.length), Integer.valueOf(dynamicArgs.length), stack));
						}
					}
					isCallSite = true;
				}
				else if (callSite != null && callSiteLevel > 1) {
					// We have a call-site which doesn't fit to the method-name requested not on top-level.
					break;
				}
				else if (callSite != null) {
					// We have a call-site (INVOKEDYNAMIC) but call another interface-method.
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("  method %s/%s but call-site %s/%s",
								mi.name, mi.desc, callSite.getName(), callSite.getDesc()));
					}
					isCheckClassMethods = false;
					objRef = objRefStack;
					classOwner = objRef.getClass();
					break;
				}
			}
		}
		if (isCallSite && "<init>".equals(lMethodName)) {
			// constructor-ref
			final Constructor<?>[] declaredConstructors = classOwner.getDeclaredConstructors();
			Constructor<?> constructor = null;
			for (final Constructor<?> constructorLoop : declaredConstructors) {
				final String loopDescr = Type.getConstructorDescriptor(constructorLoop);
				if (loopDescr.equals(methodDesc)) {
					constructor = constructorLoop;
					break;
				}
			}
			if (constructor == null) {
				throw new NoSuchMethodError(String.format("invoke: No such constructor (%s, was %s) with (%s, was %s) in (%s) for (%s, was %s)",
						 lMethodName, mi.name, methodDesc, mi.desc, miOwnerName, mi.owner, classOwner));
			}
			constructor.setAccessible(true);
			final Object[] initargs = new Object[numArgs];
			for (int i = 0; i < numArgs; i++) {
				final int idxArg = numArgs - 1 - i;
				Object obj = stack.pop();
				final Type typeDecl = types[idxArg];
				obj = convertJvmTypeIntoDeclType(obj, typeDecl);
				initargs[idxArg] = obj;
			}
			// stack.pop(); // remove objectref
			final Object instanceInit;
			try {
				constructor.setAccessible(true);
				instanceInit = constructor.newInstance(initargs);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				final Function<Object, String> fktDisp = o -> (o != null) ? o.getClass().toString() : null;
				final String sInitargs = Arrays.stream(initargs).map(fktDisp).collect(Collectors.joining(", "));
				throw new JvmException(String.format("invokespecial: Error while initializing constructor (%s) with name %s and description %s with args (%s)",
						constructor, mi.name, mi.desc, sInitargs), e);	
			}
			catch (InvocationTargetException e) {
				final Throwable eCause = e.getCause();
				final boolean doContinueWhile = handleCatchException(eCause);
				if (doContinueWhile) {
					return true;
				}
				throw new JvmUncaughtException("invokespecial: InvocationTargetException at " + mi.name + " with " + mi.desc,
						e.getCause());
			}
			stack.push(instanceInit);
			return false;
		}
		if (mi.getOpcode() == Opcodes.INVOKESPECIAL && !classOwner.getName().equals(miOwnerName)) {
			// e.g. StringBuilder#append(int) -> AbstractStringBuilder.append(int).
			classOwner = registry.loadClass(miOwnerName, clazz);
		}
		Method invMethod = null;
		if (isCheckClassMethods) {
			invMethod = findMethodInClass(lMethodName, types, returnType, classOwner);
		}
		if (invMethod == null && !lIsStatic && (isInterface || isVirtual)) {
			final Class<?> classInt;
			try {
				assert mi != null : "mi";
				assert mi.owner != null : "mi.owner";
				classInt = registry.loadClass(miOwnerName, clazz);
			} catch (ClassNotFoundException e) {
				final boolean doContinueWhile = handleCatchException(e);
				if (doContinueWhile) {
					return true;
				}
				throw e;
			}
			invMethod = findMethodInInterfaces(classOwner, lMethodName, types, isVirtual, classInt);
		}
		if (invMethod == null) {
			if (mi.name.equals(lMethodName)) {
				throw new NoSuchMethodError(String.format("invoke: No such method (%s) with (%s) in (%s) for %s",
						 mi.name, mi.desc, mi.owner, classOwner));
			}
			throw new NoSuchMethodError(String.format("invoke: No such method (%s, was %s) with (%s, was %s) in (%s) for (%s, was %s)",
					 lMethodName, mi.name, methodDesc, mi.desc, miOwnerName, mi.owner, classOwner));
		}
		final SimpleClassExecutor executor = registry.getClassExecutor(invMethod.getDeclaringClass());
		final boolean isNative = Modifier.isNative(invMethod.getModifiers());
		Object returnObj;
		if (executor != null && !isNative) {
			try {
				returnObj = executor.executeMethod(mi.getOpcode(), invMethod, methodDesc, stack);
				visitor.visitMethodExitBack(clazz, pMethod, this, returnObj);
			}
			catch (JvmUncaughtException e) {
				final boolean doContinueWhile = handleCatchException(e.getCause());
				if (doContinueWhile) {
					return true;
				}
				// This exception isn't handled here.
				throw e;
			}
			catch (JvmException e) {
				throw new JvmException(String.format("JvmException in %s#%d, execution of %s",
						clazz, Integer.valueOf(getCurrLineNum()), invMethod), e);
			}
		}
		else {
			final Class<?> classInt;
			try {
				classInt = registry.loadClass(miOwnerName, clazz);
			} catch (ClassNotFoundException e) {
				final boolean doContinueWhile = handleCatchException(e);
				if (doContinueWhile) {
					return true;
				}
				throw e;
			}
			final Method invMethodIntf = findMethodInClass(lMethodName, types, returnType, classInt);
			if (invMethodIntf != null) {
				invMethod = invMethodIntf;
			}

			final Object[] initargs = new Object[numArgs];
			int idxArg = numArgs;
			for (int i = 0; i < numArgs; i++) {
				idxArg--;
				Object oStack = stack.pop();
				try {
					oStack = convertJvmTypeIntoDeclType(oStack, types[idxArg]);
				} catch (ArrayIndexOutOfBoundsException e) {
					throw new JvmException(String.format("Unexpected AIOOBE while converting JVM-types (%s) with stack (%s) and idxArg %d",
							Arrays.toString(types), stack, Integer.valueOf(idxArg)), e);
				}
				initargs[idxArg] = oStack;
			}
			if (!lIsStatic || isMethodOverriden) {
				stack.pop(); // remove object-reference
			}
			try {
				invMethod.setAccessible(true);
				returnObj = invMethod.invoke(objRef, initargs);
			}
			catch (IllegalAccessException | IllegalArgumentException e) {
				throw new JvmException(String.format("invokevirtual: Execution error at %s, %s with %s on object of type %s",
						invMethod, mi.name, mi.desc, (objRef != null) ? objRef.getClass() : null), e);
			}
			catch (InvocationTargetException e) {
				final Throwable eCause = e.getCause();
				final boolean doContinueWhile = handleCatchException(eCause);
				if (doContinueWhile) {
					return true;
				}
				throw new JvmUncaughtException("invokevirtual: InvocationTargetException at " + mi.name + " with " + mi.desc,
						e.getCause());
			}
		}
		final Class<?> classReturnType = invMethod.getReturnType();
		if (!void.class.equals(classReturnType)) {
			final Object returnObjStack = convertFieldTypeIntoJvmType(classReturnType, returnObj);
			stack.push(returnObjStack);
		}
		return false;
	}

	/**
	 * Finds a method in the interfaces of a class.
	 * @param classOwner class which interfaces are to be searches
	 * @param lMethodName name of method
	 * @param types argument-types of the method
	 * @param isVirtual <code>true</code> if all interfaces are to be searched
	 * @param classInt the parent-interface in case of non-virtual mode
	 * @return method or <code>null</code>
	 */
	public static Method findMethodInInterfaces(Class<?> classOwner, String lMethodName, Type[] types, boolean isVirtual,
			final Class<?> classInt) {
		Method invMethod = null;
		Class<?> classObj = classOwner;
whileSuperClass:
		while (classObj != null) {
			final Class<?>[] aInterfaces = classObj.getInterfaces();
			for (Class<?> classLoop : aInterfaces) {
				if (isVirtual || classInt.isAssignableFrom(classLoop)) {
					invMethod = findMethodInInterface(lMethodName, types, classLoop);
					if (invMethod != null) {
						break whileSuperClass;
					}
				}
			}
			classObj = classObj.getSuperclass();
		}
		return invMethod;
	}

	/**
	 * Executes and INVOKESPECIAL-instruction.
	 * @param mi INVOKESPECIAL
	 * @return <code>true</code> if the exception will be handled at the set instruction-index
	 * @throws Throwable in case of an exception
	 */
	public boolean executeInvokeSpecial(final MethodInsnNode mi) throws Throwable{
		final Type[] types = Type.getArgumentTypes(mi.desc);
		final int anzArgs = types.length;

		final UninitializedInstance uninstType;
		final Class<?> classInit = registry.loadClass(Type.getObjectType(mi.owner).getClassName(), clazz);
		final Object oInstance = stack.peek(anzArgs);
		Class<?> classConstr = classInit;
		if (oInstance instanceof UninitializedInstance) {
			uninstType = (UninitializedInstance) oInstance;
			if (Thread.class.isAssignableFrom(classInit) && !DONT_PATCH_THREAD_CLASSES) {
				classConstr = registry.getThreadClassGenerator().generateClass(classInit, mi.desc);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("executeInvokeSpecial: replace (%s) by (%s)",
							classInit, classConstr));
				}
			}
		}
		else {
			uninstType = null;
		}
		final Constructor<?>[] constructors = classConstr.getDeclaredConstructors();
		Constructor<?> constructor = null;
		for (Constructor<?> constructorLoop : constructors) {
			String loopDescr = Type.getConstructorDescriptor(constructorLoop);
			if (loopDescr.equals(mi.desc)) {
				constructor = constructorLoop;
				break;
			}
		}
		if (constructor == null) {
			throw new JvmException("invokespecial: Couldn't found " + mi.name + " with " + mi.desc + " for " + stack.peek(anzArgs));	
		}

		final Object instanceInit;
		SimpleClassExecutor executor = null;
		if (registry.isClassConstructorJsmudPatched(classConstr)) {
			executor = registry.getClassExecutor(classConstr);
		}
		if (executor != null) {
			if (uninstType == null) {
				// The instance is instanciated already.
				instanceInit = null;
			}
			else {
				try {
					final Constructor<?> constrDefault = classConstr.getDeclaredConstructor();
					constrDefault.setAccessible(true);
					instanceInit = constrDefault.newInstance();
				} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
						| IllegalArgumentException | InvocationTargetException e) {
					throw new JvmException(String.format("Couldn't instanciate %s via default-constructor", classConstr.getName()), e);
				}
				stack.replaceUninitialized(uninstType, instanceInit);
			}
			try {
				executor.executeMethod(mi.getOpcode(), constructor, mi.desc, stack);
				visitor.visitMethodExitBack(clazz, pMethod, this, null);
			}
			catch (Throwable e) {
				final boolean doContinueWhile = handleCatchException(e);
				if (doContinueWhile) {
					return true;
				}
				// This exception isn't handled here.
				throw e;
			}
		}
		else {
			final Object[] initargs = new Object[anzArgs];
			for (int i = 0; i < anzArgs; i++) {
				final int idxArg = anzArgs - 1 - i;
				Object obj = stack.pop();
				obj = convertJvmTypeIntoDeclType(obj, types[idxArg]);
				initargs[idxArg] = obj;
			}
			stack.pop(); // remove objectref

			try {
				constructor.setAccessible(true);
				instanceInit = constructor.newInstance(initargs);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				final Function<Object, String> fktDisp = o -> (o != null) ? o.getClass().toString() : null;
				final String sInitargs = Arrays.stream(initargs).map(fktDisp).collect(Collectors.joining(", "));
				throw new JvmException(String.format("invokespecial: Error while initializing constructor (%s) of class (%s) in class-loader (%s) with name %s and description %s with args (%s)",
						constructor, constructor.getDeclaringClass(), constructor.getDeclaringClass().getClassLoader(),
						mi.name, mi.desc, sInitargs), e);	
			}
			catch (InvocationTargetException e) {
				final Throwable eCause = e.getCause();
				final boolean doContinueWhile = handleCatchException(eCause);
				if (doContinueWhile) {
					return true;
				}
				throw new JvmUncaughtException("invokespecial: InvocationTargetException at " + mi.name + " with " + mi.desc,
						e.getCause());
			}
		}

		if (uninstType != null) {
			stack.replaceUninitialized(uninstType, instanceInit);
			if (Thread.class.isAssignableFrom(classConstr) && !DONT_PATCH_THREAD_CLASSES) {
				LOG.debug("set ThreadExecutor: " + classConstr);
				final Field field = classConstr.getDeclaredField(ThreadClassGenerator.FIELD_THREAD_EXECUTOR);
				field.set(instanceInit, new ThreadExecutor(registry, visitor));
			}
		}
		return false;
	}

	/**
	 * Gets a field in the class or one of its super-classes.
	 * @param classFieldOwner class
	 * @param fi field wanted
	 * @return field
	 * @throws NoSuchFieldException if the field couldn't be found
	 */
	private static Field findDeclaredField(final Class<?> classFieldOwner, final FieldInsnNode fi)
			throws NoSuchFieldException {
		Class<?> classField = classFieldOwner;
		Field field = null;
		NoSuchFieldException nsfe = null;
		while (true) {
			try {
				field = classField.getDeclaredField(fi.name);
			}
			catch (NoSuchFieldException e) {
				if (nsfe == null) {
					nsfe = e;
				}
				if (Object.class.equals(classField)) {
					// The field couldn't be found.
					throw nsfe;
				}
				classField = classField.getSuperclass();
				continue;
			}
			break;
		}
		return field;
	}

	/**
	 * Searches for a constructor in the given class or one of its parents.
	 * @param types argument-types of the constructor
	 * @param cClassObj class 
	 * @return constructor or <code>null</code>
	 */
	public static Constructor<?> findConstrInClass(final Type[] types, final Class<?> cClassObj) {
		Constructor<?> invConstr = null;
		Class<?> classObj = cClassObj;
whileSuperClass:
		while (classObj != null) {
loopDeclMeth:
			for (Constructor<?> constrLoop : classObj.getDeclaredConstructors()) {
				final String descConstr = Type.getConstructorDescriptor(constrLoop);
				final Type[] argumentTypes = Type.getArgumentTypes(descConstr);
				boolean found = argumentTypes.length == types.length;
				if (!found) {
					continue;
				}
				for (int i = 0; i < argumentTypes.length; i++) {
					if (!argumentTypes[i].equals(types[i])) {
						continue loopDeclMeth;
					}
				}
				invConstr = constrLoop;
				break whileSuperClass;
			}
			classObj = classObj.getSuperclass();
		}
		return invConstr;
	}

	/**
	 * Searches for a method in the given class or one of its parents.
	 * @param invName name of the method
	 * @param types argument-types of the method
	 * @param returnType return-type of the method
	 * @param cClassObj class 
	 * @return method or <code>null</code>
	 */
	public static Method findMethodInClass(final String invName, final Type[] types, final Type returnType, final Class<?> cClassObj) {
		//LOG.debug(String.format("  Searching method (%s) in class (%s) ...", invName, cClassObj));
		Method invMethod = null;
		Class<?> classObj = cClassObj;
whileSuperClass:
		while (classObj != null) {
loopDeclMeth:
			for (Method methodLoop : classObj.getDeclaredMethods()) {
				if (!methodLoop.getName().equals(invName)) {
					continue;
				}
				Type[] argumentTypes = Type.getArgumentTypes(methodLoop);
				boolean found = argumentTypes.length == types.length;
				if (!found) {
					continue;
				}
				for (int i = 0; i < argumentTypes.length; i++) {
					if (!argumentTypes[i].equals(types[i])) {
						continue loopDeclMeth;
					}
				}
				if (!Type.getDescriptor(methodLoop.getReturnType()).equals(returnType.getDescriptor())) {
					LOG.info(String.format("%s: Unexpected return-type %s (%s) instead of %s (%s)",
							invName, 
							methodLoop.getReturnType().getCanonicalName(), methodLoop.getReturnType().getTypeName(),
							returnType.getClassName(), returnType.getDescriptor()));
					continue;
				}
				invMethod = methodLoop;
				break whileSuperClass;
			}
			classObj = classObj.getSuperclass();
		}
		return invMethod;
	}

	/**
	 * Searches a default method-implementation in an interfaces or one of its parents.
	 * @param invName name of the method
	 * @param types types of arguments
	 * @param cInterface class of interface
	 * @return default-method or <code>null</code>
	 */
	private static Method findMethodInInterface(final String invName, final Type[] types, final Class<?> cInterface) {
		//LOG.debug(String.format("  Searching method (%s) in (%s) ...", invName, cInterface));
		Method invMethod = null;
loopDeclMeth:
		for (Method methodLoop : cInterface.getDeclaredMethods()) {
			// synthetic: e.g. lambda$0 of a INVOKEDYNAMIC of a default-method in an interface.
			if (!methodLoop.isDefault() && !methodLoop.isSynthetic()) {
				continue;
			}
			if (!methodLoop.getName().equals(invName)) {
				continue;
			}
			final Type[] argumentTypes = Type.getArgumentTypes(methodLoop);
			boolean found = argumentTypes.length == types.length;
			if (!found) {
				continue;
			}
			for (int i = 0; i < argumentTypes.length; i++) {
				if (!argumentTypes[i].equals(types[i])) {
					continue loopDeclMeth;
				}
			}
			invMethod = methodLoop;
			break;
		}
		if (invMethod == null) {
			for (Class<?> cParent : cInterface.getInterfaces()) {
				invMethod = findMethodInInterface(invName, types, cParent);
				if (invMethod != null) {
					break;
				}
			}
		}
		
		return invMethod;
	}

	static Class<?> getClassArrayViaType(final Type aType, final VM vm, final Class<?> clazz) throws ClassNotFoundException {
		final Type elType = aType.getElementType();
		final Class<?> classArray;
		final int sort = elType.getSort();
		if (sort == Type.BOOLEAN) {
			classArray = boolean.class;
		}
		else if (sort == Type.BYTE) {
			classArray = byte.class;
		}
		else if (sort == Type.CHAR) {
			classArray = char.class;
		}
		else if (sort == Type.DOUBLE) {
			classArray = double.class;
		}
		else if (sort == Type.FLOAT) {
			classArray = float.class;
		}
		else if (sort == Type.INT) {
			classArray = int.class;
		}
		else if (sort == Type.LONG) {
			classArray = long.class;
		}
		else if (sort == Type.SHORT) {
			classArray = short.class;
		}
		else {
			final String nameNew = aType.getClassName().replace("[]", "");
			try {
				classArray = vm.loadClass(nameNew, clazz);
			} catch (ClassNotFoundException e) {
				throw new ClassNotFoundException(String.format("Error while loading class (%s)", nameNew), e);
			}
		}
		return classArray;
	}

	/**
	 * Gets an boolean-, char- or int-value from stack as int-value.
	 * @param stack current stack
	 * @return int-value
	 */
	static int getIntBooleanAsInt(OperandStack pStack) {
		final Object oValue = pStack.pop();
		final int v;
		if (oValue instanceof Integer) {
			v = ((Integer) oValue).intValue();
		}
		else if (oValue instanceof Boolean) {
			v = ((Boolean) oValue).booleanValue() ? 1 : 0;
		}
		else if (oValue == null) {
			throw new JvmException("Unexpected null (expected int-value)");
		}
		else {
			throw new JvmException("Unexpected type " + oValue.getClass());
		}
		return v;
	}

	/**
	 * Checks if a thrown exception is handled by a try-catch-block.
	 * In that case the instruction-index will be set to the catch-block.
	 * @param objRef object-instance
	 * @param methodClass current method
	 * @param eCause thrown exception
	 * @return <code>true</code> if the exception will be handled at the current instruction
	 */
	public boolean handleCatchException(final Throwable eCause) {
		visitor.invokeException(eCause);

		for (final TryCatchBlockNode tcb : method.tryCatchBlocks) {
			// tcb.type == null: type "any"), e.g. finally-block 
			if (tcb.type != null) {
				Class<?> classTcb;
				try {
					classTcb = registry.loadClass(tcb.type.replace("/", "."), clazz);
				} catch (ClassNotFoundException cnfe) {
					throw new JvmException(String.format("Throwable %s in catch-block of %s is unknown",
							tcb.type, methodName), cnfe);
				}
				if (!classTcb.isAssignableFrom(eCause.getClass())) {
					continue;
				}
			}
			final Integer instrStart = mapLabel.get(tcb.start.getLabel());
			final Integer instrEnd = mapLabel.get(tcb.end.getLabel());
			if (instrStart == null) {
				throw new JvmException(String.format("Unknown start-label in tcb of %s: %s, %s - %s",
						methodName, tcb.type, tcb.start.getLabel(), tcb.end.getLabel()));
			}
			if (instrEnd == null) {
				throw new JvmException(String.format("Unknown end-label in tcb of %s: %s, %s - %s",
						methodName, tcb.type, tcb.start.getLabel(), tcb.end.getLabel()));
			}
			if (instrStart.intValue() <= instrNum && instrNum < instrEnd.intValue()) {
				final Integer instrHandler = mapLabel.get(tcb.handler.getLabel());
				if (instrHandler == null) {
					throw new JvmException(String.format("Unknown handler-label (%s) in tcb of %s: %s, %s - %s",
							tcb.handler.getLabel(), methodName,
							tcb.type, tcb.start.getLabel(), tcb.end.getLabel()));
				}
				stack.clear();
				stack.push(eCause);
				instrNum = instrHandler.intValue();
				return true; // continue while
			}
		}
		return false;
	}

	/**
	 * Checks if obj can be casted to the type described by desc.
	 * @param desc type-description
	 * @param obj object to be checked
	 * @return <code>true</code>, if obj can be casted
	 * @throws ClassNotFoundException in case of an unknown class 
	 */
	private boolean handleCheckcast(String desc, Object obj) throws ClassNotFoundException {
		final Class<?> classDesc;
		if (desc.startsWith("[")) {
			if ("[I".equals(desc)) {
				classDesc = int[].class;
			}
			else if ("[B".equals(desc)) {
				classDesc = byte[].class;
			}
			else if ("[C".equals(desc)) {
				classDesc = char[].class;
			}
			else if ("[Z".equals(desc)) {
				classDesc = boolean[].class;
			}
			else if ("[L".equals(desc)) {
				classDesc = long[].class;
			}
			else if ("[S".equals(desc)) {
				classDesc = short[].class;
			}
			else if ("[F".equals(desc)) {
				classDesc = float[].class;
			}
			else if ("[D".equals(desc)) {
				classDesc = double[].class;
			}
			else if (desc.startsWith("[L") && desc.endsWith(";")) {
				final String className = desc.substring(2, desc.length() - 1).replace('/', '.');
				final Class<?> classComp;
				try {
					classComp = registry.loadClass(className, clazz);
				} catch (ClassNotFoundException e) {
					throw new JvmException("Unknown class " + className, e);
				}
				classDesc = Array.newInstance(classComp, 0).getClass();
			}
			else if (desc.startsWith("[")) {
				final Type type = Type.getType(desc);
				final int dim = type.getDimensions();
				final int[] aDims = new int[dim];
				final Class<?> elClass = getClassArrayViaType(type, registry, clazz);
				final Object oArray = Array.newInstance(elClass, aDims);
				classDesc = oArray.getClass();
			}
			else {
				throw new JvmException(String.format("Unexpected type in CHECKCAST (%s) to object.class (%s)",
						desc, (obj != null) ? obj.getClass() : null));
			}
		}
		else {
			try {
				classDesc = registry.loadClass(desc.replace('/', '.'), clazz);
			} catch (ClassNotFoundException e) {
				throw new JvmException("Unknown class " + desc, e);
			}
		}
		return classDesc.isInstance(obj);
	}

	/**
	 * Reads the methods arguments and removes them from the caller's stack.
	 * @param args stack of the caller
	 */
	private void readArgsIntoLocals(final OperandStack args) {
		final int argDefsLen = argDefs.length;
		int j = 0;
		final boolean isStatic = Modifier.isStatic(pMethod.getModifiers());
		if (!isStatic) {
			j++;
		}
		assert aLocals.length >= argDefs.length : String.format("aLocals.len=%d < argDefs.len=%d",
				Integer.valueOf(aLocals.length), Integer.valueOf(argDefs.length));
		for (int i = 0; i < argDefs.length; i++) {
			final Type curType = argDefs[i];
			try {
				Object sValue = args.peek(argDefsLen - 1 - i);
				sValue = convertDeclTypeIntoJvmType(curType, sValue);
				aLocals[j] = sValue;
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new JvmException(String.format("Stack small (%s): i=%d, j=%d, argDefs=%s, aLocals.length=%d, stack=(%s), method=%s",
						e.getMessage(), Integer.valueOf(i), Integer.valueOf(j),
						Arrays.toString(argDefs), Integer.valueOf(aLocals.length), args, pMethod));
			}
			j++;
			if (Type.LONG_TYPE.equals(curType) || Type.DOUBLE_TYPE.equals(curType)) {
				// long and double use two local-indizes.
				j++;
			}
		}
		args.remove(argDefs.length);
		if (!isStatic) {
			aLocals[0] = args.pop();
		}
	}

	/**
	 * Gets the instruction-index of a label.
	 * @param instructions list of instructions
	 * @return map from label to instructions
	 */
	static Map<Label, Integer> createLabelInstructionIndex(final InsnList instructions) {
		final Map<Label, Integer> mapLabel = new HashMap<>();
		final int anzInstructions = instructions.size();
		for (int i = 0; i < anzInstructions; i++) {
			final AbstractInsnNode iLoop = instructions.get(i);
			if (iLoop instanceof LabelNode) {
				final LabelNode loopLabel = (LabelNode) iLoop;
				mapLabel.put(loopLabel.getLabel(), Integer.valueOf(i));
			}
		}
		return mapLabel;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(12);
		sb.append("MF#").append(Integer.toHexString(System.identityHashCode(this)));
		return sb.toString();
	}

}
