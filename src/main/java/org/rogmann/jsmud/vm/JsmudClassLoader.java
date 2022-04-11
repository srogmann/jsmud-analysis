package org.rogmann.jsmud.vm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Class-loader which may patch bytecode of classes to support static initializers or constructors.
 */
public class JsmudClassLoader extends ClassLoader {
	/** logger */
	protected static final Logger LOG = LoggerFactory.getLogger(JsmudClassLoader.class);

	/** flag: bytecode has been generated by this class-loader */
	private static final int FLAG_PATCHED_CLASS = 2;
	/** flag: static initializer has been patched in this class-loader */
	private static final int FLAG_PATCHED_CLINIT = 4;
	/** flag: default initializer has been added or patched in this class-loader */
	private static final int FLAG_PATCHED_INIT = 8;
	/** flag: default initializer has been added in this class-loader */
	private static final int FLAG_ADDED_INIT = 16;

	/** prefix of jsmud-classes (package-name of this class) */
	private static final String PREFIX_JSMUD = JsmudClassLoader.class.getName().replaceFirst("[.][^.]*$", ".");

	/** Suffix ".class" */
	private static final String SUFFIX_CLASS = ".class";

	/** configuration */
	protected final JsmudConfiguration configuration;

	/** parent-class-loader (default class-loader) */
	private final ClassLoader parentClassLoader;

	/** map from class-name to original class-loader of patched class */
	protected final ConcurrentMap<String, ClassLoader> mapPatchedClassesClassLoader = new ConcurrentHashMap<>(100);

	/** map from class-name to patched class */
	protected final ConcurrentMap<String, Class<?>> mapPatchedClasses = new ConcurrentHashMap<>(100);
	/** map from class-name to remapped class (e.g. java.lang.Record to jdksim.java.lang.Record.clss) */
	protected final ConcurrentMap<String, Class<?>> mapRemappedClasses = new ConcurrentHashMap<>(100);

	/** map from class to bytecode of a class defined in {@link JsmudClassLoader#defineJsmudClass(String, byte[])} */
	protected final ConcurrentMap<Class<?>, byte[]> mapJsmudClassBytecode = new ConcurrentHashMap<>();
	/** map from class-loader to class-name to class of a class defined via jsmud */
	protected final ConcurrentMap<ClassLoader, ConcurrentMap<String, Class<?>>> mapJsmudClasses = new ConcurrentHashMap<>();

	/** map from class-name to flags */
	protected final ConcurrentMap<String, Integer> mapClassFlags = new ConcurrentHashMap<>(100);

	/** class-names of classes to be patched */
	final Predicate<String> patchFilter;

	/** <code>true</code> if static-initializers ("<&lt;clinit&gt;") may be patched */
	final boolean patchClinit;
	
	/** <code>true</code> if default-constructors ("&lt;init&gt;") may be patched */
	final boolean patchInit;

	/** <code>true</code> if hot-code-replace-requests should be accepted */
	final boolean redefineClasses;

	/**
	 * Constructor.
	 * <p>Default is patchFilter = (name -&gt; false), patchClinit = patchInit = redefineClasses = false.</p>
	 * <p>Use patchFilter and patchClinit = redefineClasses = true, patchInit = false, to
	 * analyze or debug static initializers.</p>
	 * <p>If the reflection-helper can't build a class you may try patchInit to
	 * analyze or debug a constructors.</p> 
	 * 
	 * @param parentClassLoader parent-classloader
	 * @param configuration jsmud-configuration
	 * @param patchFilter checks if a class-name belongs to a class to be patched
	 * @param patchClinit <code>true</code> if static-initializers ("&lt;linit&gt;") may be patched 
	 * @param patchInit <code>true</code> if default-constructors ("&lt;init&gt;") may be patched
	 * @param redefineClasses <code>true</code> if hot-code-replace-requests should be accepted
	 */
	public JsmudClassLoader(final ClassLoader parentClassLoader,
			final JsmudConfiguration configuration,
			final Predicate<String> patchFilter,
			final boolean patchClinit, final boolean patchInit, final boolean redefineClasses) {
		this.parentClassLoader = parentClassLoader;
		this.configuration = configuration;
		this.patchFilter = patchFilter;
		this.patchClinit = patchClinit;
		this.patchInit = patchInit;
		this.redefineClasses = redefineClasses;
	}

