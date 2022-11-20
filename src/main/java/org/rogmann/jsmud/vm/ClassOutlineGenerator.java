package org.rogmann.jsmud.vm;

import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Builds a .class-file based on a class-definition including fields and methods.
 * The methods will get an empty respective minimal body.
 * 
 * <p>The interface of this class doesn't have asm-dependencies.</p>
 */
public class ClassOutlineGenerator {

	/** class-writer */
	private final ClassWriter cw;

	/** internal name of class */
	private final String classNameInt;
	/** internal name of super-class */
	private final String superClassNameInt;

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
	 */
	public ClassOutlineGenerator(final int accessFlags, final String classNameInt, final String superClassNameInt,
			final String[] aInterfaces) {
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V1_8, accessFlags,
				classNameInt, null, superClassNameInt, aInterfaces);
		this.classNameInt = classNameInt;
		this.superClassNameInt = superClassNameInt;
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
	 */
	public void addMethod(final ClassMethodDefinition methodDef) {
		MethodVisitor vm = cw.visitMethod(methodDef.accessFlags, methodDef.name,
				methodDef.descriptor, methodDef.signature, methodDef.exceptions);
		if (!Modifier.isAbstract(methodDef.accessFlags) && !Modifier.isNative(methodDef.accessFlags)) {
			createEmptyMethod(methodDef, vm, classNameInt, superClassNameInt);
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
	 * @param vm method-visitor
	 * @param classNameInt class-name of the class to be generated
	 * @param classNameSuper class-name of the super-class
	 */
	private static void createEmptyMethod(ClassMethodDefinition methodDef, MethodVisitor vm,
			final String classNameInt, final String classNameSuper) {
		final int localSizeInstance = ((methodDef.accessFlags & Modifier.STATIC) == 0) ? 1 : 0;
		final int numLocals = localSizeInstance + methodDef.paramTypes.length;
		final int numStack = 2;
		vm.visitMaxs(numStack, numLocals);
		//vm.visitFrame(Opcodes.F_FULL, numLocals, new Object[numLocals], numStack, new Object[numStack]);
		//vm.visitCode();
		
		if ("<init>".equals(methodDef.name)) {
			if (classNameSuper != null) {
				vm.visitIntInsn(Opcodes.ALOAD, 0);
				vm.visitMethodInsn(Opcodes.INVOKESPECIAL, classNameSuper, "<init>", "()V", false);
			}
			vm.visitInsn(Opcodes.RETURN);
		}
		else {
			final String returnType = methodDef.returnType;
			if (returnType.length() == 1) {
				if ("V".equals(returnType)) {
					vm.visitInsn(Opcodes.RETURN);
				}
				else if ("Z".equals(returnType) || "I".equals(returnType)
						|| "B".equals(returnType)
						|| "C".equals(returnType) || "S".equals(returnType)) {
					vm.visitInsn(Opcodes.ICONST_0);
					vm.visitInsn(Opcodes.IRETURN);
				}
				else if ("J".equals(returnType)) {
					vm.visitInsn(Opcodes.LCONST_0);
					vm.visitInsn(Opcodes.LRETURN);
				}
				else if ("F".equals(returnType)) {
					vm.visitInsn(Opcodes.FCONST_0);
					vm.visitInsn(Opcodes.FRETURN);
				}
				else if ("D".equals(returnType)) {
					vm.visitInsn(Opcodes.DCONST_0);
					vm.visitInsn(Opcodes.DRETURN);
				}
				else {
					throw new JvmException("Unexpected return-type " + returnType);
				}
			}
			else if ("toString".equals(methodDef.name) && "Ljava/lang/String;".equals(methodDef.returnType)
					&& !"java/lang/Object".equals(classNameInt)) {
				vm.visitVarInsn(Opcodes.ALOAD, 0); 
				vm.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
				vm.visitInsn(Opcodes.ARETURN);
			}
			else {
				vm.visitInsn(Opcodes.ACONST_NULL);
				vm.visitInsn(Opcodes.ARETURN);
			}
		}
		vm.visitMaxs(0, 0);
		vm.visitEnd();
	}

}
