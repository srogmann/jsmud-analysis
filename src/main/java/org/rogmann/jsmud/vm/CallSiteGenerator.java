package org.rogmann.jsmud.vm;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.rogmann.jsmud.gen.JsmudGeneratedClasses;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Generates a call-site-class whose instances are used to build the result
 * of an INVOKEDYNAMIC-instruction.
 * 
 * <p>This class is a partial replacement of java.lang.invoke.LambdaMetafactory.</p>
 * @see java.lang.invoke.MethodHandle
 * @see java.lang.invoke.CallSite
 * @see CallSiteSimulation
 */
public class CallSiteGenerator {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmInvocationHandlerReflection.class);

	/** optional folder used to dump generated class-site-classes */
	private static final String FOLDER_DUMP_CALL_SITES = System.getProperty(CallSiteGenerator.class.getName() + ".folderCallSites");

	/** type of the {@link CallSiteContext}-instance */
	private static final Type TYPE_CALL_SITE_CONTEXT = Type.getType(CallSiteContext.class);

	/**
	 * static field to check if a method is executed by JSMUD
	 * (the instruction GETSTATIC is patched to return the correct value)
	 */
	public static final String FIELD_IS_EXECUTED_BY_JSMUD = "__IS_EXECUTED_BY_JSMUD";

	/** name of field "callSiteContext" */
	private static final String FIELD_CALL_SITE_CONTEXT = "callSiteContext";

	/** number of generated call-site-classes */
	private final AtomicInteger numCallSites = new AtomicInteger();

	// private-lambda-methods can't be access via other packages.
	/** default-package of generated call-site-classes */
	private final String callSitePackage = System.getProperty(CallSiteGenerator.class.getName() + ".callSitePackage",
			JsmudGeneratedClasses.class.getName().replaceFirst("[.][^.]*$", ""));

	/** class-loader for generated classes */
	private final JsmudClassLoader classLoader;

	/** VM (for loading classes) */
	private final VM vm;

	/** map from generated class to bytecode used */ 
	private final ConcurrentMap<Class<?>, byte[]> mapBytecodes = new ConcurrentHashMap<>();

	/** map from INVOKEDYNAMIC-instruction to call-site */
	private final ConcurrentMap<InvokeDynamicInstructionKey, Class<?>> mapCallSiteClasses = new ConcurrentHashMap<>();

	/**
	 * Internal key-class (internal because of non-considered equals-contract).
	 */
	static class InvokeDynamicInstructionKey {
		private final Class<?> classOwner;
		private final InvokeDynamicInsnNode idi;
		InvokeDynamicInstructionKey(final Class<?> classOwner, final InvokeDynamicInsnNode idi) {
			this.classOwner = classOwner;
			this.idi = idi;
		}
		@Override
		public int hashCode() {
			return classOwner.hashCode() * 37 + idi.hashCode();
		}
		@Override
		public boolean equals(Object otherObj) {
			if (this == otherObj) {
				return true;
			}
			final InvokeDynamicInstructionKey otherKey = (InvokeDynamicInstructionKey) otherObj;
			return classOwner.equals(otherKey.classOwner) && idi.equals(otherKey.idi);
		}
		
	}

	/**
	 * Constructor
	 * @param classLoader class-loader for generated classes
	 */
	public CallSiteGenerator(final JsmudClassLoader classLoader, final VM vm) {
		this.classLoader = classLoader;
		this.vm = vm;
	}

	/**
	 * Creates a call-site-proxy by generating a class.
	 * @param registry class-registry (VM)
	 * @param owner owner-class
	 * @param idin INVOKEDYNAMIC
	 * @param stack current stack 
	 */
	public Object createCallSite(ClassRegistry registry, final Class<?> classOwner, final InvokeDynamicInsnNode idin,
			final OperandStack stack) {
		final Handle bsm = idin.bsm;
		if ("java/lang/runtime/SwitchBootstraps".equals(bsm.getOwner())
				&& "typeSwitch".equals(bsm.getName())
				&& "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;".equals(bsm.getDesc())) {
			return createCallSiteClassTypeSwitch(classOwner, idin, stack);
		}
		final InvokeDynamicInstructionKey key = new InvokeDynamicInstructionKey(classOwner, idin);
		Class<?> classCallSite = mapCallSiteClasses.get(key);
		if (classCallSite == null) {
			// In a multi-threaded environment this computeIfAbsent-less implementation could result in several call-site-classes.
			classCallSite = createCallSiteClassMetafactory(classOwner, idin);
			mapCallSiteClasses.put(key, classCallSite);
		}
		
		final Type[] callSiteConstrArgs = Type.getArgumentTypes(idin.desc);
		final Constructor<?> constr = classCallSite.getDeclaredConstructors()[0];
		constr.setAccessible(true);
		final Object[] args = new Object[1 + callSiteConstrArgs.length];
		final Handle methodHandle = (Handle) idin.bsmArgs[1];
		final CallSiteContext callSiteContext = new CallSiteContext(registry, classOwner, methodHandle);
		for (int i = args.length - 1; i > 0; i--) {
			args[i] = stack.pop();
		}
		args[0] = callSiteContext;
		final Object callSite;
		try {
			callSite = constr.newInstance(args);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new JvmException(String.format("Can't instanciate call-site (%s) for class (%s)",
					classCallSite, classOwner), e);
		}
		return callSite;
	}
	
	/**
	 * Creates a call-site-proxy by generating a class.
	 * @param owner owner-class
	 * @param idin INVOKEDYNAMIC
	 * @return class of call-site
	 */
	public Class<?> createCallSiteClassMetafactory(final Class<?> classOwner, final InvokeDynamicInsnNode idin) {
		final Handle bsm = idin.bsm;
		final int tag = bsm.getTag();
		if (!("java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())
				&& "metafactory".equals(bsm.getName())
				&& "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;".equals(bsm.getDesc()))) {
			throw new JvmException(String.format("Unsupported bootstrap-method in owner-class %s: tag=%d, bsm.owner=%s, bsm.method=%s%s",
					classOwner, Integer.valueOf(tag), bsm.getOwner(), bsm.getName(), bsm.getDesc()));
		}
		if (idin.bsmArgs == null || idin.bsmArgs.length != 3 || !(idin.bsmArgs[1] instanceof Handle)) {
			throw new JvmException(String.format("Unexpected bsm-arguments in owner-class %s: bsm.tag=%d, bsm.args=%s",
					classOwner, Integer.valueOf(tag), Arrays.toString(idin.bsmArgs)));
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("createCallSite: owner.clazz=%s, method=%s%s, bsm.tag=%d, bsm.isInterface=%s, idin.args=%s",
					classOwner, idin.name, idin.desc, Integer.valueOf(tag), Boolean.toString(bsm.isInterface()),
					Arrays.toString(idin.bsmArgs)));
		}
		final Type typeInterface = Type.getReturnType(idin.desc);
		if (typeInterface.getSort() != Type.OBJECT) {
			throw new JvmException(String.format("Unexpected return-type of idin-method %s%s in owner-class %s: bsm.tag=%d, bsm.args=%s",
					idin.name, idin.desc, classOwner, Integer.valueOf(tag), Arrays.toString(idin.bsmArgs)));
		}
		// Uncommented code to load the interface-class.
		//ClassLoader clInterface = classOwner.getClassLoader();
		//if (clInterface == null) {
		//	clInterface = classLoader;
		//}
		//final Class<?> classIntf;
		//try {
		//	classIntf = clInterface.loadClass(typeInterface.getClassName());
		//} catch (ClassNotFoundException e1) {
		//	throw new JvmException(String.format("Can't load class (%s) of idin-method %s%s in owner-class %s: bsm.tag=%d, bsm.args=%s",
		//			typeInterface.getClassName(), idin.name, idin.desc, classOwner, Integer.valueOf(tag), Arrays.toString(idin.bsmArgs)));
		//}
		//final String cCallSitePackage = classOwner.getName().replaceFirst("[.][^.]*$", "");
		final int callSiteIdx = numCallSites.incrementAndGet();
		final String callSiteName = String.format("%s.CallSite%d_%s", callSitePackage,
				Integer.valueOf(callSiteIdx), classOwner.getSimpleName());
		// Appending the class-name is not allowed in JRE-classes.
		//final String callSiteName = String.format("%s$$Lambda$%d", classOwner.getName(),
		//		Integer.valueOf(callSiteIdx), classOwner.getSimpleName());
		final String callSiteNameInt = callSiteName.replace('.', '/');
		final ClassWriter cw = new ClassWriterCallSite(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
				classLoader, callSiteNameInt);
		final String[] aInterfaces = { typeInterface.getInternalName() };
		cw.visit(Opcodes.V1_8, Opcodes.ACC_FINAL + Opcodes.ACC_SUPER +  Opcodes.ACC_SYNTHETIC,
				callSiteNameInt, null, Type.getInternalName(Object.class), aInterfaces);

		final Handle methodHandle = (Handle) idin.bsmArgs[1];

		// name of the method of the call-site-instance.
		final String callSiteMethodName = idin.name;
		final Type callSiteMethodDescRuntime = (Type) idin.bsmArgs[0];
		final Type callSiteMethodDescCompile = (Type) idin.bsmArgs[2];
		final Type[] callSiteMethodArgsRuntime = callSiteMethodDescRuntime.getArgumentTypes();
		final Type[] callSiteMethodArgsCompile = callSiteMethodDescCompile.getArgumentTypes();
		if (callSiteMethodArgsRuntime.length != callSiteMethodArgsCompile.length) {
			throw new JvmException(String.format("Unexpected argument-counts of runtime-time-method (%s) and compile-time-method (%s) in owner-class %s: bsm.tag=%d, bsm.args=%s",
					callSiteMethodDescRuntime, callSiteMethodDescCompile, classOwner, Integer.valueOf(tag), Arrays.toString(idin.bsmArgs)));
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("createCallSite: idin.desc=%s, methodDescRuntime=%s, methodDescCompile=%s",
					idin.desc, callSiteMethodDescRuntime, callSiteMethodDescCompile));
		}

		// Generate a jsmud-internal field.
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, FIELD_IS_EXECUTED_BY_JSMUD,
				Type.BOOLEAN_TYPE.getDescriptor(), null, null);

		final Type typeCallSiteContext = Type.getType(CallSiteContext.class);
		cw.visitField(Opcodes.ACC_PRIVATE, FIELD_CALL_SITE_CONTEXT, typeCallSiteContext.getDescriptor(), null, null);

		// Generate fields to store the constructor's arguments.
		final Type[] callSiteConstrArgs = Type.getArgumentTypes(idin.desc);
		final String[] fieldNames = new String[callSiteConstrArgs.length];
		for (int i = 0; i < callSiteConstrArgs.length; i++) {
			final int index = i + 1;	
			fieldNames[i] = "field" + index;
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldNames[i],
					callSiteConstrArgs[i].getDescriptor(), null, null);
		}

		// Generate a constructor.
		generateConstructor(classOwner, callSiteName, callSiteNameInt, cw, callSiteMethodDescRuntime,
				callSiteConstrArgs, fieldNames);

		// Generate the method.
		generateHandleMethod(classOwner, callSiteNameInt, cw, methodHandle, callSiteMethodName,
				callSiteMethodDescRuntime, callSiteMethodArgsRuntime, callSiteMethodArgsCompile,
				callSiteConstrArgs, fieldNames);

		cw.visitEnd();
		
		final byte[] bufClass = cw.toByteArray();
		if (FOLDER_DUMP_CALL_SITES != null) {
			final File fileCallSiteClass = new File(FOLDER_DUMP_CALL_SITES, callSiteName + ".class");
			LOG.debug(String.format("Dump call-site-class into (%s)", fileCallSiteClass));
			try {
				Files.write(fileCallSiteClass.toPath(), bufClass);
			} catch (IOException e) {
				throw new JvmException(String.format("IO-error while dumping class (%s) into file (%s)",
						callSiteName, fileCallSiteClass), e);
			}
		}
		final Class<?> classCallSite = classLoader.defineJsmudClass(callSiteName, bufClass);
		mapBytecodes.put(classCallSite, bufClass);
		return classCallSite;
	}

	/**
	 * Creates a call-site of a typeSwitch ("pattern-matching-switch") by generating a class.
	 * The call-site is constant, the target will be executed at once. Therefore an integer is put onto the stack.
	 * @param owner owner-class
	 * @param idin INVOKEDYNAMIC
	 * @param stack current stack
	 */
	Integer createCallSiteClassTypeSwitch(final Class<?> classOwner, final InvokeDynamicInsnNode idin,
			final OperandStack stack) {
		final Handle bsm = idin.bsm;
		final int tag = bsm.getTag();
		if (LOG.isDebugEnabled()) {
			// createCallSiteClassTypeSwitch: bsm.args=[Ljava/lang/Integer;, Ljava/lang/Integer;, Ljava/lang/String;]
			// createCallSiteClassTypeSwitch: bsm.args.class=[class org.objectweb.asm.Type, class org.objectweb.asm.Type, class org.objectweb.asm.Type]
			LOG.debug(String.format("createCallSiteClassTypeSwitch: bsm.args=%s", Arrays.toString(idin.bsmArgs)));
		}
		if (idin.bsmArgs == null) {
			throw new JvmException(String.format("Missing bsm-arguments in owner-class %s: bsm.tag=%d",
					classOwner, Integer.valueOf(tag)));
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("createCallSiteClassTypeSwitch: owner.clazz=%s, method=%s%s, bsm.tag=%d, bsm.isInterface=%s, idin.args=%s",
					classOwner, idin.name, idin.desc, Integer.valueOf(tag), Boolean.toString(bsm.isInterface()),
					Arrays.toString(idin.bsmArgs)));
		}
		if (!"(Ljava/lang/Object;I)I".equals(idin.desc)) {
			// A labels-array "Object[] labels" isn't supported yet.
			throw new JvmException(String.format("Unexpected typeSwitch-description %s", idin.desc));
		}

		// See java.lang.runtime.SwitchBootstraps.doSwitch(Object, int, Object[])
		final Object[] labels = idin.bsmArgs;
		final int startIndex = ((Integer) stack.pop()).intValue();
		final Object target = stack.pop();
		int result;
		if (target == null) {
			result = -1;
		}
		else {
			result = labels.length;
			final Class<? extends Object> targetClass = target.getClass();
			for(int i=startIndex; i<labels.length; i++) {
				final Object label = labels[i];
				if (label instanceof Type) {
					final Type type = (Type) label;
					final String typeClassName = type.getClassName();
					if (typeClassName.equals(targetClass.getName())) {
						result = i;
						break;
					}
					final Class<?> classLabel;
					try {
						classLabel = vm.loadClass(typeClassName, classOwner);
					} catch (ClassNotFoundException e) {
						throw new JvmException(String.format("Can't load class (%s) of label %d for (%s)",
								typeClassName, Integer.valueOf(i), classOwner), e);
					}
					if (classLabel.isAssignableFrom(targetClass)) {
						result = i;
						break;
					}
				}
				else if (label instanceof Integer) {
					final Integer constant = (Integer) label;
					if (target instanceof Number && ((Number) target).intValue() == constant.intValue()) {
						result = i;
						break;
					}
					if (target instanceof Character && ((Character) target).charValue() == constant.intValue()) {
						result = i;
						break;
					}
					
				}
			}
		}
		return Integer.valueOf(result);
	}

	/**
	 * Generates a constructor accepting the INVOKEDYNAMIC-arguments.
	 * @param classOwner owner-class
	 * @param callSiteName name of call-site-class
	 * @param callSiteNameInt internal name of call-site-class
	 * @param cw class-writer
	 * @param callSiteMethodDescRuntime description of call-site-method
	 * @param callSiteConstrArgs arguments of call-site-constructor
	 * @param fieldNames internal field-names
	 */
	private static void generateConstructor(final Class<?> classOwner, final String callSiteName, final String callSiteNameInt,
			final ClassWriter cw, final Type callSiteMethodDescRuntime,
			final Type[] callSiteConstrArgs, final String[] fieldNames) {
		// The arguments of the constructor are a CallSiteContext-instance
		// followed by the INVOKEDYNAMIC-arguments (idin.desc).
		final Type[] contextAndConstrArgs = new Type[callSiteConstrArgs.length + 1];
		contextAndConstrArgs[0] = TYPE_CALL_SITE_CONTEXT;
		System.arraycopy(callSiteConstrArgs, 0, contextAndConstrArgs, 1, callSiteConstrArgs.length);
		final String constrDesc = Type.getMethodDescriptor(Type.VOID_TYPE, contextAndConstrArgs);

		final MethodVisitor constr = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constrDesc, null, null);
		constr.visitVarInsn(Opcodes.ALOAD, 0);
		constr.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class),
				"<init>", "()V", false);
		// Load callSiteContext.
		constr.visitVarInsn(Opcodes.ALOAD, 0);
		constr.visitVarInsn(Opcodes.ALOAD, 1);
		constr.visitFieldInsn(Opcodes.PUTFIELD, callSiteNameInt, FIELD_CALL_SITE_CONTEXT, contextAndConstrArgs[0].getDescriptor());
		// Load the INVOKEDYNAMIC-arguments
		int indexInLocals = 2;
		for (int i = 0; i < callSiteConstrArgs.length; i++) {
			constr.visitVarInsn(Opcodes.ALOAD, 0);
			final Type arg = callSiteConstrArgs[i];
			try {
				loadLocalVariable(constr, indexInLocals, arg, null);
			}
			catch (JvmException e) {
				throw new JvmException(String.format("Error while generating constructor of call-site-class (%s) for call-site-method (%s) in (%s): %s",
						callSiteName, callSiteMethodDescRuntime, classOwner, e.getMessage()));
			}
			constr.visitFieldInsn(Opcodes.PUTFIELD, callSiteNameInt, fieldNames[i], arg.getDescriptor());
			indexInLocals += (Type.LONG_TYPE.equals(arg) || Type.DOUBLE_TYPE.equals(arg)) ? 2 : 1;
		}
		constr.visitInsn(Opcodes.RETURN);
		constr.visitMaxs(0, 0);
		constr.visitEnd();
	}

	/**
	 * Creates the handle-method called at execution of the call-site-instance.
	 * The method has two branches: One branch calls the lambda-function at once,
	 * the other branch uses JSMUD to execute the lambda-function.
	 *  
	 * @param classOwner owner-class
	 * @param callSiteNameInt internal name of the call-site-class
	 * @param cw class-writer
	 * @param methodHandle method-handle
	 * @param callSiteMethodName name of the handle-method
	 * @param callSiteMethodDescRuntime method-description (at runtime)
	 * @param callSiteMethodArgsRuntime method-arguments at runtime
	 * @param callSiteMethodArgsCompile method-arguments at compile-time
	 * @param callSiteConstrArgs constructor-arguments
	 * @param fieldNames internal field-names
	 */
	private static void generateHandleMethod(final Class<?> classOwner, final String callSiteNameInt, final ClassWriter cw,
			final Handle methodHandle, final String callSiteMethodName, final Type callSiteMethodDescRuntime,
			final Type[] callSiteMethodArgsRuntime, final Type[] callSiteMethodArgsCompile,
			final Type[] callSiteConstrArgs, final String[] fieldNames) {
		final MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, callSiteMethodName,
				callSiteMethodDescRuntime.getDescriptor(), null, null);
		mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
		final Label labelJsmudExec = new Label();
		final Label labelReturn = new Label();
		mv.visitFieldInsn(Opcodes.GETSTATIC, callSiteNameInt, FIELD_IS_EXECUTED_BY_JSMUD,
				Type.BOOLEAN_TYPE.getDescriptor());
		mv.visitJumpInsn(Opcodes.IFEQ, labelJsmudExec);

		// Execution via INVOKE-instruction.
		// This branch is called if JSMUD is executing the method.
		loadInstanceAndArguments(mv, methodHandle, classOwner, callSiteNameInt, callSiteMethodDescRuntime,
				callSiteMethodArgsRuntime, callSiteMethodArgsCompile, callSiteConstrArgs, fieldNames);
		final int opcodeInvoke = CallSiteContext.lookupInvokeOpcode(classOwner, methodHandle);
		mv.visitMethodInsn(opcodeInvoke, methodHandle.getOwner(),
				methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("generateHandleMethod: methodHandle=%s, callSiteMethodDescRuntime=%s", methodHandle, callSiteMethodDescRuntime));
		}
		
		if (Type.VOID_TYPE.equals(callSiteMethodDescRuntime.getReturnType())
				&& !Type.VOID_TYPE.equals(Type.getReturnType(methodHandle.getDesc()))) {
			// remove the void-object from stack.
			mv.visitInsn(Opcodes.POP);
		}
		mv.visitJumpInsn(Opcodes.GOTO, labelReturn);

		// Generate execution via JSMUD.
		// This branch is called if JSMUD should take control of execution
		// (e.g. after calling java.util.stream).
		mv.visitLabel(labelJsmudExec);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, callSiteNameInt, FIELD_CALL_SITE_CONTEXT, TYPE_CALL_SITE_CONTEXT.getDescriptor());
		mv.visitLdcInsn(Integer.valueOf(callSiteConstrArgs.length + callSiteMethodArgsRuntime.length));
		mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getType(Object.class).getInternalName());

		for (int i = 0; i < callSiteConstrArgs.length; i++) {
			mv.visitInsn(Opcodes.DUP);
			mv.visitLdcInsn(Integer.valueOf(i));
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			final Type arg = callSiteConstrArgs[i];
			mv.visitFieldInsn(Opcodes.GETFIELD, callSiteNameInt, fieldNames[i], arg.getDescriptor());
			convertPrimitiveToObject(mv, arg);
			mv.visitInsn(Opcodes.AASTORE);
		}
		int indexInLocals = 1;
		for (int i = 0; i < callSiteMethodArgsRuntime.length; i++) {
			mv.visitInsn(Opcodes.DUP);
			mv.visitLdcInsn(Integer.valueOf(callSiteConstrArgs.length + i));
			final Type arg = callSiteMethodArgsRuntime[i];
			final Type argCompile = callSiteMethodArgsCompile[i];
			try {
				loadLocalVariable(mv, indexInLocals, arg, argCompile);
			}
			catch (JvmException e) {
				throw new JvmException(String.format("Error while processing call-site-method (%s) in (%s): %s",
						callSiteMethodDescRuntime, classOwner, e.getMessage()));
			}
			convertPrimitiveToObject(mv, arg);
			mv.visitInsn(Opcodes.AASTORE);
			indexInLocals += (Type.LONG_TYPE.equals(arg) || Type.DOUBLE_TYPE.equals(arg)) ? 2 : 1;
		}

		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TYPE_CALL_SITE_CONTEXT.getInternalName(), "executeMethod",
				"([Ljava/lang/Object;)Ljava/lang/Object;", false);
		final int sortReturn = callSiteMethodDescRuntime.getReturnType().getSort();
		switch (sortReturn) {
		case Type.VOID:
			// remove the void-object from stack.
			mv.visitInsn(Opcodes.POP);
			break;
		case Type.INT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Integer.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Integer.class).getInternalName(), "intValue", "()I", false);
			break;
		case Type.BOOLEAN:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Boolean.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Boolean.class).getInternalName(), "booleanValue", "()Z", false);
			break;
		case Type.CHAR:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Character.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Character.class).getInternalName(), "charValue", "()C", false);
			break;
		case Type.SHORT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Short.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Short.class).getInternalName(), "shortValue", "()S", false);
			break;
		case Type.LONG:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Long.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Long.class).getInternalName(), "longValue", "()J", false);
			break;
		case Type.FLOAT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Float.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Float.class).getInternalName(), "floatValue", "()F", false);
			break;
		case Type.DOUBLE:
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Double.class).getInternalName());
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Double.class).getInternalName(), "doubleValue", "()D", false);
			break;
		case Type.ARRAY:
		case Type.OBJECT:
			if (!Type.getType(Object.class).equals(callSiteMethodDescRuntime.getReturnType())) {
				mv.visitTypeInsn(Opcodes.CHECKCAST, callSiteMethodDescRuntime.getReturnType().getInternalName());
			}
			break;
		default:
			throw new JvmException(String.format("Unexpected return-type (%s) of call-site-method (%s) in (%s)",
					callSiteMethodDescRuntime.getReturnType(), callSiteMethodDescRuntime.getDescriptor(), classOwner));
		}

		// Send the return-value
		mv.visitLabel(labelReturn);
		final Type returnType = callSiteMethodDescRuntime.getReturnType();
		if (Type.VOID_TYPE.equals(returnType)) {
			mv.visitInsn(Opcodes.RETURN);
		}
		else if (returnType.getSort() == Type.OBJECT) {
			mv.visitInsn(Opcodes.ARETURN);
		}
		else if (Type.INT_TYPE.equals(returnType)
				|| Type.BOOLEAN_TYPE.equals(returnType)
				|| Type.CHAR_TYPE.equals(returnType)
				|| Type.SHORT_TYPE.equals(returnType)) {
			mv.visitInsn(Opcodes.IRETURN);
		}
		else if (Type.LONG_TYPE.equals(returnType)) {
			mv.visitInsn(Opcodes.LRETURN);
		}
		else if (Type.FLOAT_TYPE.equals(returnType)) {
			mv.visitInsn(Opcodes.FRETURN);
		}
		else if (Type.DOUBLE_TYPE.equals(returnType)) {
			mv.visitInsn(Opcodes.DRETURN);
		}
		else {
			throw new JvmException(String.format("Unexpected return-type (%s) of call-site-method (%s) in (%s)",
					returnType, methodHandle, classOwner));
		}

		// close the method-generation.
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * Converts a primitive-type on stack into the corresponding object-type.
	 * @param mv method-visitor
	 * @param arg type of topmost type
	 */
	private static void convertPrimitiveToObject(final MethodVisitor mv, final Type arg) {
		if (Type.BOOLEAN_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Boolean.class),
					"valueOf", "(Z)Ljava/lang/Boolean;", false);
		}
		else if (Type.CHAR_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Character.class),
					"valueOf", "(C)Ljava/lang/Character;", false);
		}
		else if (Type.SHORT_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Short.class),
					"valueOf", "(S)Ljava/lang/Short;", false);
		}
		else if (Type.INT_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Integer.class),
					"valueOf", "(I)Ljava/lang/Integer;", false);
		}
		else if (Type.LONG_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Long.class),
					"valueOf", "(J)Ljava/lang/Long;", false);
		}
		else if (Type.FLOAT_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Float.class),
					"valueOf", "(F)Ljava/lang/Float;", false);
		}
		else if (Type.DOUBLE_TYPE.equals(arg)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Double.class),
					"valueOf", "(D)Ljava/lang/Double;", false);
		}
	}

	/**
	 * Loads instance and arguments to call the lambda-function.
	 * @param mv method-visitor
	 * @param methodHandle method-handle
	 * @param classOwner owner-class
	 * @param callSiteNameInt internal name of call-site-class
	 * @param callSiteMethodDescRuntime method-description (runtime)
	 * @param callSiteMethodArgsRuntime method-arguments (runtime)
	 * @param callSiteMethodArgsCompile method-arguments (compile-time)
	 * @param callSiteConstrArgs constructor-arguments
	 * @param fieldNames field-names
	 */
	private static void loadInstanceAndArguments(final MethodVisitor mv, final Handle methodHandle, final Class<?> classOwner,
			final String callSiteNameInt, final Type callSiteMethodDescRuntime, final Type[] callSiteMethodArgsRuntime,
			final Type[] callSiteMethodArgsCompile, final Type[] callSiteConstrArgs, final String[] fieldNames) {
		if (methodHandle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
			mv.visitTypeInsn(Opcodes.NEW, methodHandle.getOwner());
			mv.visitInsn(Opcodes.DUP);
		}
		for (int i = 0; i < callSiteConstrArgs.length; i++) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			final Type arg = callSiteConstrArgs[i];
			mv.visitFieldInsn(Opcodes.GETFIELD, callSiteNameInt, fieldNames[i], arg.getDescriptor());
		}
		int indexInLocals = 1;
		for (int i = 0; i < callSiteMethodArgsRuntime.length; i++) {
			final Type arg = callSiteMethodArgsRuntime[i];
			final Type argCompile = callSiteMethodArgsCompile[i];
			try {
				loadLocalVariable(mv, indexInLocals, arg, argCompile);
			}
			catch (JvmException e) {
				throw new JvmException(String.format("Error while processing call-site-method (%s) in (%s): %s",
						callSiteMethodDescRuntime, classOwner, e.getMessage()));
			}
			indexInLocals += (Type.LONG_TYPE.equals(arg) || Type.DOUBLE_TYPE.equals(arg)) ? 2 : 1;
		}
	}

	/**
	 * Loads a local-variable onto the stack.
	 * @param mv method-visitor
	 * @param indexInLocals index of local variable
	 * @param arg type of argument
	 * @param argCompile optional type be casted to
	 * @throws JvmException in case of an unexpected argument-type
	 */
	public static void loadLocalVariable(final MethodVisitor mv, final int indexInLocals, final Type arg, final Type argCompile) throws JvmException {
		if (arg.getSort() == Type.OBJECT) {
			mv.visitVarInsn(Opcodes.ALOAD, indexInLocals);
			if (argCompile != null && "java/lang/Object".equals(arg.getInternalName()) && !arg.equals(argCompile)) {
				mv.visitTypeInsn(Opcodes.CHECKCAST, argCompile.getInternalName());
			}
		}
		else if (Type.INT_TYPE.equals(arg)
				|| Type.BOOLEAN_TYPE.equals(arg)
				|| Type.CHAR_TYPE.equals(arg)
				|| Type.SHORT_TYPE.equals(arg)) {
			mv.visitVarInsn(Opcodes.ILOAD, indexInLocals);
		}
		else if (Type.LONG_TYPE.equals(arg)) {
			mv.visitVarInsn(Opcodes.LLOAD, indexInLocals);
		}
		else if (Type.FLOAT_TYPE.equals(arg)) {
			mv.visitVarInsn(Opcodes.FLOAD, indexInLocals);
		}
		else if (Type.DOUBLE_TYPE.equals(arg)) {
			mv.visitVarInsn(Opcodes.DLOAD, indexInLocals);
		}
		else {
			throw new JvmException(String.format("Unexpected argument-type (%s) at local-index (%d)",
					arg, Integer.valueOf(indexInLocals)));
		}
	}

	/**
	 * Returns the bytecode of a generated class.
	 * The method returns <code>null</code> if the given class isn't generated by this class.
	 * @param clazz class
	 * @return bytecode or <code>null</code>
	 */
	public byte[] getBytecode(final Class<?> clazz) {
		return mapBytecodes.get(clazz);
	}
}