	/** {@inheritDoc} */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		//if (LOG.isDebugEnabled()) {
		//	LOG.debug("loadClass: looking for " + name);
		//}
		boolean isJreXmlClass = name.startsWith("org.xml.") || name.startsWith("org.w3c.");
		boolean isJsmud = name.startsWith(PREFIX_JSMUD);
		final Class<?> clazz;
		if (CallSiteContext.class.getName().equals(name)) {
			// CallSiteContext is used in generated classes supporting INVOKEDYNAMIC.
			clazz = CallSiteContext.class;
		}
		else if (patchFilter.test(name) && !isJreXmlClass && !isJsmud) {
			clazz = findClass(name);
		}
		else {
			clazz = parentClassLoader.loadClass(name);
		}
		//if (LOG.isDebugEnabled()) {
		//	LOG.debug(String.format("loadClass: return %s of %s", clazz, clazz.getClassLoader()));
		//}
		return clazz;
	}

	/**
	 * Defines a new class.
	 * @param name name of the new class
	 * @param bufBytecode bytecode
	 * @return defined class
	 */
	public Class<?> defineJsmudClass(final String name, final byte[] bufBytecode) {
		final Class<?> clazz = defineClass(name, bufBytecode, 0, bufBytecode.length);
		registerJsmudClass(clazz, name, bufBytecode);
		return clazz;
	}

	/**
	 * Defines and registers a class patched by this classloader.
	 * @param name class-name
	 * @param classLoader jsmud-classloader-instance
	 * @param bytecodePatched patched bytecode
	 * @return class defined
	 * @throws ClassFormatError in case of a class-format-error
	 */
	public Class<?> definePatchedClass(final String name, final ClassLoader classLoader, byte[] bytecodePatched)
			throws ClassFormatError {
		Class<?> clazz;
		final String folderJsmudBytecode = configuration.folderDumpJsmudPatchedBytecode;
		if (folderJsmudBytecode != null) {
			final String nameClass = name.replace('.', '/') + ".class";
			try {
				Files.write(new File(folderJsmudBytecode,
						nameClass.replace('/', '.')).toPath(), bytecodePatched);
			} catch (IOException e) {
				System.err.println("Fehler beim Schreiben: " + e);
			}
		}
		clazz = defineClass(name, bytecodePatched, 0, bytecodePatched.length);
		if (LOG.isDebugEnabled()) {
			LOG.debug("mapPatchedClass: put " + name);
		}
		mapPatchedClasses.put(name, clazz);
		mapPatchedClassesClassLoader.put(name, classLoader);
		registerJsmudClass(clazz, name, bytecodePatched);
		setClassFlag(name, FLAG_PATCHED_CLASS);
		return clazz;
	}

	/**
	 * Registers a in JSMUD generated class.
	 * @param clazz class
	 * @param name fully qualified name of the class
	 * @param bufBytecode bytecode
	 */
	public void registerJsmudClass(final Class<?> clazz, final String name, final byte[] bufBytecode) {
		final ClassLoader cl = Utils.getClassLoader(clazz, this);
		mapJsmudClassBytecode.put(clazz, bufBytecode);
		mapJsmudClasses.computeIfAbsent(cl, clKey -> new ConcurrentHashMap<>())
			.put(name, clazz);
	}

	/**
	 * Defines a new class.
	 * @param name name of the new class
	 * @param bufBytecode bytecode
	 * @param classBefore class before (we need its class-loader)
	 * @return redefined class
	 */
	public Class<?> redefineJsmudClass(final String name, final byte[] bufBytecode, final Class<?> classBefore) {
		// We use a new class-loader to redefine a class.
		final JsmudClassLoader newClassLoader = new JsmudClassLoader(classBefore.getClassLoader(),
				configuration,
				patchFilter, patchClinit, patchInit, redefineClasses);
		return newClassLoader.defineJsmudClass(name, bufBytecode);
	}

	/** {@inheritDoc} */
	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return findClass(name, parentClassLoader, null);
	}

	/**
	 * Loads a class. The bytecode will be patched if the patch-filter matches.
	 * @param name name of the class to be loaded (and patched)
	 * @param classLoader classLoader to be used for loading the original-bytecode
	 * @param vm optional VM (may know classes defined at runtime)
	 * @return class being looked for
	 * @throws ClassNotFoundException if the class couldn't be found
	 */
	public Class<?> findClass(final String name, final ClassLoader classLoader, VM vm) throws ClassNotFoundException {
		Class<?> clazz = mapPatchedClasses.get(name);
		if (clazz == null) {
			final ConcurrentMap<String, Class<?>> mapNameClass = mapJsmudClasses.get(classLoader);
			if (mapNameClass != null) {
				clazz = mapNameClass.get(name);
			}
		}
		if (clazz == null) {
			clazz = mapRemappedClasses.get(name);
		}
		if (clazz == null) {
			final boolean classMayBePatched = patchFilter.test(name) && (patchClinit || patchInit);
			boolean classIsAlreadyDefined = false;
			if (classLoader != null) {
				classIsAlreadyDefined = (vm != null) && (vm.getBytecodeOfDefinedClass(classLoader, name) != null);
			}
			byte[] bytecodePatched = null;
			String namePatched = name;
			if (classMayBePatched && !classIsAlreadyDefined && classLoader != null) {
				final String nameClass = name.replace('.', '/') + ".class";
				try (InputStream isBytecode = classLoader.getResourceAsStream(nameClass)) {
					if (isBytecode == null) {
						throw new ClassNotFoundException(String.format("Bytecode of (%s) is not found in class-loader (%s)",
								nameClass, classLoader));
					}
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("findClass: patching %s of %s", nameClass, classLoader));
					}
					ClassWithName patchedClass = patchClass(name, isBytecode);
					bytecodePatched = patchedClass.getBytecode();
					namePatched = patchedClass.getName();
				}
				catch (IOException e) {
					throw new ClassNotFoundException(String.format("IO-error while reading class (%s) via class-loader (%s)",
							name, classLoader), e);
				}
			}
			else {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("findClass: looking for %s in %s", name, classLoader));
				}
			}
			if (bytecodePatched != null) {
				clazz = definePatchedClass(namePatched, classLoader, bytecodePatched);
				if (!namePatched.equals(name)) {
					mapRemappedClasses.put(name, clazz);
				}
			}
			else if (classIsAlreadyDefined && classLoader instanceof JsmudClassLoader) {
				final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) classLoader;
				LOG.info("Check " + jsmudClassLoader + " for " + name);
				clazz = jsmudClassLoader.loadDefinedClass(name);
			}
			else if (classLoader != null) {
				clazz = classLoader.loadClass(name);
			}
			else {
				clazz = parentClassLoader.loadClass(name);
			}
		}
		return clazz;
	}

	/**
	 * Loads a class defined defined at runtime without patching.
	 * @param name class-name
	 * @return clazz
	 * @throws ClassNotFoundException in case of an unknown clas
	 */
	private Class<?> loadDefinedClass(final String name) throws ClassNotFoundException {
		return super.loadClass(name);
	}

	/** {@inheritDoc} */
	@Override
    public InputStream getResourceAsStream(final String resName) {
		if (resName.endsWith(SUFFIX_CLASS)) {
			// Check if resName is a class defined in this class-loader.
			final String className = resName.substring(0, resName.length() - SUFFIX_CLASS.length()).replace('/', '.');
			final ConcurrentMap<String, Class<?>> mapNameClass = mapJsmudClasses.get(this);
			if (mapNameClass != null) {
				final Class<?> clazz = mapNameClass.get(className);
				if (clazz != null) {
					final byte[] bufClass = mapJsmudClassBytecode.get(clazz);
					if (bufClass != null) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("getResourceAsStream: return bytecode (len %d) of defined class (%s)",
									Integer.valueOf(bufClass.length), className));
						}
						return new ByteArrayInputStream(bufClass);
					}
				}
			}
		}
		return super.getResourceAsStream(resName);
    }

	/**
	 * Sets a class-flag in the current-class.
	 * @param className class-name, e.g. "java/lang/Object"
	 * @param flag flag
	 */
	void setClassFlag(final String className, final int flag) {
		mapClassFlags.compute(className, (cn, oldFlag) ->
			(oldFlag != null) ? Integer.valueOf(oldFlag.intValue() | flag) : Integer.valueOf(flag));
	}

	/**
	 * Gets the class-loader used to read the original bytecode of a patched class.
	 * @param className class-name
	 * @return class-loader or <code>null</code>
	 */
	public ClassLoader getPatchedClassClassLoader(String className) {
		return mapPatchedClassesClassLoader.get(className);
	}

	/**
	 * Checks if the class' default-constructor is JSMUD-patched or added.
	 * @param clazz class
	 * @return patched-flag
	 */
	public boolean isDefaultConstructorPatched(final Class<?> clazz) {
		final Integer flags = mapClassFlags.get(clazz.getName());
		return flags != null && (flags.intValue() & FLAG_PATCHED_INIT) != 0;
	}

	/**
	 * Checks if the class' default-constructor is added.
	 * Added means that there is not default-constructor in the original class!
	 * @param clazz class
	 * @return added-flag
	 */
	public boolean isDefaultConstructorAdded(final Class<?> clazz) {
		final Integer flags = mapClassFlags.get(clazz.getName());
		return flags != null && (flags.intValue() & FLAG_ADDED_INIT) != 0;
	}

	/**
	 * Checks if the class' static-initializer is JSMUD-patched.
	 * @param clazz class
	 * @return patched-flag
	 */
	public boolean isStaticInitializerPatched(Class<?> clazz) {
		final Integer flags = mapClassFlags.get(clazz.getName());
		return flags != null && (flags.intValue() & FLAG_PATCHED_CLINIT) != 0;
	}

	/**
	 * <code>true</code> if hot-code-replace should be replaced.
	 * @return redefine-classes-flag
	 */
	public boolean isRedefineClasses() {
		return redefineClasses;
	}

	/**
	 * Patches static initializer and optional constructors of a class.
	 * @param name class-name
	 * @param isBytecode input-stream of bytecode
	 * @return patched bytecode, <code>null</code> in case of an interface
	 * @throws IOException in case of an IO-error while reading the class 
	 */
	public ClassWithName patchClass(String name, InputStream isBytecode) throws IOException {
		final ClassReader classReader = new ClassReader(isBytecode);
		if ((configuration.isDontPatchPublicInterfaces
			&& (classReader.getAccess() & (Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC))
				== (Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC))) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("patchClass: Don't duplicate public interface (%s) with 0x%x into %s",
						name, Integer.valueOf(classReader.getAccess()), this));
			}
			return null;
		}
		final int flags = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
		final ClassWriter classWriter = new ClassWriter(classReader, flags);
		final Optional<ClassRemapper> remapper = configuration.getClassRemapper();
		String patchedClassName = remapper.map(r -> r.remapName(name)).orElse(name);
		final ClassVisitor classVisitor = new InitializerAdapter(classWriter, name);
		final int parsingOptions = 0;
		classReader.accept(classVisitor, parsingOptions);
		ClassWriter classWriter2 = classWriter;
		if (remapper.isPresent()) {
			ClassRemapper oRemapper = remapper.get();
			classWriter2 = oRemapper.remapClassWriter(classWriter, name);
		}
		return new ClassWithName(patchedClassName, classWriter2.toByteArray());
	}

	class InitializerAdapter extends ClassVisitor {

		/** name of the renamed static initializer */
		static final String METHOD_JSMUD_CLINIT = "__JSMUD_clinit";
		/** name of the default-constructor-flag */
		static final String FIELD_CONSTR_FLAG = "__JSMUD_constr_flag";

		/** name of the patched class */
		private final String patchedClassName;

		private boolean patchedDefaultInit = false;
		
		/** name of this class, e.g. "org/apache/commons/logging/impl/LogFactoryImpl" */
		private String tClassName;

		/** access-bits of this class */
		private int classAccess;
		
		/** <code>true</code> if the class is an interface */
		private boolean isInterface;
		
		/** <code>true</code> if removable final-modifiers should be removed */
		private final boolean isRemoveFinalModifiers;

		/** type of the parent-class */
		Type typeSuper;

		/** <code>true</code>, if the static-initializer mustn't be changed */
		private boolean patchClinitForbidden = false;
		/** <code>true</code>, if the default-constructur mustn't be created or changed */
		private boolean patchInitForbidden = false;

		/**
		 * Constructor
		 * @param classWriter class-writer
		 * @param patchedClassName name of the patched class
		 */
		public InitializerAdapter(final ClassWriter classWriter, String patchedClassName) {
			super(Opcodes.ASM9, classWriter);
			isRemoveFinalModifiers = true;
			this.patchedClassName = patchedClassName;
		}

		/** {@inheritDoc} */
		@Override
		public void visit(final int version, final int access, final String pName, final String signature,
				final String superName, final String[] interfaces) {
			final String name = patchedClassName.replace('.', '/');;
			tClassName = name;
			classAccess = access;
			isInterface = ((access & Opcodes.ACC_INTERFACE) != 0);
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("init: name=%s, version=%d, superName=%s, access=%d",
						name, Integer.valueOf(version), superName, Integer.valueOf(access)));
			}
			String sVersion = configuration.patchedClassesVersion;
			final int versionDest = (sVersion == null) ? version : Integer.parseInt(sVersion);
			super.visit(versionDest, access, name, signature, superName, interfaces);
			typeSuper = Type.getObjectType(superName);
			if (!"java/lang/Object".equals(superName)) {
				patchSuperClass(typeSuper, "needed for " + name);

				// Check the super-class.
				String superClassName = typeSuper.getClassName();
				Class<?> classSuper;
				try {
					classSuper = findClass(superClassName);
				} catch (ClassNotFoundException e) {
					throw new JvmException(String.format("Can't load super-type (%s) of (%s)", superClassName, name), e);
				}
				final Integer flagsSuper = mapClassFlags.get(superClassName);
				if (classSuper.isInterface()) {
					patchInitForbidden = true;
					patchClinitForbidden = true;
				}
				else if ("java.lang.Enum".equals(superClassName)) {
					patchClinitForbidden = true;
				}
				else if (superClassName.startsWith("java.") || superClassName.startsWith("sun.")) {
					// The super-class is in the JRE (and not java.lang.Object).
					// We don't want to patch it.
					patchInitForbidden = true;
				}
				else if (flagsSuper != null && (flagsSuper.intValue() & FLAG_PATCHED_INIT) == 0) {
					// The constructor of the super-class hasn't been patched.
					// So we don't patch this child-class.
					patchInitForbidden = true;
				}
			}
			
			//cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, CallSiteGenerator.FIELD_IS_EXECUTED_BY_JSMUD,
			//		Type.BOOLEAN_TYPE.getDescriptor(), null, null);
		}

		/** {@inheritDoc} */
		@Override
		public FieldVisitor visitField(final int access, final String name,
			      final String descriptor, final String signature, final Object value) {
			int pAccess = access;
			if (isRemoveFinalModifiers && value == null && !isInterface) {
				// No initial constant value, remove final-modifier.
				pAccess = pAccess & ~Modifier.FINAL;
			}
			return super.visitField(pAccess, name, descriptor, signature, value);
		}

		/**
		 * Patches a super-class.
		 * @param superType type of super-class
		 * @param reason reason why we want to patch this class (used in error-messages)
		 */
		private void patchSuperClass(Type superType, String reason) {
			final String superClassName = superType.getClassName();
			try {
				loadClass(superClassName);
			} catch (ClassNotFoundException e) {
				throw new JvmException(String.format("Can't load class (%s) %s",
						superClassName, reason), e);
			}
		}

		/** {@inheritDoc} */
		@Override
		public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
				final String signature, final String[] exceptions) {
			if ("<clinit>".equals(name) && "()V".equals(descriptor) && !patchClinitForbidden
					&& patchClinit && ((classAccess & Opcodes.ACC_INTERFACE) == 0)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("visitMethod <clinit>: name=%s, class=%s, classAccess=%d", name, tClassName, Integer.valueOf(classAccess)));
				}
				setClassFlag(Type.getObjectType(tClassName).getClassName(), FLAG_PATCHED_CLINIT);
				return super.visitMethod(access, METHOD_JSMUD_CLINIT, descriptor, signature, exceptions);
			}
			else if ("<init>".equals(name) && "()V".equals(descriptor)
					&& !patchInitForbidden
					&& patchInit && ((classAccess & Opcodes.ACC_INTERFACE) == 0)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("visitMethod <init>: name=%s, class=%s, classAccess=%d", name, tClassName, Integer.valueOf(classAccess)));
				}
				patchedDefaultInit = true;
				final MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
				return new DefaultConstructorAdapter(access, name, descriptor, signature, exceptions, mv, tClassName);
			}
			//else if ("toString".equals(name) && "()Ljava/lang/String;".equals(descriptor) {
			//	final MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			//	final Label labelJsmudExec = new Label();
			//	final Label labelReturn = new Label();
			//	mv.visitFieldInsn(Opcodes.GETSTATIC, tClassName, CallSiteGenerator.FIELD_IS_EXECUTED_BY_JSMUD,
			//			Type.BOOLEAN_TYPE.getDescriptor());
			//	mv.visitJumpInsn(Opcodes.IFEQ, labelJsmudExec);
			//	return mv;
			//}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		/** {@inheritDoc} */
		@Override
		public void visitEnd() {
			if (patchInit && (classAccess & Opcodes.ACC_INTERFACE) == 0 && !patchInitForbidden) {
				if (patchedDefaultInit) {
					// We need a boolean-flag which disables the default-constructor in the instantiation.
					final FieldVisitor fv = cv.visitField(Opcodes.ACC_PRIVATE, FIELD_CONSTR_FLAG, "Z", null, null);
					if (fv != null) {
						fv.visitEnd();
					}
				}
				else {
					final MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
					mv.visitCode();
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, typeSuper.getInternalName(), "<init>", "()V", false);
					mv.visitInsn(Opcodes.RETURN);
					mv.visitMaxs(1, 1);
					mv.visitEnd();
					setClassFlag(Type.getObjectType(tClassName).getClassName(), FLAG_ADDED_INIT);
				}
				setClassFlag(Type.getObjectType(tClassName).getClassName(), FLAG_PATCHED_INIT);
			}

			super.visitEnd();
		}

		class DefaultConstructorAdapter extends MethodNode {

			/** owner-class of JSMUD-field */
			protected final String jsmudFieldOwner;

			/**
			 * Constructor.
			 *
			 * @param access the method's access flags (see {@link Opcodes})
			 * @param name the method's name
			 * @param descriptor the method's descriptor (see {@link Type})
			 * @param signature the method's signature
			 * @param exceptions the internal names of the method's exception classes
			 * @param jsmudFieldOwner JSMUD-field owner class
			 */
			public DefaultConstructorAdapter(final int access, final String name, final String descriptor,
					final String signature, final String[] exceptions, final MethodVisitor mv, final String jsmudFieldOwner) {
				super(Opcodes.ASM9, access, name, descriptor, signature, exceptions);
				this.mv = mv;
				this.jsmudFieldOwner = jsmudFieldOwner;
			}

			/** {@inheritDoc} */
			@Override
			public void visitEnd() {
				// TODO Don't patch empty default-constructor!
				final AbstractInsnNode firstNode = instructions.get(0);
				final InsnList insnList = new InsnList();
				final LabelNode labelElse = new LabelNode(new Label());
				insnList.add(new InsnNode(Opcodes.ICONST_0));
				insnList.add(new JumpInsnNode(Opcodes.IFNE, labelElse));
				insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
				insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, typeSuper.getInternalName(), "<init>", "()V", false));
				//insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
				//insnList.add(new FieldInsnNode(Opcodes.GETFIELD,
				//		jsmudFieldOwner, InitializerAdapter.FIELD_CONSTR_FLAG, "Z"));
				//final LabelNode labelElse = new LabelNode(new Label());
				//insnList.add(new JumpInsnNode(Opcodes.IFNE, labelElse));
				//insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
				//insnList.add(new InsnNode(Opcodes.ICONST_1));
				//insnList.add(new FieldInsnNode(Opcodes.PUTFIELD,
				//		jsmudFieldOwner, InitializerAdapter.FIELD_CONSTR_FLAG, "Z"));
				insnList.add(new InsnNode(Opcodes.RETURN));
				insnList.add(labelElse);
				//insnList.add(new FrameNode(Opcodes.F_, numLocal, local, numStack, stack));
				instructions.insert(firstNode, insnList);
				accept(mv);
			}
			
		}

	}
	
}
