package org.rogmann.jsmud.vm;

import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Builds a .class-file based on a class-definition including fields and methods.
 * The methods will get an empty respective minimal body.
 * 
 * <p>In case of a given method-executor the method bodies get filled with a call of the method-executor.
 * This can be used to put an instance of the generated class into JRE-code but get control
 * at execution of this class' methods.</p>
 * 
 * <p>The interface of this class doesn't have asm-dependencies.</p>
 */
public class ClassOutlineGenerator {

	/** static field to store the method-executor */
	public static final String FIELD_EXECUTOR = "__JSMUD_EXECUTOR";

	/** static field to store the array of executables */
	public static final String FIELD_EXECUTABLES = "__JSMUD_EXECUTABLES";

	/** class-writer */
	private final ClassWriter cw;

	/** internal name of class */
	private final String classNameInt;
	/** internal name of super-class */
	private final String superClassNameInt;

	/** optional method-executor */
	private final MethodExecutor methodExecutor;

	/** definition of a field */
	public static class ClassFieldDefinition {
		/** access-flags (modifiers) */
		final int accessFlags;
		/** name of the field */
		final String name;
		/** field's descriptor */
		final String descriptor;
		/** field's signature */
		final String signature;
		/**
		 * Constructor
		 * @param accessFlags access-flags (modifiers)
		 * @param name field-name
		 * @param descriptor field's descriptor
		 * @param signature field's signature (may be <code>null</code> if there are no generics)
		 */
		public ClassFieldDefinition(final int accessFlags, final String name, final String descriptor, final String signature) {
			this.accessFlags = accessFlags;
			this.name = name;
			this.descriptor = descriptor;
			this.signature = signature;
		}
	}

	/** definition of a method (without body) */
	public static class ClassMethodDefinition {
		/** access-flags (modifiers) */
		final int accessFlags;
		/** name of the field */
		final String name;
		/** field's descriptor */
		final String descriptor;
		/** field's signature */
		final String signature;
		/** types of parameters */
		final String[] paramTypes;
		/** return-type */
		final String returnType;
		/** exceptions (internal names) */
		final String[] exceptions;
		/**
		 * Constructor
		 * @param accessFlags access-flags (modifiers)
		 * @param name field-name
		 * @param descriptor field's descriptor
		 * @param signature field's signature (may be <code>null</code> if there are no generics)
		 * @param paramTypes parameter-types of the method
		 * @param returnType return-type of the method
		 * @param exceptions internal names of the exceptions thrown
		 */
		public ClassMethodDefinition(final int accessFlags, final String name, final String descriptor, final String signature,
				final String[] paramTypes, final String returnType, final String[] exceptions) {
			this.accessFlags = accessFlags;
			this.name = name;
			this.descriptor = descriptor;
			this.signature = signature;
			this.paramTypes = paramTypes;
			this.returnType = returnType;
			this.exceptions = exceptions;
		}
	}
	
	/**
	 * Creates the JDK-bytecode (i.e. not dalvik) of a class-hull.
	 * @param accessFlags access-flags of the class
	 * @param classNameInt internal class-name
	 * @param superClassNameInt internal name of super-class
	 * @param aInterfaces internal names of interfaces
	 * @param methodExecutor optional method-executor
	 */
	public ClassOutlineGenerator(final int accessFlags, final String classNameInt, final String superClassNameInt,
			final String[] aInterfaces, MethodExecutor methodExecutor) {
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V1_8, accessFlags,
				classNameInt, null, superClassNameInt, aInterfaces);
		this.classNameInt = classNameInt;
		this.superClassNameInt = superClassNameInt;
		this.methodExecutor = methodExecutor;
		
