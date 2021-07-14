package org.rogmann.jsmud;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Simple class for executing methods in .class-files.
 */
public class SimpleClassExecutor {

	/** class-registry */
	private final ClassRegistry fRegistry;

	/** class to be executed */
	private final Class<?> fClass;
	/** class-loader of the class to be executed */
	private final ClassLoader fClassLoader;
	/** class-reader */
	private final ClassReader fReader;
	/** class-node */
	private final ClassNode fNode;
	/** methods by name */
	private final Map<String, List<MethodNode>> fMethods;
	
	/** map from method-descriptor to method-args */
	private final Map<String, Type[]> fMethodArgs;
	
	/** visitor */
	private final JvmExecutionVisitor fVisitor;

	/** invocation-handler */
	private final JvmInvocationHandler fInvocationHandler;

	/**
	 * Constructor
	 * @param registry class-registry
	 * @param clazz class to be executed
	 * @param visitor JVM-visitor
	 */
	public SimpleClassExecutor(final ClassRegistry registry, final Class<?> clazz,
			final JvmExecutionVisitor visitor, final JvmInvocationHandler invocationHandler) {
		fRegistry = registry;
		fClass = clazz;
		fClassLoader = clazz.getClassLoader();
		fReader = createClassReader(fClassLoader, clazz);
		fNode = new ClassNode();
		fReader.accept(fNode, 0);
		fMethods = new HashMap<>(fNode.methods.size());
		fMethodArgs = new HashMap<>(fNode.methods.size());
		for (MethodNode method : fNode.methods) {
			List<MethodNode> methodsByName = fMethods.get(method.name);
			if (methodsByName == null) {
				methodsByName = new ArrayList<>(1);
				fMethods.put(method.name, methodsByName);
			}
			methodsByName.add(method);
			fMethodArgs.put(method.desc, Type.getArgumentTypes(method.desc));
		}
		fVisitor = visitor;
		fInvocationHandler = invocationHandler;
	}

	/**
	 * Creates a class-reader.
	 * @param classLoader class-loader
	 * @param clazz class to be read
	 * @return reader
	 */
	public static ClassReader createClassReader(final ClassLoader classLoader, final Class<?> clazz) {
		// Some OSGi-class-loader depend on an absolute path.
		final String resName = '/' + clazz.getName().replace('.', '/') + ".class";
		final InputStream is = clazz.getResourceAsStream(resName);
		if (is == null) {
			throw new IllegalArgumentException(String.format("Can't read ressource (%s) of class (%s) in class-loader (%s)",
					resName, clazz.getName(), classLoader));
		}
		final ClassReader classReader;
		try {
			classReader = new ClassReader(is);
		} catch (IOException e) {
			throw new IllegalArgumentException(String.format("IO-error while reading class (%s) in class-loader (%s)",
					clazz.getName(), classLoader), e);
		}
		return classReader;
	}
	
	/**
	 * Executes a static method.
	 * @param objInstance instance-object, <code>null</code> in case of a static method
	 * @param pMethod method
	 * @param methodDesc descriptor of the method
	 * @param isInstanceMethod <code>true</code> if the method belongs to the instance
	 * @param args stack containing the arguments
	 * @return return-instance or <code>null</code>
	 */
	public Object executeMethod(final int invokeOpcode,
			final Executable pMethod, final String methodDesc, OperandStack args) throws Throwable {
		String methodName = (pMethod instanceof Constructor<?>) ? "<init>" : pMethod.getName();
		if (JsmudClassLoader.InitializerAdapter.METHOD_JSMUD_CLINIT.equals(methodName)) {
			methodName = "<clinit>";
		}
		final MethodNode method = loopkupMethod(methodName, methodDesc);

		final Type[] argsDefs = fMethodArgs.get(methodDesc);
		final MethodFrame frame = new MethodFrame(fRegistry, pMethod, method, argsDefs, fVisitor, fInvocationHandler);
		final Object methodReturnObj;
		try {
			fRegistry.pushMethodFrame(frame);
			methodReturnObj = frame.execute(invokeOpcode, args);
		}
		finally {
			fRegistry.popMethodFrame();
		}

		return methodReturnObj;
	}

	/**
	 * Looks up a method.
	 * @param methodName name of the method
	 * @param methodDesc descriptor of the method
	 * @return method
	 * @throws NoSuchMethodError in case of an unknown method
	 */
	public MethodNode loopkupMethod(final String methodName, final String methodDesc) throws NoSuchMethodError {
		final List<MethodNode> methods = fMethods.get(methodName);
		if (methods == null) {
			throw new NoSuchMethodError(String.format("No such method (%s) in (%s)",
					methodName, fClass.getName()));
		}
		MethodNode method = null;
		for (MethodNode loopMethod : methods) {
			if (loopMethod.desc.equals(methodDesc)) {
				method = loopMethod;
				break;
			}
		}
		if (method == null) {
			final String descs = methods.stream().map(m -> m.desc).collect(Collectors.joining(", "));
			throw new NoSuchMethodError(String.format("No such description (%s) of method (%s) in (%s) with class-loader (%s): %s",
					methodDesc, methodName, fClass.getName(), fClass.getClassLoader(), descs));
		}
		return method;
	}
}
