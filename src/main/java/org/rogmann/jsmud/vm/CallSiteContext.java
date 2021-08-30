package org.rogmann.jsmud.vm;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Context-information to execute JSMUD out of a call-site-instance.
 */
public class CallSiteContext {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(CallSiteContext.class);

	/** class-registry (VM) */
	private final ClassRegistry registry;
	/** owner-class of call-site */
	private final Class<?> classOwner;
	/** method-handle to be executed */
	private final Handle methodHandle;

	/**
	 * Constructor
	 * @param registry class-registry
	 * @param classOwner owner-class of call-site 
	 * @param methodHandle method-handle to be executed
	 */
	public CallSiteContext(ClassRegistry registry, Class<?> classOwner, Handle methodHandle) {
		this.registry = registry;
		this.classOwner = classOwner;
		this.methodHandle = methodHandle;
	}

	/**
	 * Executes a method.
	 * @param args
	 * @return return-instance
	 * @throws throwable in case of an exception
	 */
	public Object executeMethod(final Object[] args) throws Throwable {
		final String methodClassName = methodHandle.getOwner().replace('/', '.');
		final Class<?> classMethod;
		try {
			classMethod = registry.loadClass(methodClassName, classOwner);
		} catch (ClassNotFoundException e) {
			throw new JvmException(String.format("Can't load class (%s) to execute a call-site with method-handle (%s) in owner-class (%s)",
					methodClassName, methodHandle, classOwner), e);
		}

		final Object objReturn;
		final SimpleClassExecutor executor = registry.getClassExecutor(classMethod);
		if (executor == null) {
			// The method should be executed without simulation.
			final Type methodType = Type.getMethodType(methodHandle.getDesc());
			final Type[] types = methodType.getArgumentTypes();
			final Type returnType = methodType.getReturnType();
			final Method method = MethodFrame.findMethodInClass(methodHandle.getName(), types, returnType, classMethod);
			if (method == null) {
				throw new JvmException(String.format("Can't find method (%s) in class (%s)",
						methodHandle.getName(), methodHandle.getDesc(), classMethod));
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("executeMethod: non-simulated execution of %s%s in %s",
						methodHandle.getName(), methodHandle.getDesc(), classMethod));
			}
			
			method.setAccessible(true);
			final Object obj;
			final Object[] mArgs;
			if (Modifier.isStatic(method.getModifiers())) {
				obj = classMethod;
				mArgs = args;
			}
			else {
				obj = args[0];
				mArgs = Arrays.copyOfRange(args, 1, args.length);
			}
			objReturn = method.invoke(obj, mArgs);
		}
		else {
			final int opcodeInvoke = lookupInvokeOpcode(classOwner, methodHandle);
			final Type[] argTypes = Type.getArgumentTypes(methodHandle.getDesc());
			final Class<?>[] aArgs = new Class<?>[argTypes.length];
			for (int i = 0; i < argTypes.length; i++) {
				final Type type = argTypes[i];
				final Class<?> clazz = convertTypeToClass(type);
				aArgs[i] = clazz;
			}
			final Executable method = classMethod.getDeclaredMethod(methodHandle.getName(), aArgs);
			final OperandStack stack = new OperandStack(args.length);
			for (int i = 0; i < args.length; i++) {
				stack.push(args[i]);
			}
			objReturn = executor.executeMethod(opcodeInvoke, method, methodHandle.getDesc(), stack);
		}
		return objReturn;
	}

	private Class<?> convertTypeToClass(final Type type) throws ClassNotFoundException {
		final Class<?> clazz;
		final int sort = type.getSort();
		if (sort == Type.BOOLEAN) {
			clazz = boolean.class;
		}
		else if (sort == Type.BYTE) {
			clazz = byte.class;
		}
		else if (sort == Type.CHAR) {
			clazz = char.class;
		}
		else if (sort == Type.DOUBLE) {
			clazz = double.class;
		}
		else if (sort == Type.FLOAT) {
			clazz = float.class;
		}
		else if (sort == Type.INT) {
			clazz = int.class;
		}
		else if (sort == Type.LONG) {
			clazz = long.class;
		}
		else if (sort == Type.SHORT) {
			clazz = short.class;
		}
		else {
			clazz = registry.loadClass(type.getClassName(), classOwner);
		}
		return clazz;
	}

	/**
	 * Lookup of the invoke-instruction to execute a method-handle.
	 * @param classOwner owner-class
	 * @param methodHandle method-handle
	 * @return invoke-instruction
	 */
	static int lookupInvokeOpcode(final Class<?> classOwner, final Handle methodHandle) {
		final int opcodeInvoke;
		if (methodHandle.getTag() == Opcodes.H_INVOKEVIRTUAL) {
			opcodeInvoke = Opcodes.INVOKEVIRTUAL;
		}
		else if (methodHandle.getTag() == Opcodes.H_INVOKESTATIC) {
			opcodeInvoke = Opcodes.INVOKESTATIC;
		}
		else if (methodHandle.getTag() == Opcodes.H_INVOKESPECIAL && methodHandle.isInterface()) {
			opcodeInvoke = Opcodes.INVOKEINTERFACE;
		}
		else if (methodHandle.getTag() == Opcodes.H_INVOKESPECIAL) {
			opcodeInvoke = Opcodes.INVOKEVIRTUAL;
		}
		else if (methodHandle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
			opcodeInvoke = Opcodes.INVOKESPECIAL;
		}
		else if (methodHandle.getTag() == Opcodes.H_INVOKEINTERFACE) {
			opcodeInvoke = Opcodes.INVOKEINTERFACE;
		}
		else {
			throw new JvmException(String.format("Unexpected tag (%s) of call-site-method (%s) in (%s)",
					Integer.valueOf(methodHandle.getTag()), methodHandle, classOwner));
		}
		return opcodeInvoke;
	}

}