		if (methodExecutor != null) {
			// Generate a jsmud-internal field to demonstrate that we want to simulate the execution.
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, FIELD_EXECUTOR,
					Type.getDescriptor(MethodExecutor.class), null, null);
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, FIELD_EXECUTABLES,
					Type.getDescriptor(Object[].class), null, null);
		}
	}

	/**
	 * Adds a field.
	 * @param fieldDef field-definition
	 * @param value initial field-value or <code>null</code>
	 */
	public void addField(final ClassFieldDefinition fieldDef, final Object value) {
		cw.visitField(fieldDef.accessFlags, fieldDef.name, fieldDef.descriptor, fieldDef.signature, value);
	}

	/**
	 * Adds a method-definition (with minimal body).
	 * @param methodDef method-definition
	 * @param methodIdx index of the method in this class
	 */
	public void addMethod(final ClassMethodDefinition methodDef, final int methodIdx) {
		final int accessFlags = methodDef.accessFlags;
		MethodVisitor vm = cw.visitMethod(accessFlags, methodDef.name,
				methodDef.descriptor, methodDef.signature, methodDef.exceptions);
		if (!Modifier.isAbstract(accessFlags) && !Modifier.isNative(accessFlags)) {
			if (methodExecutor != null && !Modifier.isStatic(accessFlags) && !"<init>".equals(methodDef.name)) {
				createExecutorMethod(methodDef, vm, classNameInt, superClassNameInt, methodIdx);
			}
			else {
				createEmptyMethod(methodDef, vm, classNameInt, superClassNameInt);
			}
		}
	}

	/**
	 * Renders the bytecode.
	 * @return bytecode
	 */
	public byte[] toByteArray() {
		return cw.toByteArray();
	}

	/**
	 * Creates a method body which returns <code>null</code> or 0 (depending on return-type).
	 * @param methodDef method-definition
	 * @param mv method-visitor
	 * @param classNameInt class-name of the class to be generated
	 * @param classNameSuper class-name of the super-class
	 */
	private static void createEmptyMethod(ClassMethodDefinition methodDef, MethodVisitor mv,
			final String classNameInt, final String classNameSuper) {
		final int numLocals = computeNumberLocalVariables(methodDef);
		final int numStack = 2;
		mv.visitMaxs(numStack, numLocals);
		//vm.visitFrame(Opcodes.F_FULL, numLocals, new Object[numLocals], numStack, new Object[numStack]);
		//vm.visitCode();
		
		if ("<init>".equals(methodDef.name)) {
			if (classNameSuper != null) {
				mv.visitIntInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, classNameSuper, "<init>", "()V", false);
			}
			mv.visitInsn(Opcodes.RETURN);
		}
		else {
			final String returnType = methodDef.returnType;
			if (returnType.length() == 1) {
				if ("V".equals(returnType)) {
					mv.visitInsn(Opcodes.RETURN);
				}
				else if ("Z".equals(returnType) || "I".equals(returnType)
						|| "B".equals(returnType)
						|| "C".equals(returnType) || "S".equals(returnType)) {
					mv.visitInsn(Opcodes.ICONST_0);
					mv.visitInsn(Opcodes.IRETURN);
				}
				else if ("J".equals(returnType)) {
					mv.visitInsn(Opcodes.LCONST_0);
					mv.visitInsn(Opcodes.LRETURN);
				}
				else if ("F".equals(returnType)) {
					mv.visitInsn(Opcodes.FCONST_0);
					mv.visitInsn(Opcodes.FRETURN);
				}
				else if ("D".equals(returnType)) {
					mv.visitInsn(Opcodes.DCONST_0);
					mv.visitInsn(Opcodes.DRETURN);
				}
				else {
					throw new JvmException("Unexpected return-type " + returnType);
				}
			}
			else if ("toString".equals(methodDef.name)
					&& !Modifier.isStatic(methodDef.accessFlags)
					&& (methodDef.paramTypes.length == 0)
					&& "Ljava/lang/String;".equals(methodDef.returnType)
					&& !"java/lang/Object".equals(classNameInt)) {
				mv.visitVarInsn(Opcodes.ALOAD, 0); 
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
				mv.visitInsn(Opcodes.ARETURN);
			}
			else {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * Creates a method body which returns <code>null</code> or 0 (depending on return-type).
	 * @param methodDef method-definition
	 * @param mv method-visitor
	 * @param classNameInt class-name of the class to be generated
	 * @param classNameSuper class-name of the super-class
	 * @param methodIdx index of the method in the executable-array
	 */
	private static void createExecutorMethod(ClassMethodDefinition methodDef, MethodVisitor mv,
			final String classNameInt, final String classNameSuper, final int methodIdx) {
		if ("<init>".equals(methodDef.name)) {
			throw new JvmException(String.format("A constructor-execution (%s) with a executor-method is not yet supported.", classNameInt));
		}
		final int numParams = methodDef.paramTypes.length;
		final int numLocalVariables = computeNumberLocalVariables(methodDef);
		final int localSizeMethodExecutor = 1;
		final int numLocals = numLocalVariables + localSizeMethodExecutor;
		final int numStack = 2;
		mv.visitMaxs(numStack, numLocals);

		final int idxArray = numLocals;
		mv.visitLdcInsn(Integer.valueOf(numParams));
		mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(Object[].class));
		mv.visitVarInsn(Opcodes.ASTORE, idxArray);

		loadInstanceAndArguments(mv, methodDef, classNameInt, idxArray);

		mv.visitFieldInsn(Opcodes.GETSTATIC, classNameInt, FIELD_EXECUTOR, Type.getDescriptor(MethodExecutor.class));
		mv.visitFieldInsn(Opcodes.GETSTATIC, classNameInt, FIELD_EXECUTABLES, Type.getDescriptor(Object[].class));
		if (methodIdx < 128) {
			mv.visitIntInsn(Opcodes.BIPUSH, methodIdx);
		} else {
			mv.visitIntInsn(Opcodes.SIPUSH, methodIdx);
		}
		mv.visitInsn(Opcodes.AALOAD);
		mv.visitVarInsn(Opcodes.ALOAD, 0); // this
		mv.visitVarInsn(Opcodes.ALOAD, idxArray); // array
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(MethodExecutor.class), "execute", "(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", true);

		final String returnType = methodDef.returnType;
		convertObjectIntoReturnType(mv, returnType);

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * Compute the number of local variables given by the method-invocation:
	 * An optional this-instance and the local variables to hand over the method parameters.
	 * @param methodDef method definition
	 * @return number of local variables
	 */
	private static int computeNumberLocalVariables(ClassMethodDefinition methodDef) {
		int numLocalVariables = 0;
		if ((methodDef.accessFlags & Modifier.STATIC) == 0) {
			numLocalVariables++;
		}
		final String[] aParamTypes = methodDef.paramTypes;
		for (int i = 0; i < aParamTypes.length; i++) {
			final String paramType = aParamTypes[i];
			if ("J".equals(paramType) || "D".equals(paramType)) {
				numLocalVariables += 2;
			}
			else {
				numLocalVariables++;
			}
		}
		return numLocalVariables;
	}

	private static void loadInstanceAndArguments(MethodVisitor mv, ClassMethodDefinition methodDef,
			String classNameInt2, int idxArray) {
		final String[] aParamTypes = methodDef.paramTypes;
		if (aParamTypes.length > 127) {
			throw new JvmException(String.format("Too many parameters (%d) in method %s",
					Integer.valueOf(aParamTypes.length), methodDef.name));
		}
		int indexVar = 1;
		for (int i = 0; i < aParamTypes.length; i++) {
			mv.visitVarInsn(Opcodes.ALOAD, idxArray);
			if (i == 0) {
				mv.visitInsn(Opcodes.ICONST_0);
			} else if (i == 1) {
				mv.visitInsn(Opcodes.ICONST_1);
			} else if (i == 2) {
				mv.visitInsn(Opcodes.ICONST_2);
			} else if (i == 3) {
				mv.visitInsn(Opcodes.ICONST_3);
			} else if (i == 4) {
				mv.visitInsn(Opcodes.ICONST_4);
			} else {
				mv.visitIntInsn(Opcodes.BIPUSH, i);
			}
			final String paramType = aParamTypes[i];
			if (paramType.startsWith("L")) {
				mv.visitVarInsn(Opcodes.ALOAD, indexVar);
			}
			else if ("I".equals(paramType)) {
				mv.visitVarInsn(Opcodes.ILOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			}
			else if ("Z".equals(paramType)) {
				mv.visitVarInsn(Opcodes.ILOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
			}
			else if ("S".equals(paramType)) {
				mv.visitVarInsn(Opcodes.ILOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
			}
			else if ("C".equals(paramType)) {
				mv.visitVarInsn(Opcodes.ILOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
			}
			else if ("J".equals(paramType)) {
				mv.visitVarInsn(Opcodes.LLOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
				indexVar++;
			}
			else if ("F".equals(paramType)) {
				mv.visitVarInsn(Opcodes.FLOAD, indexVar);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
			}
			else if ("D".equals(paramType)) {
				mv.visitVarInsn(Opcodes.DLOAD, indexVar);
				indexVar++;
			}
			else {
				throw new JvmException(String.format("Unknown type (%s) at index (%d) in parameters of method %s",
						paramType, Integer.valueOf(i), methodDef.name));
			}
			mv.visitInsn(Opcodes.AASTORE);
			indexVar++;
		}
	}

	/**
	 * Convert a returned object into a return-type.
	 * 
	 * @param mv method-visitor
	 * @param returnType return-type
	 */
	private static void convertObjectIntoReturnType(MethodVisitor mv, final String returnType) {
		if (returnType.length() == 1) {
			if ("V".equals(returnType)) {
				mv.visitInsn(Opcodes.RETURN);
			}
			else if ("Z".equals(returnType)) {
				//	50: checkcast     #68                 // class java/lang/Boolean
				//	57: invokevirtual #77                 // Method java/lang/Boolean.booleanValue:()Z
				//	60: ireturn
				final String intName = Type.getInternalName(Boolean.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "booleanValue", "()Z", false);
				mv.visitInsn(Opcodes.IRETURN);
			}
			else if ("I".equals(returnType)) {
				final String intName = Type.getInternalName(Integer.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "intValue", "()I", false);
				mv.visitInsn(Opcodes.IRETURN);
			}
			else if ("B".equals(returnType)) {
				final String intName = Type.getInternalName(Byte.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "byteValue", "()B", false);
				mv.visitInsn(Opcodes.IRETURN);
			}
			else if ("C".equals(returnType)) {
				final String intName = Type.getInternalName(Character.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "charValue", "()C", false);
				mv.visitInsn(Opcodes.IRETURN);
			}
			else if ("S".equals(returnType)) {
				final String intName = Type.getInternalName(Short.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "shortValue", "()S", false);
				mv.visitInsn(Opcodes.IRETURN);
			}
			else if ("J".equals(returnType)) {
				final String intName = Type.getInternalName(Long.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "longValue", "()J", false);
				mv.visitInsn(Opcodes.LRETURN);
			}
			else if ("F".equals(returnType)) {
				final String intName = Type.getInternalName(Float.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "floatValue", "()F", false);
				mv.visitInsn(Opcodes.FRETURN);
			}
			else if ("D".equals(returnType)) {
				final String intName = Type.getInternalName(Double.class);
				mv.visitTypeInsn(Opcodes.CHECKCAST, intName);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, intName, "doubleValue", "()D", false);
				mv.visitInsn(Opcodes.DRETURN);
			}
			else {
				throw new JvmException("Unexpected return-type " + returnType);
			}
		}
		else {
			mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(returnType).getInternalName());
			mv.visitInsn(Opcodes.ARETURN);
		}
	}

}
