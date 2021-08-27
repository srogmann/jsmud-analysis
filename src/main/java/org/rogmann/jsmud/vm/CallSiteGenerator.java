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
 */
public class CallSiteGenerator {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmInvocationHandlerReflection.class);
	
	/** number of generated call-site-classes */
	private final AtomicInteger numCallSites = new AtomicInteger();

	// private-lambda-methods can't be access via other packages.
	/** default-package of generated call-site-classes */
	private final String callSitePackage = System.getProperty(CallSiteGenerator.class.getName() + ".callSitePackage",
			JsmudGeneratedClasses.class.getName().replaceFirst("[.][^.]*$", ""));

	/** class-loader for generated classes */
	private final JsmudClassLoader classLoader;

	/** map from generated class to bytecode used */ 
	private final ConcurrentMap<Class<?>, byte[]> mapBytecodes = new ConcurrentHashMap<>();

	/**
	 * Constructor
	 * @param classLoader class-loader for generated classes
	 */
	public CallSiteGenerator(final JsmudClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/**
	 * Creates a call-site-proxy by generating a class.
	 * @param owner owner-class
	 * @param idin INVOKEDYNAMIC
	 * @param stack current stack 
	 */
	public Object createCallSite(final Class<?> classOwner, final InvokeDynamicInsnNode idin,
			final OperandStack stack) {
		final Class<?> classCallSite = createCallSiteClass(classOwner, idin);
		
		final Type[] callSiteConstrArgs = Type.getArgumentTypes(idin.desc);
		final Constructor<?> constr = classCallSite.getDeclaredConstructors()[0];
		constr.setAccessible(true);
		final Object[] args = new Object[callSiteConstrArgs.length];
		for (int i = args.length - 1; i >= 0; i--) {
			args[i] = stack.pop();
		}
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
	public Class<?> createCallSiteClass(final Class<?> classOwner, final InvokeDynamicInsnNode idin) {
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
		final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
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
		
		// Generate a constructor.
		final Type[] callSiteConstrArgs = Type.getArgumentTypes(idin.desc);
		final String[] fieldNames = new String[callSiteConstrArgs.length];
		{
			for (int i = 0; i < callSiteConstrArgs.length; i++) {
				final int index = i + 1;	
				fieldNames[i] = "field" + index;
				cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldNames[i],
						callSiteConstrArgs[i].getDescriptor(), null, null);
			}
			final String constrDesc = Type.getMethodDescriptor(Type.VOID_TYPE, callSiteConstrArgs);
	
			final MethodVisitor constr = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constrDesc, null, null);
			constr.visitVarInsn(Opcodes.ALOAD, 0);
			constr.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class),
					"<init>", "()V", false);
			for (int i = 0; i < callSiteConstrArgs.length; i++) {
				constr.visitVarInsn(Opcodes.ALOAD, 0);
				final Type arg = callSiteConstrArgs[i];
				final int indexInLocals = 1 + i;
				try {
					loadLocalVariable(constr, indexInLocals, arg, null);
				}
				catch (JvmException e) {
					throw new JvmException(String.format("Error while generating constructor of call-site-class (%s) for call-site-method (%s) in (%s): %s",
							callSiteName, callSiteMethodDescRuntime, classOwner, e.getMessage()));
				}
				constr.visitFieldInsn(Opcodes.PUTFIELD, callSiteNameInt, fieldNames[i], arg.getDescriptor());
			}
			constr.visitInsn(Opcodes.RETURN);
			constr.visitMaxs(0, 0);
			constr.visitEnd();
		}

		// Generate the method.
		{
			final MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, callSiteMethodName,
					callSiteMethodDescRuntime.getDescriptor(), null, null);
			mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
			if (methodHandle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
				mv.visitTypeInsn(Opcodes.NEW, methodHandle.getOwner());
				mv.visitInsn(Opcodes.DUP);
			}
			else if (methodHandle.getTag() == Opcodes.H_INVOKEVIRTUAL && callSiteConstrArgs.length == 0) {
				mv.visitVarInsn(Opcodes.ALOAD, 0); // instance
			}
			for (int i = 0; i < callSiteConstrArgs.length; i++) {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				final Type arg = callSiteConstrArgs[i];
				mv.visitFieldInsn(Opcodes.GETFIELD, callSiteNameInt, fieldNames[i], arg.getDescriptor());
			}
			for (int i = 0; i < callSiteMethodArgsRuntime.length; i++) {
				final Type arg = callSiteMethodArgsRuntime[i];
				final Type argCompile = callSiteMethodArgsCompile[i];
				final int indexInLocals = 1 + i;
				try {
					loadLocalVariable(mv, indexInLocals, arg, argCompile);
				}
				catch (JvmException e) {
					throw new JvmException(String.format("Error while processing call-site-method (%s) in (%s): %s",
							callSiteMethodDescRuntime, classOwner, e.getMessage()));
				}
			}
			if (methodHandle.getTag() == Opcodes.H_INVOKEVIRTUAL) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else if (methodHandle.getTag() == Opcodes.H_INVOKESTATIC) {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else if (methodHandle.getTag() == Opcodes.H_INVOKESPECIAL && methodHandle.isInterface()) {
				mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else if (methodHandle.getTag() == Opcodes.H_INVOKESPECIAL) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else if (methodHandle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else if (methodHandle.getTag() == Opcodes.H_INVOKEINTERFACE) {
				mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, methodHandle.getOwner(),
						methodHandle.getName(), methodHandle.getDesc(), methodHandle.isInterface());
			}
			else {
				throw new JvmException(String.format("Unexpected tag (%s) of call-site-method (%s) in (%s)",
						Integer.valueOf(methodHandle.getTag()), methodHandle, classOwner));
			}
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
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		cw.visitEnd();
		
		final byte[] bufClass = cw.toByteArray();
		try {
			Files.write(new File("/tmp/CallSite1.class").toPath(), bufClass);
		} catch (IOException e) {
			e.printStackTrace();
		}
		final Class<?> classCallSite = classLoader.defineJsmudClass(callSiteName, bufClass);
		mapBytecodes.put(classCallSite, bufClass);
		return classCallSite;
	}

	/**
	 * Loads a local-variable onto the stack.
	 * @param mv method-visitor
	 * @param indexInLocals index of local variable
	 * @param arg type of argument
	 * @param argCompile optional type be casted to
	 * @throws JvmException in case of an unexpected argument-type
	 */
	private static void loadLocalVariable(final MethodVisitor mv, final int indexInLocals, final Type arg, final Type argCompile) throws JvmException {
		if (arg.getSort() == Type.OBJECT) {
			mv.visitVarInsn(Opcodes.ALOAD, indexInLocals);
			if (argCompile != null && "java/lang/Object".equals(arg.getInternalName()) && !arg.equals(argCompile)) {
				mv.visitTypeInsn(Opcodes.CHECKCAST, argCompile.getInternalName());
			}
		}
		else if (Type.INT_TYPE.equals(arg)) {
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
