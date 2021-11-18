package org.rogmann.jsmud.vm;

import java.security.AccessController;

/**
 * Configuration-properties of jsmud-analysis.
 */
public class JsmudConfiguration {
	/** prefix of configuration-keys */
	private static final String KEY_PREFIX = "jsmud.";

	/** <code>true</code> if a call-site should be simulated via proxy, <code>false</code> if a call-site should get a generated class (<code>false</code> is default) */
	protected final boolean isCallsiteViaProxy = getProperty("CallsiteViaProxy", false);

	/** <code>true</code> if System.exit() should be replaced by an exception */
	protected final boolean isCatchSystemExit = getProperty("CatchSystemExit", true);

	/** <code>true</code>, if {@link AccessController} should be executed by the JVM (default is <code>true</code>) */
	protected final boolean isEmulateAccessController = getProperty("EmulateAccessController", true); 

	/** <code>true</code>, if {@link Thread}-classes should not be patched (default is <code>true</code>) */
	protected final boolean isPatchThreadClasses = getProperty("PatchThreadClasses", true);

	/** <code>true</code>, if reflection-calls should be emulated (default is <code>true</code>) */
	protected final boolean isSimulateReflection = getProperty("SimulateReflection", true);

	/**
	 * Gets the value of the given property.
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
}
