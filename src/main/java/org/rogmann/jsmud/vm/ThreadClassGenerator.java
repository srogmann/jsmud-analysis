package org.rogmann.jsmud.vm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rogmann.jsmud.gen.JsmudGeneratedClasses;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Generator to generate sub-classes of thread-classes to inject a JSMUD-runnable-patch.
 */
public class ThreadClassGenerator {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ThreadClassGenerator.class);

	/** class-loader for generated classes */
	private final JsmudClassLoader classLoader;

	/** optional folder used to dump generated thread-classes */
	private static final String FOLDER_DUMP_THREAD_CLASSES = System.getProperty(ThreadClassGenerator.class.getName() + ".threadClassFolder");

	/** internal field */
	public static final String FIELD_THREAD_EXECUTOR = "__JSMUD_THREAD_EXECUTOR";

	/** default-package of generated call-site-classes */
	private final String threadClassPackage = System.getProperty(ThreadClassGenerator.class.getName() + ".threadClassPackage",
			JsmudGeneratedClasses.class.getName().replaceFirst("[.][^.]*$", ""));

	/** number of generated thread-classes */
	private final AtomicInteger numThreadClasses = new AtomicInteger();

	/** map from thread-class to thread-child-class containing JSMUD-patch */
	private final ConcurrentMap<Class<?>, Class<?>> mapThreadChildClasses = new ConcurrentHashMap<>();

	/** map from generated class to bytecode used */ 
	private final ConcurrentMap<Class<?>, byte[]> mapBytecodes = new ConcurrentHashMap<>();

	/**
	 * Constructor
	 * @param classLoader class-loader for generated classes
	 */
	public ThreadClassGenerator(final JsmudClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public Class<?> generateClass(final Class<?> classParent, final String constrDesc) {
		final Class<?> classChild = mapThreadChildClasses.get(classParent);
		if (classChild != null) {
			return classChild;
		}
		final int numClass = numThreadClasses.incrementAndGet();
		final String classNameSimple = String.format("ThreadClass%s_%s",
				Integer.valueOf(numClass), classParent.getSimpleName());
		final String className = threadClassPackage + ((threadClassPackage.length() > 0) ? "." : "")
				+ classNameSimple;
		final String classNameInt = className.replace('.', '/');
		final ClassWriter cw = new ClassWriterCallSite(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
				classLoader, classNameInt);
		final Class<?>[] aClassInterfaces = classParent.getInterfaces();
		final String[] aInterfaces = new String[aClassInterfaces.length];
		for (int i = 0; i < aClassInterfaces.length; i++) {
			aInterfaces[i] = aClassInterfaces[i].getName().replace('.', '/');
		}
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				classNameInt, null, Type.getInternalName(classParent), aInterfaces);

		{
			final MethodVisitor constr = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constrDesc, null, null);
			constr.visitVarInsn(Opcodes.ALOAD, 0);
			final Type[] argumentTypes = Type.getArgumentTypes(constrDesc);
			for (int i = 0; i < argumentTypes.length; i++) {
				final int indexInLocals = 1 + i;
				CallSiteGenerator.loadLocalVariable(constr, indexInLocals, argumentTypes[i], argumentTypes[i]);
			}
			constr.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(classParent),
					"<init>", constrDesc, false);
			constr.visitInsn(Opcodes.RETURN);
			constr.visitMaxs(0, 0);
			constr.visitEnd();
		}
		
		{
			// Generate a jsmud-internal field.
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, CallSiteGenerator.FIELD_IS_EXECUTED_BY_JSMUD,
					Type.BOOLEAN_TYPE.getDescriptor(), null, null);

			cw.visitField(Opcodes.ACC_PUBLIC, FIELD_THREAD_EXECUTOR, Type.getDescriptor(ThreadExecutor.class),
					null, null);
		}
		
		{
			final MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PROTECTED, "run", "()V", null, null);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, classNameInt, FIELD_THREAD_EXECUTOR,
					Type.getDescriptor(ThreadExecutor.class));
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ThreadExecutor.class), "run", "(Ljava/lang/Thread;)V", false);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		final byte[] bufClass = cw.toByteArray();
		if (FOLDER_DUMP_THREAD_CLASSES != null) {
			final File fileCallSiteClass = new File(FOLDER_DUMP_THREAD_CLASSES, classNameSimple + ".class");
			LOG.debug(String.format("Dump thread-class into (%s)", fileCallSiteClass));
			try {
				Files.write(fileCallSiteClass.toPath(), bufClass);
			} catch (IOException e) {
				throw new JvmException(String.format("IO-error while dumping class (%s) into file (%s)",
						classNameInt, fileCallSiteClass), e);
			}
		}
		final Class<?> classThreadChild = classLoader.defineJsmudClass(className, bufClass);
		mapBytecodes.put(classThreadChild, bufClass);
		
		mapThreadChildClasses.put(classParent, classThreadChild);
		return classThreadChild;
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
