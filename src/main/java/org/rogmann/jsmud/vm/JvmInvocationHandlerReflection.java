package org.rogmann.jsmud.vm;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.concurrent.Callable;

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

	/** filter to determine invocation-handlers to be interpreted */
	private final ClassExecutionFilter filterProxy;

	/** invocation-handler in {@link Proxy} of JRE 8 or JRE 11 or <code>null</code> */
	private final Field fFieldInvocationHandlerJreInternal;

	/** configuration of jsmud-analysis */
	private final JsmudConfiguration configuration;

	/**
	 * Constructor
	 * @param filterProxy filter to select invocation-handlers to be interpreted, <code>null</code> if proxies should be executed by the underlying JVM
	 * @param configuration configuration of jsmud-analysis
	 */
	public JvmInvocationHandlerReflection(final ClassExecutionFilter filterProxy, final JsmudConfiguration configuration) {
		this.filterProxy = filterProxy;
		this.configuration = configuration;
		if (filterProxy != null && configuration.isInvocationHandlerUseField) {
			Field fieldInvocationHandlerJre8;
			try {
				fieldInvocationHandlerJre8 = Proxy.class.getDeclaredField("h");
				fieldInvocationHandlerJre8.setAccessible(true);
			}
			catch (NoSuchFieldException e) {
				fieldInvocationHandlerJre8 = null;
			}
			catch (SecurityException e) {
				fieldInvocationHandlerJre8 = null;
				LOG.error(String.format("Accessing internals of (%s) is not allowed", Proxy.class), e);
			}
			catch (RuntimeException e) {
				// e.g. java.lang.reflect.InaccessibleObjectException.
				fieldInvocationHandlerJre8 = null;
				LOG.error(String.format("Accessing internals of (%s) was not successful", Proxy.class), e);
			}
			fFieldInvocationHandlerJreInternal = fieldInvocationHandlerJre8;
		}
		else {
			fFieldInvocationHandlerJreInternal = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public InvokeFlow preprocessStaticCall(MethodFrame frame, final MethodInsnNode mi, OperandStack stack) throws Throwable {
		InvokeFlow doContinueWhile = null;
		if ("java/lang/Class".equals(mi.owner) && "forName".equals(mi.name)
				&& configuration.isSimulateReflection) {
			// Emulation of Class.forName, we may want to patch the class to be loaded.
			final Type[] argumentTypes = Type.getArgumentTypes(mi.desc);
			final int numArgs = argumentTypes.length;
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Emulate Class.forName(%s) loading class (%s), stack %s",
						mi.desc, stack.peek(numArgs - 1), stack));
			}
			// We ignore initialize-flag in the arguments.
			final boolean hasClassLoader = "java.lang.ClassLoader".equals(argumentTypes[numArgs - 1].getClassName());
			ClassLoader classLoader = hasClassLoader ? (ClassLoader) stack.peek() : null;
			for (int i = 1; i < numArgs; i++) {
				stack.pop();
			}
			final String className = (String) stack.pop();
			LOG.info("cl: " + classLoader + ", bytecode: " + frame.registry.getBytecodeOfDefinedClass(frame.registry.getClassLoader(), className));
			if (hasClassLoader
					&& frame.registry.getBytecodeOfDefinedClass(frame.registry.getClassLoader(), className) != null
					&& classLoader != frame.registry.getClassLoader()
					&& frame.registry.getClassLoader() instanceof JsmudClassLoader) {
				final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) frame.registry.getClassLoader();
				if (jsmudClassLoader.patchFilter.test(className)) {
					LOG.info(String.format("Load class (%s) via (%s) instead of (%s)",
							className, frame.registry.getClassLoader(), classLoader));
					classLoader = frame.registry.getClassLoader();
				}
			}
			final Class<?> loadedClass;
			try {
				// Class.forName: We use the registry itself as reference-class-loader.
				if (classLoader != null) {
					loadedClass = frame.registry.loadClass(className, classLoader);
				}
				else {
					loadedClass = frame.registry.loadClass(className, frame.registry.getClass());
				}
			}
			catch (JvmUncaughtException e) {
				final boolean handleException = frame.handleCatchException(e.getCause());
				if (handleException) {
					return InvokeFlow.EXEC_CATCH;
				}
				// This exception isn't handled here.
				throw e;
			}
			catch (Exception e) {
				final boolean handleException = frame.handleCatchException(e);
				if (handleException) {
					return InvokeFlow.EXEC_CATCH;
				}
				// This exception isn't handled here.
				throw e;
			}
			stack.push(loadedClass);
			doContinueWhile = InvokeFlow.EXEC_OK;
		}
		else if ("java/security/AccessController".equals(mi.owner) && "doPrivileged".equals(mi.name)
				&& configuration.isEmulateAccessController) {
			final Type[] argumentTypes = Type.getArgumentTypes(mi.desc);
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Mock method %s%s", mi.name, mi.desc));
			}
			// We ignore initialize-flag and class-loader in the arguments.
			for (int i = 1; i < argumentTypes.length; i++) {
				stack.pop();
			}
			final Object oAction = stack.peek();
			final Class<?> classAction;
			final String descMethDoPrivileged;
			if (oAction instanceof PrivilegedAction) {
				classAction = PrivilegedAction.class;
				descMethDoPrivileged = "(Ljava/security/PrivilegedAction;)Ljava/lang/Object;";
			}
			else if (oAction instanceof PrivilegedExceptionAction) {
				classAction = PrivilegedExceptionAction.class;
				descMethDoPrivileged = "(Ljava/security/PrivilegedExceptionAction;)Ljava/lang/Object;";
			}
			else {
				throw new JvmException(String.format("Unexpected first argument (%s) for (%s%s) in (%s)",
						oAction, mi.name, mi.desc, mi.owner));
			}
			if (oAction instanceof JvmCallSiteMarker) {
				LOG.info("CallSite: " + oAction);
			}

			final SimpleClassExecutor executor = frame.registry.getClassExecutor(MockMethods.class);
			if (executor == null) {
				throw new JvmException(String.format("Mock method %s%s but no executor for (%s)", mi.name, mi.desc, oAction.getClass()));
			}

			final Method methodRun = MockMethods.class.getDeclaredMethod(mi.name, classAction);
			final Object objReturn;
			try {
				objReturn = executor.executeMethod(Opcodes.INVOKESTATIC, methodRun, descMethDoPrivileged, stack);
			}
			catch (JvmUncaughtException e) {
				final boolean doContinueWhileFlag = frame.handleCatchException(e.getCause());
				if (doContinueWhileFlag) {
					return InvokeFlow.EXEC_CATCH;
				}
				// This exception isn't handled here.
				throw e;
			}
			stack.push(objReturn);
			doContinueWhile = InvokeFlow.EXEC_OK;
		}
		else if ("java/lang/System".equals(mi.owner) && "exit".equals(mi.name)
				&& configuration.isCatchSystemExit) {
			final Integer rc = (Integer) stack.pop(); 
			throw new JvmException(String.format("System.exit(%d) has been called", rc));
		}
		return doContinueWhile;
	}

	/** {@inheritDoc} */
	@Override
	public InvokeFlow preprocessInstanceCall(MethodFrame frame, final MethodInsnNode mi,
			final Object objRefStack, final OperandStack stack) throws Throwable {
		InvokeFlow doContinueWhile = null;
		if ("java/lang/Object".equals(mi.owner)) {
			if ("wait".equals(mi.name)) {
				final Long timeout;
				final Integer nanos;
				if ("()V".equals(mi.desc)) {
					timeout = Long.valueOf(0);
					nanos = Integer.valueOf(0);
				}
				else if ("(J)V".equals(mi.desc)) {
					timeout = (Long) stack.peek();
					nanos = Integer.valueOf(0);
				}
				else if ("(JI)V".equals(mi.desc)) {
					nanos = (Integer) stack.peek();
					timeout = (Long) stack.peek();
				}
				else {
					throw new JvmException(String.format("Unexpected signature %s of wait-method (%s) in (%s)",
							mi.desc, mi.name, objRefStack));
				}
				final Object monitorObj = stack.peek();
				try {
					frame.registry.doObjectWait(monitorObj, timeout.longValue(), nanos.intValue());
				} catch (InterruptedException e) {
					final boolean doContinueWhileE = frame.handleCatchException(e);
					if (doContinueWhileE) {
						return InvokeFlow.EXEC_CATCH;
					}
					// This exception isn't handled here.
					throw new JvmUncaughtException(String.format("interruption of Object#wait(%d,%d)",
							timeout, nanos), e);
				}
				doContinueWhile = InvokeFlow.EXEC_OK;
				return doContinueWhile;
			}
			if ("notify".equals(mi.name) && "()V".equals(mi.desc)) {
				final Object monitorObj = stack.peek();
				frame.registry.doNotify(monitorObj);
				doContinueWhile = InvokeFlow.EXEC_OK;
				return doContinueWhile;
			}
			if ("notifyAll".equals(mi.name) && "()V".equals(mi.desc)) {
				final Object monitorObj = stack.peek();
				frame.registry.doNotifyAll(monitorObj);
				doContinueWhile = InvokeFlow.EXEC_OK;
				return doContinueWhile;
			}
		}
		else if ("java/lang/reflect/Constructor".equals(mi.owner) && "newInstance".equals(mi.name)
				&& configuration.isSimulateReflection) {
			final Constructor<?> constr = (Constructor<?>) stack.peek(1);
			final Class<?> classInit = constr.getDeclaringClass();
			frame.registry.checkClassInitialization(classInit);
			final SimpleClassExecutor executor = frame.registry.getClassExecutor(classInit);
			final ReflectionHelper reflectionHelper = frame.getReflectionHelper();
			if (executor != null
					&& (reflectionHelper.canConstructViaReflectionFactory()
							|| frame.registry.isClassConstructorJsmudPatched(classInit))) {
				// We try to instantiate the instance.
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Emulate constructor (%s) in class (%s), stack %s",
							constr, classInit.getName(), stack));
				}
				final Object oObjRef = reflectionHelper.instantiateClass(classInit);
				final Object[] oArgs = (Object[]) stack.pop();
				// Remove the Constructor-instance and replace it with the new instance.
				stack.pop();
				stack.push(oObjRef);
				if (oArgs != null) {
					// cglib calls newInstance with null
					for (int i = 0; i < oArgs.length; i++) {
						stack.push(oArgs[i]);
					}
				}
				final String descr = Type.getConstructorDescriptor(constr);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Execute constructor (%s) in class (%s), stack %s",
							constr, classInit.getName(), stack));
				}
				try {
					executor.executeMethod(Opcodes.INVOKEVIRTUAL, constr, descr, stack);
				}
				catch (JvmUncaughtException e) {
					final boolean doContinueWhileE = frame.handleCatchException(e.getCause());
					if (doContinueWhileE) {
						return InvokeFlow.EXEC_CATCH;
					}
					// This exception isn't handled here.
					throw e;
				}
				stack.push(oObjRef);
				doContinueWhile = InvokeFlow.EXEC_OK;
			}
		}
		else if ("java/lang/reflect/Method".equals(mi.owner) && "invoke".equals(mi.name)
				&& configuration.isSimulateReflection
				&& Proxy.class.isAssignableFrom(((Method) stack.peek(2)).getDeclaringClass())
				&& filterProxy != null) {
			// Reflection on a method of a proxy-implementation.
			// We have on stack: method, proxy-object, method-arguments.
			final Method reflMethod = (Method) stack.peek(2);
			final Proxy oProxy = (Proxy) stack.peek(1);
			final InvocationHandler ih = getInvocationHandler(oProxy);
			SimpleClassExecutor executor = null;
			if (filterProxy.isClassToBeSimulated(ih.getClass())) {
				executor = frame.registry.getClassExecutor(ih.getClass());
			}
			if (executor != null) {
				// Remove the reflection-call from stack.
				final Object[] methArgs = (Object[]) stack.pop();
				stack.pop();
				stack.pop();
				final Method intfMethod = findInterfaceMethodOfProxy(oProxy, reflMethod);
				// We need on stack: invocation-handler, proxy-object, interface-method, method-arguments.
				final Object[] oIhArgs = { ih };
				stack.pushAndResize(0, oIhArgs);
				stack.push(oProxy);
				stack.push(intfMethod);
				stack.push(methArgs);
				final Method ihMethod = findMethodInvoke(ih);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Execute invocation-handler (%s) of proxy-class (%s) called by reflection, %s",
							ih, oProxy.getClass(), stack));
				}
				doContinueWhile = executeInvokeMethod(frame, ih, executor, ihMethod, stack);
			}
		}
		else if ("java/lang/reflect/Method".equals(mi.owner) && "invoke".equals(mi.name)
				&& configuration.isSimulateReflection) {
			// Emulation of Method#invoke?
			final Method reflMethod = (Method) stack.peek(2);
			final Class<?> classMethod = reflMethod.getDeclaringClass();
			SimpleClassExecutor executor = frame.registry.getClassExecutor(classMethod);
			Method invMethod = reflMethod;
			final Object oObjRef = stack.peek(1);
			if (invMethod.getDeclaringClass().isInterface() && !(oObjRef instanceof Proxy)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("looking for implementation of (%s)", reflMethod));
				}
				final String methodName = reflMethod.getName();
				Type[] paramTypes = Type.getArgumentTypes(reflMethod);
				Type retType = Type.getReturnType(reflMethod);
				// We have to look for an implementing method.
				invMethod = MethodFrame.findMethodInClass(methodName, paramTypes, retType, oObjRef.getClass());
				if (invMethod == null) {
					final boolean isVirtual = false;
					invMethod = MethodFrame.findMethodInInterfaces(oObjRef.getClass(), methodName, paramTypes, isVirtual, oObjRef.getClass());
				}
				if (invMethod == null) {
					throw new JvmException(String.format("No implementing-method for method (%s) in (%s)",
							reflMethod, oObjRef.getClass()));
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("method (%s) -> (%s)", reflMethod, invMethod));
				}
				executor = frame.registry.getClassExecutor(invMethod.getDeclaringClass());
			}
			if (executor != null) {
				// We can invoke the method in the declaring class.
				final Object[] oArgs = (Object[]) stack.pop();
				stack.pop(); // oObjRef
				// Remove the method-instance.
				stack.pop();
				final boolean isNonStatic = !Modifier.isStatic(reflMethod.getModifiers());
				if (isNonStatic) {
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
				if (oObjRef instanceof Proxy && !(objRefStack instanceof JvmCallSiteMarker)) {
					// Reflection invoke, objRef is proxy, no filter-proxy.
					doContinueWhile = executeProxyInvokeMethod(frame, (Proxy) oObjRef, stack, reflMethod.getName(), descr);
					if (doContinueWhile == null) {
						// e.g. JUnit-test testsReflectionAnnotation.
						final Type[] types = Type.getArgumentTypes(descr);
						frame.executeInvokeMethodNative(reflMethod, oObjRef, oArgs.length,
								types, isNonStatic);
					}
				}
				else {
					try {
						objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, invMethod, descr, stack);
					}
					catch (JvmUncaughtException e) {
						final boolean doContinueWhileFlag = frame.handleCatchException(e.getCause());
						if (doContinueWhileFlag) {
							return InvokeFlow.EXEC_CATCH;
						}
						// This exception isn't handled here.
						throw e;
					}
					if (!Void.class.equals(reflMethod.getReturnType())) {
						stack.push(objReturn);
					}
				}
				doContinueWhile = InvokeFlow.EXEC_OK;
			}
			else if ("defineClass".equals(reflMethod.getName()) && ClassLoader.class.equals(reflMethod.getDeclaringClass())
					&& reflMethod.getParameterCount() == 5) {
				// Example:
				// INVOKEVIRTUAL java/lang/reflect/Method#invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;:
				// stack: currLen=3, maxLen=6, types=[Method, JsmudClassLoader, Object[]],
				// values=[protected final java.lang.Class java.lang.ClassLoader.defineClass(java.lang.String,byte[],int,int,java.security.ProtectionDomain) throws java.lang.ClassFormatError,
				//         org.rogmann.jsmud.vm.JsmudClassLoader@4cb2c100, [Ljava.lang.Object;@443dae2]
				final Method m = (Method) stack.peek(2);
				LOG.info(String.format("defineClass: method %s, class-loader of declared class %s",
						m, m.getDeclaringClass().getClassLoader()));
				ClassLoader classLoader = (ClassLoader) stack.peek(1);
				final Object[] args = (Object[]) stack.peek(0);
				final String className = (String) args[0];
                final ClassLoader classLoaderDefault = frame.registry.getClassLoader();
                JsmudClassLoader patchClassLoader = null;
                if (classLoaderDefault instanceof JsmudClassLoader) {
                        final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) classLoaderDefault;
                        if (jsmudClassLoader.patchFilter.test(className)) {
                                // The class should be loaded in the JsmudClassLoader because
                                // otherwise its classes would reference the application's classloader
                                // but the patched classes would reference the JsmudClassLoader.
                                LOG.info(String.format("defineClass: Replace class-loader %s by %s",
                                                classLoader, jsmudClassLoader));
                                stack.pop(); // arguments
                                stack.pop(); // classLoader
                                stack.push(jsmudClassLoader);
                                stack.push(args);
                                classLoader = jsmudClassLoader;
                                patchClassLoader = jsmudClassLoader;
                        }
                }
				final byte[] buf = (byte[]) args[1];
				final int offset = ((Integer) args[2]).intValue();
				final int len = ((Integer) args[3]).intValue();
				final byte[] bufBytecode;
				if (patchClassLoader != null) {
					// We patch the bytecode before loading.
					final ByteArrayInputStream bais = new ByteArrayInputStream(buf, offset, len);
					final byte[] bufBytecodePatched = patchClassLoader.patchClass(className, bais);
					if (bufBytecodePatched != null) {
						LOG.info(String.format("defineClass (%s) of (%s) in buffer (%s/%d/%d), patch bytecode",
								className, classLoader, buf,
								Integer.valueOf(offset), Integer.valueOf(len)));
						final Class<?> classPatched = patchClassLoader.definePatchedClass(className,
								patchClassLoader, bufBytecodePatched);
						stack.pop();
						stack.pop();
						stack.push(classPatched);
						frame.registry.defineClass(classLoader, className, bufBytecodePatched);
						return InvokeFlow.EXEC_OK;
					}
				}
				LOG.info(String.format("defineClass (%s) of (%s) in buffer (%s/%d/%d), store bytecode",
						className, classLoader, buf,
						Integer.valueOf(offset), Integer.valueOf(len)));
				bufBytecode = Arrays.copyOfRange(buf, offset, len);
				// Register the bytecode to be defined.
				frame.registry.defineClass(classLoader, className, bufBytecode);
			}
		}
		else if (objRefStack instanceof Proxy && !(objRefStack instanceof JvmCallSiteMarker)) {
			// proxy-instance
			doContinueWhile = executeProxyInvokeMethod(frame, (Proxy) objRefStack, stack, mi.name, mi.desc);
		}
		else if ("clone".equals(mi.name) && mi.owner.startsWith("[")) {
			// clone an array.
			final Object obj = stack.pop();
			if (obj instanceof Object[]) {
				final Object[] oArray = (Object[]) obj;
				stack.push(oArray.clone());
			}
			else if (obj instanceof boolean[]) {
				final boolean[] pArray = (boolean[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof byte[]) {
				final byte[] pArray = (byte[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof char[]) {
				final char[] pArray = (char[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof short[]) {
				final short[] pArray = (short[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof int[]) {
				final int[] pArray = (int[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof long[]) {
				final long[] pArray = (long[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof double[]) {
				final double[] pArray = (double[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj instanceof float[]) {
				final float[] pArray = (float[]) obj;
				stack.push(pArray.clone());
			}
			else if (obj == null) {
				throw new NullPointerException();
			}
			else {
				throw new JvmException(String.format("Unexpected clone-object of type (%s)", obj.getClass()));
			}
			doContinueWhile = InvokeFlow.EXEC_OK;
		}
		return doContinueWhile;
	}

	/** {@inheritDoc} */
	@Override
	public void preprocessInvokeSpecialCall(final MethodInsnNode mi, MethodFrame frame, final OperandStack stack) {
		if ("java/util/concurrent/FutureTask".equals(mi.owner) && "<init>".equals(mi.name) && "(Ljava/util/concurrent/Callable;)V".equals(mi.desc)) {
			// Replace the callable with one controlled by jsmud-analysis.
			final Callable<?> callable = (Callable<?>) stack.pop();
			final ThreadExecutor threadExecutor = new ThreadExecutor(frame.registry, frame.visitor);
			final Callable<Object> callableJsmud = new Callable<Object>() {
				/** {@inheritDoc} */
				@Override
				public Object call() {
					final Object returnObj = threadExecutor.call(callable);
					return returnObj;
				}
			};
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("FutureTask#call: replace callable %s with %s", callable, callableJsmud));
			}
			stack.push(callableJsmud);
		}
	}

	/**
	 * An invocation-handler expects a method of an interface.
	 * This method looks for an interface-method of a proxy-method.
	 * @param oProxy proxy-instance
	 * @param proxyMethod method of proxy
	 * @return
	 */
	static Method findInterfaceMethodOfProxy(final Proxy oProxy, final Method proxyMethod) {
		Method intfMethod = proxyMethod;
		if (!intfMethod.getDeclaringClass().isInterface()) {
			// The invocation-handler expects the given method to be an interface-method.
			// We look for the corresponding interface-method.
			for (final Class<?> classIntf : oProxy.getClass().getInterfaces()) {
				try {
					intfMethod = classIntf.getDeclaredMethod(proxyMethod.getName(), proxyMethod.getParameterTypes());
				} catch (NoSuchMethodException e) {
					continue;
				} catch (SecurityException e) {
					throw new JvmException(String.format("The interface (%s) of proxy (%s) isn't allowed to be analyzed",
							classIntf, oProxy.getClass()), e);
				}
				break;
			}
		}
		return intfMethod;
	}

	private static Method findMethodInvoke(final InvocationHandler ih) throws NoSuchMethodException {
		Method ihMethod = null;
		Class<?> classLoopIh = ih.getClass();
		int parentCounter = 0;
		while (true) {
			try {
				ihMethod = classLoopIh.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
				break;
			} catch (NoSuchMethodException e) {
				// We try the super-class.
				// Example in the wild: https://github.com/apache/tomcat/blob/9.0.x/modules/jdbc-pool/src/main/java/org/apache/tomcat/jdbc/pool/DisposableConnectionFacade.java
				final Class<?> loopIhSuper = classLoopIh.getSuperclass();
				if (loopIhSuper == null || Object.class.equals(loopIhSuper)) {
					throw e;
				}
				classLoopIh = loopIhSuper;
			}
			parentCounter++;
			if (parentCounter == 100) {
				throw new JvmException(String.format("Unexpected parent-super-depth in class %s", ih.getClass()));
			}
		}
		return ihMethod;
	}

	/**
	 * Executes the invoke-method of a proxy.
	 * @param frame current method-frame
	 * @param proxy current instance
	 * @param stack current stack
	 * @param miName name of the method to be executed
	 * @param miDesc signature of the method to be executed
	 * @return how to continue execution
	 * @throws Throwable
	 */
	private InvokeFlow executeProxyInvokeMethod(MethodFrame frame, final Proxy proxy, final OperandStack stack,
			final String miName, final String miDesc)
			throws IllegalAccessException, NoSuchMethodException, Throwable {
		InvokeFlow doContinueWhile = null;
		final Class<? extends Proxy> proxyClass = proxy.getClass();
		if (filterProxy != null) {
			final InvocationHandler ih = getInvocationHandler(proxy);
			SimpleClassExecutor executor = null;
			if (filterProxy.isClassToBeSimulated(ih.getClass())) {
				executor = frame.registry.getClassExecutor(ih.getClass());
			}
			if (executor != null) {
				// We are allowed to execute the invocation-handler.
				// We have to find the method to be called.
				Method methodIh = null;
				boolean isInSuperClass = false;
				Class<?> classProxy = proxyClass;
loopClassIh:
				while (true) {
					for (final Method method : classProxy.getDeclaredMethods()) {
						if (method.getName().equals(miName)
								&& miDesc.equals(Type.getMethodDescriptor(method))) {
							methodIh = method;
							break loopClassIh;
						}
					}
					if (Object.class.equals(classProxy)) {
						throw new JvmException(String.format("No such method (%s%s) in proxy (%s)", miName, miDesc, proxyClass));
					}
					// If the method searched is in a super-class we will execute the proxy itself,
					// not the invocation-handler.
					classProxy = classProxy.getSuperclass();
					isInSuperClass  = true;
				}
				if (!isInSuperClass) {
					// We collect the method's arguments from stack.
					final Type[] origTypes = Type.getArgumentTypes(miDesc);
					final int numArgs = origTypes.length;
					final Object[] oArgs = new Object[numArgs];
					for (int i = 0; i < numArgs; i++) {
						oArgs[numArgs - 1 - i] = stack.pop();
					}
					// We replace the proxy with the invocation-handler.
					stack.pop();
					stack.push(ih);
					// We call the method in the invocation-handler.
					final Method intfMethod = findInterfaceMethodOfProxy(proxy, methodIh);
					final Object[] oIhArgs = { proxy, intfMethod, oArgs };
					stack.pushAndResize(0, oIhArgs);
					final Method ihMethod = findMethodInvoke(ih);
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("Execute invocation-handler (%s) of proxy (%s), stack %s",
								ih, proxyClass, stack));
					}
					doContinueWhile = executeInvokeMethod(frame, ih, executor, ihMethod, stack);
				}
			}
		}
		return doContinueWhile;
	}

	/**
	 * Executes an invoke-method of an invocation-handler.
	 * @param frame current frame
	 * @param ih invocation-handler to be called
	 * @param defaultExecutor default executor
	 * @param ihMethod method to be called
	 * @param stack stack
	 * @return how to continue execution
	 * @throws Throwable in case of an exception
	 */
	private static InvokeFlow executeInvokeMethod(MethodFrame frame, final InvocationHandler ih, final SimpleClassExecutor defaultExecutor,
			final Method ihMethod, final OperandStack stack) throws Throwable {
		SimpleClassExecutor executor = defaultExecutor;
		InvokeFlow doContinueWhile;
		final Class<?> loopIh = ihMethod.getDeclaringClass();
		if (!ih.getClass().equals(loopIh)) {
			// We need the executor of a super-class.
			executor = frame.registry.getClassExecutor(loopIh);
			if (executor == null) {
				throw new JvmException(String.format("Can't get a executor of super-class (%s) of (%s) for executing invoke-method (%s)",
						loopIh, ih.getClass(), ihMethod));
			}
		}
		final String descr = Type.getMethodDescriptor(ihMethod);
		final Object objReturn;
		try {
			objReturn = executor.executeMethod(Opcodes.INVOKEVIRTUAL, ihMethod, descr, stack);
		}
		catch (JvmUncaughtException e) {
			final boolean doContinueWhileE = frame.handleCatchException(e.getCause());
			if (doContinueWhileE) {
				return InvokeFlow.EXEC_CATCH;
			}
			// This exception isn't handled here.
			throw e;
		}
		stack.push(objReturn);
		doContinueWhile = InvokeFlow.EXEC_OK;
		return doContinueWhile;
	}

	/**
	 * Gets the invocation-handler of a proxy.
	 * @param oProxy proxy-instance
	 * @return invocation-handler
	 * @throws IllegalAccessException if the proxy can't be accessed
	 */
	private InvocationHandler getInvocationHandler(final Proxy oProxy) throws IllegalAccessException {
		final InvocationHandler ih;
		if (fFieldInvocationHandlerJreInternal != null) {
			// The field is preferred because the class-loaders might be different.
			ih = (InvocationHandler) fFieldInvocationHandlerJreInternal.get(oProxy);
		}
		else {
			ih = Proxy.getInvocationHandler(oProxy);
		}
		return ih;
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
