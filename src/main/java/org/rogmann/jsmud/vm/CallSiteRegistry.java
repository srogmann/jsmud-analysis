package org.rogmann.jsmud.vm;

import java.lang.reflect.Executable;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Generates a proxy-based CallSite-instance when processing a INVOKEDYNAMIC-instruction.
 * 
 * @see CallSiteGenerator
 */
public class CallSiteRegistry {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(CallSiteRegistry.class);
	
	/** map from INVOKEDYNAMIC-instruction to call-site-simulation */
	private final ConcurrentMap<InvokedynamicKey, CallSiteSimulation> mapKeys = new ConcurrentHashMap<>();
	
	/** map from object (e.g. some proxy) to call-site-simulation */
	private final ConcurrentMap<Object, CallSiteSimulation> mapProxies = new ConcurrentHashMap<>();
	
	/** number of get-errors */
	private final AtomicInteger numGetErrors = new AtomicInteger();

	/** class-loader */
	private final ClassLoader classLoader;
	
	/**
	 * Constructor
	 * @param cl class-loader for loading interfaces
	 */
	public CallSiteRegistry(final ClassLoader cl) {
		this.classLoader = cl;
	}
	
	/**
	 * Key of a INVOKEDYNAMIC-instruction in all classes.
	 */
	static class InvokedynamicKey {
		/** owner-class */
		private final Class<?> clazz;
		/** owner-method */
		private final Executable method;
		/** INVOKEDYNAMIC-instruction */
		private final InvokeDynamicInsnNode instruction;

		/**
		 * Constructor
		 * @param clazz owner-class
		 * @param method owner-method or owner-constructor
		 * @param instruction INVOKEDYNAMIC-instruction
		 */
		public InvokedynamicKey(Class<?> clazz, Executable method, InvokeDynamicInsnNode instruction) {
			this.clazz = clazz;
			this.method = method;
			this.instruction = instruction;
		}

		/**
		 * Gets the owner-class.
		 * @return owner-class
		 */
		public Class<?> getClazz() {
			return clazz;
		}

		/**
		 * Gets the owner-method
		 * @return method
		 */
		public Executable getMethod() {
			return method;
		}

		/**
		 * Gets the corresponding INVOKEDYNAMIC-instruction.
		 * @return instruction
		 */
		public InvokeDynamicInsnNode getInstruction() {
			return instruction;
		}

		/** {@inheritDoc} */
		@Override
		public int hashCode() {
			return Objects.hash(clazz, method, instruction);
		}

		/** {@inheritDoc} */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final InvokedynamicKey other = (InvokedynamicKey) obj;
			return Objects.equals(clazz, other.clazz)
					&& Objects.equals(method, other.method)
					&& Objects.equals(instruction, other.instruction);
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder(100);
			sb.append(super.toString());
			sb.append('{');
			sb.append("class:").append(clazz);
			sb.append(", method:").append(method);
			sb.append(", instr:").append(instruction);
			sb.append('}');
			return sb.toString();
		}
	}

	/**
	 * Builds a call-site.
	 * @param caller instance of object containing the instruction
	 * @param clazz owner-class
	 * @param method owner-method or owner-constructor
	 * @param idi INVOKEDYNAMIC-instruction
	 * @param lambdaHandle lambda-handle
	 * @param aArguments arguments of INVOKEDYNAMIC-instruction
	 * @return call-site simulation 
	 */
	public CallSiteSimulation buildCallSite(final Object caller, final Class<?> clazz,
			final Executable method, final InvokeDynamicInsnNode idi,
			final Handle lambdaHandle, final Object[] aArguments) {
		final InvokedynamicKey key = new InvokedynamicKey(clazz, method, idi);
		final CallSiteSimulation callSite;
		if (aArguments != null && aArguments.length > 0) {
			// We don't cache the call-site, it contains dynamic arguments.
			callSite = buildCallSite(key, caller, lambdaHandle, aArguments);
		}
		else {
			callSite = mapKeys.computeIfAbsent(key, cmi -> buildCallSite(cmi, caller,
					lambdaHandle, aArguments));
		}
		return callSite;
	}

	/**
	 * Builds and registers a call-site-instance.
	 * @param cmi instruction-key
	 * @param caller instance of object containing the instruction
	 * @param lambdaHandle lambda-handle
	 * @param aArguments arguments of INVOKEDYNAMIC-instruction
	 * @return call-site-simulation
	 */
	private CallSiteSimulation buildCallSite(final InvokedynamicKey cmi, final Object caller,
			final Handle lambdaHandle, Object[] aArguments) {
		final InvokeDynamicInsnNode instr = cmi.getInstruction();
		ClassLoader clCaller = null;
		if (caller instanceof Class) {
			clCaller = ((Class<?>) caller).getClassLoader();
		}
		else if (caller != null) {
			clCaller = caller.getClass().getClassLoader();
		}
		final ClassLoader clCallSite = (clCaller != null) ? clCaller : classLoader;
		final CallSiteSimulation callSite = new CallSiteSimulation(clCallSite, caller, cmi.getClazz(), cmi.getMethod(),
				instr, lambdaHandle, aArguments);
		final Object proxy = callSite.getProxy();

		mapProxies.put(proxy, callSite);

		return callSite;
	}

	/**
	 * Gets a call-site-simulation or <code>null</code>
	 * @param objRef known proxy-instance or unknown object
	 * @return call-site or <code>null</code>
	 */
	public CallSiteSimulation checkForCallSite(Object objRef) {
		try {
			return mapProxies.get(objRef);
		} catch (Exception e) {
			// One reason is a proxy without implementation of hashCode.
			if (numGetErrors.incrementAndGet() == 1) {
				// We log the first one.
				LOG.error(String.format("map-incompatible class (%s), some proxy?",
						objRef.getClass()), e);
			}
			return null;
		}
	}
	
}
