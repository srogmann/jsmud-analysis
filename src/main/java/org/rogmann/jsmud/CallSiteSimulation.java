package org.rogmann.jsmud;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

/**
 * Generates a CallSite-instance when processing a INVOKEDYNAMIC-instruction. 
 */
public class CallSiteSimulation {
	
	/** proxy-object (we need a proxy for constructors et al.) */
	private final Object proxy;
	/** owner-class */
	protected final Class<?> ownerClass;
	/** owner-method */
	protected final Executable ownerMethod;

	/** INVOKEDYNAMIC-instruction */
	private final InvokeDynamicInsnNode instruction;

	/** method's name */
	protected final String name;
	
	/** method's descriptor */
	protected final String desc;
	
	/** bsm-tag */
	private final int bsmTag;
	/** lambda class */
	private final String lambdaOwner;
	/** lambda method-name */
	protected final String lambdaName;
	/** lambda method-description */
	protected final String lambdaDesc;
	/** arguments of INVOKEDYNAMIC-instruction */
	private final Object[] arguments;
	
	/**
	 * Constructor
	 * @param cl class-loader
	 * @param caller caller-instance
	 * @param clazz owner-class
	 * @param method owner-method
	 * @param idi INVOKEDYNAMIC-instruction
	 * @param lambdaHandle lambda-handle
	 * @param lambdaOwner lambda-owner-class 
	 * @param lambdaName lamda-method-name
	 * @param lambdaDesc lambda-description
	 * @param aArguments arguments of INVOKEDYNAMIC-instruction
	 */
	CallSiteSimulation(final ClassLoader cl, final Object caller,
			final Class<?> clazz, final Executable method, final InvokeDynamicInsnNode idi,
			final Handle lambdaHandle, Object[] aArguments) {
		this.ownerClass = clazz;
		this.ownerMethod = method;
		this.instruction = idi;
		this.name = idi.name;
		this.desc = idi.desc;
		this.bsmTag = lambdaHandle.getTag();
		this.lambdaOwner = lambdaHandle.getOwner();
		this.lambdaName = lambdaHandle.getName();
		this.lambdaDesc = lambdaHandle.getDesc();
		this.arguments = aArguments;

		// LambdaMetafactory.metafactory(caller, invokedName, invokedType, samMethodType, implMethod, instantiatedMethodType)
		final Type returnType = Type.getReturnType(idi.desc);
		final String className = returnType.getClassName();
		final Class<?> classInterface;
		try {
			classInterface = cl.loadClass(className);
		} catch (ClassNotFoundException e) {
			throw new JvmException(String.format("Can't load class (%s) for use in INVOKEDYNAMIC in (%s)",
					className, method), e);
		}
		final Class<?>[] interfaces = { classInterface, JvmCallSiteMarker.class };
		final InvocationHandler handler = new InvocationHandler() {
			/** {@inheritDoc} */
			@Override
			public Object invoke(final Object o, final Method methodCalled, final Object[] args) throws Throwable {
				if ("hashCode".equals(methodCalled.getName()) && methodCalled.getTypeParameters().length == 0) {
					// In case of a 32-bit-hashCode I don't expect many -128..127-hits.
					// But new Integer is marked for removal :-/.
					return Integer.valueOf(super.hashCode());
				}
				else if ("toString".equals(methodCalled.getName()) && methodCalled.getTypeParameters().length == 0) {
					return String.format("INVOKEDYNAMIC-Proxy(0x%x, %s%s as %s%s in %s)",
							Integer.valueOf(System.identityHashCode(this)), lambdaName, lambdaDesc,
							name, desc, ownerMethod);
				}
				throw new JvmException(String.format("Unexpected invocation of INVOKEDYNAMIC-proxy, clazz=%s, method=%s, method-called=%s",
						clazz, method, methodCalled));
			}
		};
		proxy = Proxy.newProxyInstance(cl, interfaces, handler);
	}
	
	/**
	 * Gets the proxy-object (result of INVOKEDYNAMIC-instruction).
	 * @return proxy
	 */
	public Object getProxy() {
		return proxy;
	}
	
	/**
	 * Gets the owner-class.
	 * @return class
	 */
	public Class<?> getOwnerClazz() {
		return ownerClass;
	}

	/**
	 * Gets the owner-method.
	 * @return method
	 */
	public Executable getOwnerMethod() {
		return ownerMethod;
	}
	
	/**
	 * Gets the method's name.
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Gets the method's descriptor.
	 * @return descriptor
	 */
	public String getDesc() {
		return desc;
	}
	
	/**
	 * Gets the INVOKEDYNAMIC-instruction
	 * @return instruction
	 */
	public InvokeDynamicInsnNode getInstruction() {
		return instruction;
	}

	/**
	 * Gets the bsm-tag, e.g. INVOKESPECIAL or INVOKESTATIC
	 * @return bsm-tag
	 */
	public int getBsmTag() {
		return bsmTag;
	}
	
	/**
	 * Gets tha lambda-owner-class.
	 * @return class
	 */
	public String getLambdaOwner() {
		return lambdaOwner;
	}
	
	/**
	 * Gets tha lambda-owner-method.
	 * @return method-name
	 */
	public String getLambdaName() {
		return lambdaName;
	}
	
	/**
	 * Gets the lambda-method's description.
	 * @return method-descriptor
	 */
	public String getLambdaDesc() {
		return lambdaDesc;
	}
	
	/**
	 * Gets the arguments of the INVOKEDYNAMIC-function
	 * @return arguments
	 */
	public Object[] getArguments() {
		return arguments;
	}
}