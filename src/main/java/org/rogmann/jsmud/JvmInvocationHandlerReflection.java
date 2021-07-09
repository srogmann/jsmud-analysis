package org.rogmann.jsmud;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Default-implementation of a invocation-handler to be used in connection with a {@link JsmudClassLoader}.
 */
public class JvmInvocationHandlerReflection implements JvmInvocationHandler {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmInvocationHandlerReflection.class);

	/** invocation-handler in {@link Proxy} of JRE 8 or <code>null</code> */
	private final Field fFieldInvocationHandlerJre8;

	/**
	 * Constructor
	 */
	public JvmInvocationHandlerReflection() {
		Field fieldInvocationHandlerJre8;
		try {
			fieldInvocationHandlerJre8 = Proxy.class.getDeclaredField("h");
			fieldInvocationHandlerJre8.setAccessible(true);
		} catch (NoSuchFieldException e) {
			fieldInvocationHandlerJre8 = null;
		} catch (SecurityException e) {
			throw new JvmException(String.format("Analyzing (%s) is not allowed", Proxy.class), e);
		}
		fFieldInvocationHandlerJre8 = fieldInvocationHandlerJre8;
	}

	/** {@inheritDoc} */
	@Override
	public Boolean preprocessStaticCall(MethodFrame frame, final MethodInsnNode mi, OperandStack stack) throws Exception {
		Boolean doContinueWhile = null;
		if ("java/lang/Class".equals(mi.owner) && "forName".equals(mi.name) && frame.registry.isSimulateReflection()) {
			// Emulation of Class.forName, we may want to patch the class to be loaded.
			final Type[] argumentTypes = Type.getArgumentTypes(mi.desc);
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Emulate Class.forName(%s) loading class (%s), stack %s",
						mi.desc, stack.peek(argumentTypes.length - 1), stack));
			}
			// We ignore initialize-flag and class-loader in the arguments.
			for (int i = 1; i < argumentTypes.length; i++) {
				stack.pop();
			}
			final String className = (String) stack.pop();
			final Class<?> loadedClass;
			try {
				loadedClass = frame.registry.loadClass(className);
			} catch (Exception e) {
				doContinueWhile = Boolean.valueOf(frame.handleCatchException(e));
				if (doContinueWhile.booleanValue()) {
					doContinueWhile = Boolean.TRUE;
				}
				// This exception isn't handled here.
				throw e;
			}
			stack.push(loadedClass);
			doContinueWhile = Boolean.FALSE;
		}
		return doContinueWhile;
	}

	/** {@inheritDoc} */
	@Override
	public Boolean preprocessInstanceCall(MethodFrame frame, final MethodInsnNode mi,
			final Object objRefStack, final OperandStack stack) throws Throwable {
		Boolean doContinueWhile = null;
		if ("java/lang/reflect/Constructor".equals(mi.owner) && "newInstance".equals(mi.name) && frame.registry.isSimulateReflection()) {
			// Emulation of Constructor#newInstance?
			final Constructor<?> constr = (Constructor<?>) stack.peek(1);
			final Class<?> classInit = constr.getDeclaringClass();
			final SimpleClassExecutor executor = frame.registry.getClassExecutor(classInit);
			if (executor != null && frame.registry.isClassConstructorJsmudPatched(classInit)) {
				// The class is patched. We can instantiate the instance.
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Emulate constructor (%s) in class (%s), stack %s",
							constr, classInit.getName(), stack));
				}
				final Object oObjRef;
				try {
					final Constructor<?> constrDefault = classInit.getDeclaredConstructor();
					constrDefault.setAccessible(true);
					oObjRef = constrDefault.newInstance();
				} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
						| IllegalArgumentException | InvocationTargetException e) {
					throw new JvmException(String.format("Couldn't instanciate %s via default-constructor", classInit.getName()), e);
				}
				final Object[] oArgs = (Object[]) stack.pop();
				// Remove the Constructor-instance and replace it with the new instance.
				stack.pop();
				stack.push(oObjRef);
				for (int i = 0; i < oArgs.length; i++) {
					stack.push(oArgs[i]);
				}
				final String descr = Type.getConstructorDescriptor(constr);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Execute constructor (%s) in class (%s), stack %s",
							constr, classInit.getName(), stack));
				}
				try {
					executor.executeMethod(Opcodes.INVOKEVIRTUAL, constr, descr, stack);
				}
				catch (Throwable e) {
					final boolean doContinueWhileE = frame.handleCatchException(e);
					if (doContinueWhileE) {
						return Boolean.TRUE;
					}
					// This exception isn't handled here.
					throw e;
				}
				stack.push(oObjRef);
				doContinueWhile = Boolean.FALSE;
			}
		}
		else if ("java/lang/reflect/Method".equals(mi.owner) && "invoke".equals(mi.name)
				&& frame.registry.isSimulateReflection()) {
			// Emulation of Method#invoke?
			final Method reflMethod = (Method) stack.peek(2);
			final Class<?> classMethod = reflMethod.getDeclaringClass();
			final SimpleClassExecutor executor = frame.registry.getClassExecutor(classMethod);
			if (executor != null) {
				// We can invoke the method in the patched class.
				final Object[] oArgs = (Object[]) stack.pop();
				final Object oObjRef = stack.pop();
				// Remove the method-instance.
				stack.pop();
				if (!Modifier.isStatic(reflMethod.getModifiers())) {
					// Non-static method. We need an object-instance on the stack.
					stack.push(oObjRef);
				}
				for (int i = 0; i < oArgs.length; i++) {
					stack.push(oArgs[i]);
				}
				final String descr = Type.getMethodDescriptor(reflMethod);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Emulate method (%s) in class (%s), stack %s",
							reflMethod.getName(), classMethod.getName(), stack));
				}
				final Object objReturn;
				try {
					objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, reflMethod, descr, stack);
				}
				catch (Throwable e) {
					final boolean doContinueWhileFlag = frame.handleCatchException(e);
					if (doContinueWhileFlag) {
						return Boolean.TRUE;
					}
					// This exception isn't handled here.
					throw e;
				}
				if (!Void.class.equals(reflMethod.getReturnType())) {
					stack.push(objReturn);
				}
				doContinueWhile = Boolean.FALSE;
			}
		}
		else if (objRefStack instanceof Proxy && !(objRefStack instanceof JvmCallSiteMarker)) {
			// proxy-instance
			final Object oProxy = objRefStack;
			final Class<? extends Object> proxyClass = oProxy.getClass();
			if (frame.registry.executionFilter.isClassToBeSimulated(proxyClass) && fFieldInvocationHandlerJre8 != null) {
				// We are allowed to execute this proxy.
				final Object ih = fFieldInvocationHandlerJre8.get(oProxy);
				final SimpleClassExecutor executor = frame.registry.getClassExecutor(ih.getClass());
				if (executor != null) {
					// We are allowed to execute the invocation-handler.
					// We collect the method's arguments from stack.
					final Type[] origTypes = Type.getArgumentTypes(mi.desc);
					final int numArgs = origTypes.length;
					final Object[] oArgs = new Object[numArgs];
					for (int i = 0; i < numArgs; i++) {
						oArgs[numArgs - 1 - i] = stack.pop();
					}
					// we have to find the method to be called.
					Method methodIh = null;
					for (final Method method : proxyClass.getDeclaredMethods()) {
						if (mi.desc.equals(Type.getMethodDescriptor(method))) {
							methodIh = method;
							break;
						}
					}
					if (methodIh == null) {
						throw new JvmException(String.format("No such method (%s%s) in proxy (%s)", mi.name, mi.desc, proxyClass));
					}
					// We call the method in the invocation-handler.
					final Object[] oIhArgs = { oProxy, methodIh, oArgs };
					stack.pushAndResize(0, oIhArgs);
					final Method ihMethod = InvocationHandler.class.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
					final String descr = Type.getMethodDescriptor(ihMethod);
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("Execute invocation-handler (%s) of proxy (%s), stack %s",
								ih, proxyClass, stack));
					}
					final Object objReturn;
					try {
						objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, ihMethod, descr, stack);
					}
					catch (Throwable e) {
						final boolean doContinueWhileE = frame.handleCatchException(e);
						if (doContinueWhileE) {
							return Boolean.TRUE;
						}
						// This exception isn't handled here.
						throw e;
					}
					if (!Void.class.equals(methodIh.getReturnType())) {
						stack.push(objReturn);
					}
					doContinueWhile = Boolean.FALSE;
				}
			}
		}
		return doContinueWhile;
	}

	/** {@inheritDoc} */
	@Override
	public boolean postprocessCall(final MethodFrame frame, final MethodInsnNode mi,
			final OperandStack stack) throws Throwable {
		boolean exceptionHandlerHandled = false;
		if ("java/lang/Class".equals(mi.owner) && "newInstance".equals(mi.name) && "()Ljava/lang/Object;".equals(mi.desc)) {
			final Object objNew = stack.peek();
			final Class<? extends Object> objNewClass = objNew.getClass();
			final ClassLoader objClassLoader = objNewClass.getClassLoader();
			if (objClassLoader instanceof JsmudClassLoader) {
				final JsmudClassLoader cl = (JsmudClassLoader) objClassLoader;
				if (cl.isDefaultConstructorPatched(objNewClass)) {
					// The patched constructor gave an empty instance (e.g. without field-initialization).
					// We have to execute the original constructor.
					stack.push(objNew);
					final MethodInsnNode miInit = new MethodInsnNode(Opcodes.INVOKESPECIAL,
							Type.getInternalName(objNewClass), "<init>", "()V");
					exceptionHandlerHandled = frame.executeInvokeSpecial(miInit);
				}
			}
		}
		else if ("java/lang/Class".equals(mi.owner) &&
					("getDeclaredConstructors".equals(mi.name) || "getDeclaredConstructors".equals(mi.name))) {
			final Constructor<?>[] aConstr = (Constructor<?>[]) stack.peek();
			// We look for the default-constructor.
			for (int i = 0; i < aConstr.length; i++) {
				final Constructor<?> constr = aConstr[i];
				if (constr.getParameterCount() > 0) {
					continue;
				}
				final Class<?> constrClass = constr.getDeclaringClass();
				if (frame.registry.isClassConstructorJsmudAdded(constrClass)) {
					// The default-constructor doesn't exist in the original class.
					// We have to remove it.
					final Constructor<?>[] aConstrCorrected = new Constructor[aConstr.length - 1];
					if (i > 0) {
						System.arraycopy(aConstr, 0, aConstrCorrected, 0, i);
					}
					if (i + 1 < aConstr.length) {
						System.arraycopy(aConstr, i + 1, aConstrCorrected, i, aConstr.length - i - 1);
					}
					stack.pop();
					stack.push(aConstrCorrected);
					LOG.debug(String.format("Postprocessed %s for class %s: removed default-constructor", 
							mi.name, constrClass));
					break;
				}
			}
		}
		return exceptionHandlerHandled;
	}

}
