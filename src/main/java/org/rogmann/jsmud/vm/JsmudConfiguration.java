package org.rogmann.jsmud.vm;

import java.security.AccessController;

/**
 * Configuration-properties of jsmud-analysis.
 */
public class JsmudConfiguration {
	/** prefix of configuration-keys */
	public static final String KEY_PREFIX = "jsmud.";

	/** <code>true</code> if a call-site should be simulated via proxy, <code>false</code> if a call-site should get a generated class (<code>false</code> is default) */
	protected final boolean isCallsiteViaProxy = getProperty("CallsiteViaProxy", false);

	/** <code>true</code> if System.exit() should be replaced by an exception */
	protected final boolean isCatchSystemExit = getProperty("CatchSystemExit", true);

	/** optional folder used to dump generated class-site-classes */
	protected final String folderDumpCallSites = getProperty("FolderDumpCallSites");

	/** optional folder used to dump classes defined at runtime by the application executed*/
	protected final String folderDumpDefinedClasses = getProperty("FolderDumpDefinedClasses");

	/**
	 * <code>true</code> if the call-site generator should use the default class-loader only.
	 * A disadvantage is that INVOKEDYNAMIC-statements in some private methods or classes can't be generated. 
	 */
	protected final boolean isCallSiteDefaultClassLoaderOnly = getProperty("CallSiteDefaultClassLoaderOnly", true);

	/** <code>true</code> if defining of classes in original class-loader is forbidden */
	protected final boolean isCallSiteDontUseOrigCl = getProperty("CallSiteDontUseOrigCl", true);

	/** <code>true</code>, if {@link AccessController} should be executed by the JVM (default is <code>true</code>) */
	protected final boolean isEmulateAccessController = getProperty("EmulateAccessController", true); 

	/** <code>true</code>, if the InvocationHandler of a proxy should be field Proxy#h (default is false) */
	protected final boolean isInvocationHandlerUseField = getProperty("InvocationHandlerUseField", false); 

	/** <code>true</code>, if {@link Thread}-classes should not be patched (default is <code>true</code>) */
	protected final boolean isPatchThreadClasses = getProperty("PatchThreadClasses", true);

	/** <code>true</code>, if reflection-calls should be emulated (default is <code>true</code>) */
	protected final boolean isSimulateReflection = getProperty("SimulateReflection", true);

	/** helper class for reflection */
	private final ReflectionHelper reflectionHelper;

	/**
	 * Constructor.
	 */
	public JsmudConfiguration() {
		reflectionHelper = new ReflectionHelper();
	}

	/**
	 * Gets the value of the given boolean property.
	 * The key's prefix is "jsmud.".
	 * @param name property-suffix
	 * @param flagDefault default-flag
	 * @return flag
	 */
	@SuppressWarnings("static-method")
	protected boolean getProperty(final String name, final boolean flagDefault) {
		boolean flag = flagDefault;
		final String property = System.getProperty(KEY_PREFIX + name);
		if (property != null) {
			flag = Boolean.parseBoolean(property);
		}
		return flag;
	}

	/**
	 * Gets the value of the given string-valued property.
	 * The key's prefix is "jsmud.".
	 * @param name property-suffix
	 * @return value or <code>null</code>
	 */
	@SuppressWarnings("static-method")
	protected String getProperty(final String name) {
		return System.getProperty(KEY_PREFIX + name);
	}

	/**
	 * Gets a helper class for doing some reflection.
	 * @return helper class
	 */
	public ReflectionHelper getReflectionHelper() {
		return reflectionHelper;
	}

}
