package org.rogmann.jsmud.vm;

import java.util.Optional;

/**
 * Configuration-properties of jsmud-analysis.
 */
public class JsmudConfiguration {
	/** prefix of configuration-keys */
	public static final String KEY_PREFIX = "jsmud.";

	/** <code>true</code> if a super-constructor should be interpreted in contrast to the filter (<code>true</code> is default) */
	protected final boolean isAllowSuperInitInFilteredClasses = getProperty("AllowSuperInitInFilteredClasses", true);

	/** <code>true</code> if a call-site should be simulated via proxy, <code>false</code> if a call-site should get a generated class (<code>false</code> is default) */
	protected final boolean isCallsiteViaProxy = getProperty("CallsiteViaProxy", false);

	/** <code>true</code> if System.exit() should be replaced by an exception */
	protected final boolean isCatchSystemExit = getProperty("CatchSystemExit", true);

	/** <code>true</code> if getVariableValues should execute toString() (default is <code>false</code>) */
	protected final boolean isDebuggerDumpVariableValues = getProperty("DebuggerDumpVariableValues", false);

	/** <code>true</code> if a clinit-method may executed while processing jdwp-commands (default is <code>true</code>) */
	protected final boolean isDebuggerAllowedToExecuteClinit = getProperty("DebuggerAllowedToExecuteClinit", true);

	/** optional folder used to dump generated class-site-classes */
	protected final String folderDumpCallSites = getProperty("FolderDumpCallSites");

	/** optional folder used to dump classes defined at runtime by the application executed*/
	protected final String folderDumpDefinedClasses = getProperty("FolderDumpDefinedClasses");

	/** optional folder used to dump bytecode of classes patched by JsmudClassLoader */
	public final String folderDumpJsmudPatchedBytecode = getProperty("FolderDumpJsmudPatchedBytecode");

	/**
	 * <code>true</code> if the call-site generator should use the default class-loader only.
	 * A disadvantage is that INVOKEDYNAMIC-statements in some private methods or classes can't be generated. 
	 */
	protected final boolean isCallSiteDefaultClassLoaderOnly = getProperty("CallSiteDefaultClassLoaderOnly", true);

	/** <code>true</code> if defining of classes in original class-loader is forbidden */
	protected final boolean isCallSiteDontUseOrigCl = getProperty("CallSiteDontUseOrigCl", true);

	/** <code>true</code> if public interfaces shouldn't be duplicated into JsmudClassLoader when patching classes, default is <code>false</code> */
	protected final boolean isDontPatchPublicInterfaces = getProperty("DontPatchPublicInterfaces", false);

	/** <code>true</code>, if {@link java.security.AccessController} should be executed by the JVM (default is <code>true</code>) */
	protected final boolean isEmulateAccessController = getProperty("EmulateAccessController", true); 

	/** <code>true</code>, if the InvocationHandler of a proxy should be field Proxy#h (default is false) */
	protected final boolean isInvocationHandlerUseField = getProperty("InvocationHandlerUseField", false); 

	/** <code>true</code>, if {@link Thread}-classes should not be patched (default is <code>true</code>) */
	protected final boolean isPatchThreadClasses = getProperty("PatchThreadClasses", true);

	/** <code>true</code>, if reflection-calls should be emulated (default is <code>true</code>) */
	protected final boolean isSimulateReflection = getProperty("SimulateReflection", true);

	/** Java-version of patched classes (default is null) */
	protected final String patchedClassesVersion = getProperty("PatchedClassesVersion");

	/** helper class for reflection */
	private final ReflectionHelper reflectionHelper;

	/** class-remapper */
	private final Optional<ClassRemapper> classRemapper;

	/** method executor */
	private final NativeMethodExecutor nativeExecutor;

	/**
	 * Constructor.
	 */
	public JsmudConfiguration() {
		reflectionHelper = new ReflectionHelper();
		classRemapper = Optional.empty();
		nativeExecutor = new NativeMethodExecutorReflection();
	}

	/**
	 * Constructor.
	 * @param remapper optional class-remapper
	 * @param nativeExecutor executor to execute methods not to be executed via jsmud-analysis (default is to use reflection)
	 */
	public JsmudConfiguration(ClassRemapper remapper, NativeMethodExecutor nativeExecutor) {
		reflectionHelper = new ReflectionHelper();
		classRemapper = Optional.ofNullable(remapper);
		this.nativeExecutor = (nativeExecutor != null) ? nativeExecutor : new NativeMethodExecutorReflection();
	}

	/**
	 * Gets the value of the given boolean property.
	 * The key's prefix is "jsmud.".
	 * @param name property-suffix
	 * @param flagDefault default-flag
	 * @return flag
	 */
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

	/**
	 * Gets an optional class-remapper.
	 * @return optional remapper
	 */
	public Optional<ClassRemapper> getClassRemapper() {
		return classRemapper;
	}

	/**
	 * Gets the executor to execute native methods or methods not to be simulated by jsmud-analysis.
	 * This executor might use reflection or another simulation-engine.
	 * @return native executor
	 */
	public NativeMethodExecutor getNativeExecutor() {
		return nativeExecutor;
	}

}
