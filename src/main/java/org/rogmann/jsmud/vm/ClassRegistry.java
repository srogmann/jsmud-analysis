package org.rogmann.jsmud.vm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.rogmann.jsmud.datatypes.Tag;
import org.rogmann.jsmud.datatypes.VMArrayRegion;
import org.rogmann.jsmud.datatypes.VMArrayTypeID;
import org.rogmann.jsmud.datatypes.VMBoolean;
import org.rogmann.jsmud.datatypes.VMByte;
import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMClassLoaderID;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMFrameID;
import org.rogmann.jsmud.datatypes.VMInt;
import org.rogmann.jsmud.datatypes.VMInterfaceID;
import org.rogmann.jsmud.datatypes.VMLong;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMObjectID;
import org.rogmann.jsmud.datatypes.VMObjectOrExceptionID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;
import org.rogmann.jsmud.datatypes.VMShort;
import org.rogmann.jsmud.datatypes.VMStringID;
import org.rogmann.jsmud.datatypes.VMTaggedObjectId;
import org.rogmann.jsmud.datatypes.VMThreadGroupID;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.datatypes.VMVoid;
import org.rogmann.jsmud.debugger.DebuggerException;
import org.rogmann.jsmud.debugger.SlotRequest;
import org.rogmann.jsmud.debugger.SlotValue;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.replydata.LineTable;
import org.rogmann.jsmud.replydata.RefFieldBean;
import org.rogmann.jsmud.replydata.RefFrameBean;
import org.rogmann.jsmud.replydata.RefMethodBean;
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.replydata.TypeTag;
import org.rogmann.jsmud.replydata.VariableSlot;
import org.rogmann.jsmud.source.SourceFileWriter;
import org.rogmann.jsmud.source.SourceLine;

/**
 * Registry of classes whose execution should be simulated.
 * 
 * <p>This class simulates the JVM.</p>
 */
public class ClassRegistry implements VM, ObjectMonitor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ClassRegistry.class);

	/** version */
	public static String VERSION = "jsmud 0.5.1-SNAPSHOT (2021-12-30)";

	/** maximal wait-time in a monitor (this would be infinity in a read JVM) */
	private static final AtomicInteger MONITOR_MAX_MILLIS = new AtomicInteger(60000);

	/** maximum number of while waiting for a monitor-slot */ 
	private static final AtomicInteger MONITOR_MAX_TRIES = new AtomicInteger(100);

	/** map from class to executor */
	private final ThreadLocal<ConcurrentMap<Class<?>, SimpleClassExecutor>> tlMapClassExecutors = ThreadLocal.withInitial(() -> new ConcurrentHashMap<>(500));

	/** execution-filter */
	final ClassExecutionFilter executionFilter;

	/** configuration of jsmud-analysis */
	private final JsmudConfiguration configuration;

	/** class-loader (default) */
	private final ClassLoader classLoaderDefault;
	
	/** JVM-visitor-provider */
	private final JvmExecutionVisitorProvider visitorProvider;

	/** JVM-invocation-handler */
	private final JvmInvocationHandler invocationHandler;

	/** VM-suspend-counter */
	private final AtomicInteger vmSuspendCounter = new AtomicInteger();
	
	/** thread-id of the thread being debugged */
	private final ConcurrentMap<Long, VMThreadID> mapThreads = new ConcurrentHashMap<>();

	/** threadGroup-id of the thread being debugged */
	private final ConcurrentMap<Long, VMThreadGroupID> mapThreadGroups = new ConcurrentHashMap<>();	
	
	/** suspend-counter */
	private final ConcurrentMap<Long, AtomicInteger> mapThreadSuspendCounter = new ConcurrentHashMap<>();

	/** map from thread-id to execution-visitor of the thread */
	private final ConcurrentMap<Long, JvmExecutionVisitor> mapThreadVisitor = new ConcurrentHashMap<>();

	/** map of all monitor-objects */
	private final Map<Object, ThreadMonitor> mapMonitorObjects = Collections.synchronizedMap(new IdentityHashMap<>());

	/** map from thread to an monitor-object the thread is waiting for */
	private final ConcurrentMap<Thread, Object> mapContentedMonitor = new ConcurrentHashMap<>();

	/** lock used for monitor-synchronization */
	private final Lock fMonitorLock = new ReentrantLock();

	/** object-id-counter */
	private final AtomicLong objectIdCounter = new AtomicLong();
	
	/** map from object-id to weak-reference(object) */
	private final ConcurrentMap<VMObjectID, WeakReference<Object>> mapObjects = new ConcurrentHashMap<>(5000);
	
	/** map from object-id to object of gc-disabled objects */
	private final ConcurrentMap<VMObjectID, Object> mapObjectsGcDisabled = new ConcurrentHashMap<>(50);
	
	/** map of known class-loaders */
	private final ConcurrentMap<ClassLoader, VMClassLoaderID> mapClassLoader = new ConcurrentHashMap<>();

	/** map of class-loader to bytecode of classes defined at runtime */
	private final ConcurrentMap<ClassLoader, Map<String, byte[]>> mapClassLoaderDefinedClasses = new ConcurrentHashMap<>();

	/** map of loaded classes */
	private final ConcurrentMap<String, Class<?>> mapLoadedClasses = new ConcurrentHashMap<>();

	/** set of patched classes whose CLINIT-method has been called */
	private final ConcurrentMap<Class<?>, Boolean> mapClassesClinitExecuted = new ConcurrentHashMap<>();

	/** map containing ref-type-beans of class-signatures */
	private final ConcurrentMap<String, RefTypeBean> mapClassSignatures = new ConcurrentHashMap<>(100);

	/** map from source-name to source-file (containing bytecode) */
	private final ConcurrentMap<String, SourceFileWriter> mapSourceSourceFiles = new ConcurrentHashMap<>();
	/** map from loaded class to source-file (containing bytecode) */
	private final ConcurrentMap<Class<?>, SourceFileWriter> mapClassSourceFiles = new ConcurrentHashMap<>();

	/** class-based call-site-generator */
	private final CallSiteGenerator callSiteGenerator;

	/** class-based thread-child-class-generator */
	private final ThreadClassGenerator threadClassGenerator;

	/** registry of call-site-simulations used by INVOKEDYNAMIC */
	private final CallSiteRegistry callSiteRegistry;

	/** map containing ref-type-beans of classes */
	private final ConcurrentMap<Class<?>, RefTypeBean> mapClassRefType = new ConcurrentHashMap<>(100);

	/** map containing methods */
	private final ConcurrentMap<Executable, VMMethodID> mapMethods = new ConcurrentHashMap<>(100);

	/** known interfaces (key = class of interface, value = id of interface) */
	private final ConcurrentMap<Class<?>, VMInterfaceID> mapInterfaces = new ConcurrentHashMap<>(50);

	/** known ref-field-beans */
	private final ConcurrentMap<Field, RefFieldBean> mapRefFieldBean = new ConcurrentHashMap<>(1000);

	/** known ref-method-beans */
	private final ConcurrentMap<Executable, RefMethodBean> mapRefMethodBean = new ConcurrentHashMap<>(1000);
	
	/** map from method-frame to ref-frame-bean */
	private final ConcurrentMap<MethodFrame, RefFrameBean> mapRefFrameBean = new ConcurrentHashMap<>(50);
	
	/** weak map from string to string-id */
	private final Map<String, VMStringID> mapStrings = Collections.synchronizedMap(new WeakHashMap<>(50));
	
	/** weak map from object to object-id of a variable-value */
	private final Map<Object, VMObjectID> mapVariableValues = Collections.synchronizedMap(new WeakHashMap<>(100));
	
	/** map from thread to current method-stack */
	private final ConcurrentMap<Thread, Stack<MethodFrame>> mapStacks = new ConcurrentHashMap<>();

	/** <code>false</code> if there is an inaccessible field */
	private final AtomicBoolean isHasFieldInaccessible = new AtomicBoolean(true);

	/**
	 * Constructor
	 * @param executionFilter determines classes to be simulated
	 * @param configuration configuration-properties of jsmud-analysis
	 * @param classLoader class-loader to be used as default
	 * @param visitorProvider JVM-visitor-provider
	 * @param invocationHandler invocation-handler
	 */
	public ClassRegistry(final ClassExecutionFilter executionFilter, final JsmudConfiguration configuration,
			final ClassLoader classLoader,
			final JvmExecutionVisitorProvider visitorProvider, final JvmInvocationHandler invocationHandler) {
		this.executionFilter = executionFilter;
		this.configuration = configuration;
		this.classLoaderDefault = classLoader;
		this.visitorProvider = visitorProvider;
		this.invocationHandler = invocationHandler;
		callSiteRegistry = new CallSiteRegistry(classLoader);
		final JsmudClassLoader jsmudClassLoader;
		if (classLoader instanceof JsmudClassLoader) {
			jsmudClassLoader = (JsmudClassLoader) classLoader;
		}
		else {
			jsmudClassLoader = new JsmudClassLoader(classLoader, name -> false, false, false, false);
		}
		callSiteGenerator = new CallSiteGenerator(jsmudClassLoader, this, configuration);
		threadClassGenerator = new ThreadClassGenerator(jsmudClassLoader);
	}

	/**
	 * Looks for an executor.
	 * Only classes whose package-prefix is in the list of simulated packages will be simulated.
	 * @param clazz class to be simulated
	 * @return executor or <code>null</code>
	 */
	public SimpleClassExecutor getClassExecutor(final Class<?> clazz) {
		return getClassExecutor(clazz, false);
	}

	/**
	 * Looks for an executor.
	 * Only classes whose package-prefix is in the list of simulated packages will be simulated.
	 * @param clazz class to be simulated
	 * @param forceSimulation <code>true</code> if the execution should simulated regardless of the filter
	 * @return executor or <code>null</code>
	 */
	public SimpleClassExecutor getClassExecutor(final Class<?> clazz, final boolean forceSimulation) {
		final ConcurrentMap<Class<?>, SimpleClassExecutor> mapClassExecutors = tlMapClassExecutors.get();
		SimpleClassExecutor executor = mapClassExecutors.get(clazz);
		// We don't want to analyze ourself (i.e. JsmudClassLoader).
		if (executor == null && MockMethods.class.equals(clazz)) {
			executor = new SimpleClassExecutor(this, clazz, invocationHandler);
			mapClassExecutors.put(clazz, executor);
			executor.getVisitor().visitLoadClass(clazz);
		}
		else if (executor == null && !JsmudClassLoader.class.equals(clazz)) {
			boolean doSimulation = executionFilter.isClassToBeSimulated(clazz) || forceSimulation;
			if (doSimulation) {
				executor = new SimpleClassExecutor(this, clazz, invocationHandler);
				mapClassExecutors.put(clazz, executor);
				executor.getVisitor().visitLoadClass(clazz);
			}
		}
		return executor;
	}

	/**
	 * Gets the configuration.
	 * @return configuration
	 */
	public JsmudConfiguration getConfiguration() {
		return configuration;
	}

	/** {@inheritDoc} */
	@Override
	public JvmInvocationHandler getInvocationHandler() {
		return invocationHandler;
	}

	/** {@inheritDoc} */
	@Override
	public JvmExecutionVisitor getCurrentVisitor() {
		final Long threadKey = Long.valueOf(Thread.currentThread().getId());
		final JvmExecutionVisitor visitor = mapThreadVisitor.get(threadKey);
		if (visitor == null) {
			throw new JvmException(String.format("No registered execution-visitor of thread (%d/%s)",
					threadKey, Thread.currentThread().getName()));
		}
		return visitor;
	}

	/** {@inheritDoc} */
	@Override
	public ClassLoader getClassLoader() {
		return classLoaderDefault;
	}

	/** {@inheritDoc} */
	@Override
	public Class<?> loadClass(String className, final Class<?> ctxClass) throws ClassNotFoundException {
		final ClassLoader ctxClassLoader = (ctxClass != null) ? ctxClass.getClassLoader() : null;
		if (ctxClassLoader instanceof JsmudClassLoader) {
			final JsmudClassLoader jsmudCL = (JsmudClassLoader) ctxClassLoader;
			final ClassLoader clOrig = jsmudCL.getPatchedClassClassLoader(className);
			if (clOrig != null) {
				// There is an already patched class.
				final Class<?> classLoaded = jsmudCL.loadClass(className);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("loadClass: className=%s, ctxClass=%s, ctxClassLoader=%s, classLoaderOrig=%s, classLoaderClassLoaded=%s",
							className, ctxClass, ctxClassLoader, clOrig, classLoaded.getClassLoader()));
				}
				if (!classLoaded.isArray()) {
					final SimpleClassExecutor executor = getClassExecutor(classLoaded);
					if (classLoaderDefault instanceof JsmudClassLoader && executor != null) {
						final JsmudClassLoader jsmudCl = (JsmudClassLoader) classLoaderDefault;
						checkAndExecutePatchedClinit(classLoaded, executor, jsmudCl);
					}
				}
				return classLoaded;
			}
		}
		return loadClass(className, ctxClassLoader, ctxClass);
	}

	/**
	 * Loads a class.
	 * @param className qualified class-name, e.g. "java.lang.String"
	 * @param classLoader class-loader of the class to be loaded
	 * @return loaded class
	 * @throws ClassNotFoundException if the class can't be found
	 */
	public Class<?> loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
		return loadClass(className, classLoader, null);
	}

	/**
	 * Loads a class.
	 * The context-class is used to determine the class-loader to be used.
	 * @param className qualified class-name, e.g. "java.lang.String"
	 * @param ctxClass context-class, i.e. a class which knows the class to be loaded, or <code>null</code>
	 * @param classLoader class-loader of the class to be loaded or <code>null</code>
	 * @return loaded class
	 * @throws ClassNotFoundException if the class can't be found
	 */
	private Class<?> loadClass(String className, ClassLoader ctxClassLoader, final Class<?> ctxClass) throws ClassNotFoundException {
		// This implementation discards the used class-loader.
		// The handling of classes with the same name in different class-loaders is not correct.
		Class<?> clazz = mapLoadedClasses.get(className);
		if (clazz == null) {
			final ClassLoader classLoaderClass;
			if (ctxClassLoader instanceof JsmudClassLoader && ctxClass != null) {
				final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) ctxClassLoader;
				final ClassLoader classLoaderOrig = jsmudClassLoader.getPatchedClassClassLoader(ctxClass.getName());
				classLoaderClass = (classLoaderOrig != null) ? classLoaderOrig : classLoaderDefault;
			}
			else if (ctxClassLoader != null) {
				classLoaderClass = ctxClassLoader;
			}
			else {
				classLoaderClass = classLoaderDefault;
			}
			if (LOG.isDebugEnabled()) {
				if (ctxClass != null) {
					LOG.debug(String.format("load class (%s) in context (%s) via (%s)", className, ctxClass, classLoaderClass));
				}
				else {
					LOG.debug(String.format("load class (%s) via (%s)", className, classLoaderClass));
				}
			}
			if (className.charAt(0) == '[') {
				final Type type = Type.getObjectType(className);
				final int dims = type.getDimensions();
				final int[] aDims = new int[dims];
				final Class<?> elClass = MethodFrame.getClassArrayViaType(type, this, ctxClass);
				final Object oArray = Array.newInstance(elClass, aDims);
				clazz = oArray.getClass();
			}
			else if (classLoaderDefault instanceof JsmudClassLoader) {
				final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) classLoaderDefault;
				clazz = jsmudClassLoader.findClass(className, classLoaderClass, this);
			}
			else {
				try {
					clazz = classLoaderClass.loadClass(className);
				} catch (ClassNotFoundException e) {
					throw new ClassNotFoundException(String.format("Can't load class (%s) via (%s) in context (%s)",
							className, classLoaderClass, ctxClass), e);
				}
			}
			registerClass(clazz, className);
			if (!clazz.isArray()) {
				final SimpleClassExecutor executor = getClassExecutor(clazz);
				if (classLoaderDefault instanceof JsmudClassLoader && executor != null) {
					final JsmudClassLoader jsmudCl = (JsmudClassLoader) classLoaderDefault;
					checkAndExecutePatchedClinit(clazz, executor, jsmudCl);
				}
			}
		}
		return clazz;
	}

	/**
	 * Checks if the class has an patched CLINIT-method and executes it.
	 * @param clazz class
	 * @param executor executor to be used
	 * @param jsmudCl class-loader of jsmud-analysis
	 */
	private void checkAndExecutePatchedClinit(Class<?> clazz, final SimpleClassExecutor executor,
			final JsmudClassLoader jsmudCl) {
		if (jsmudCl.isStaticInitializerPatched(clazz) &&
				!Boolean.TRUE.equals(mapClassesClinitExecuted.putIfAbsent(clazz, Boolean.TRUE))) {
			// We have to initialize the parent-class before its child-class.
			final Class<?> classSuper = clazz.getSuperclass();
			if (!Object.class.equals(classSuper) && classSuper != null) {
				final SimpleClassExecutor executorSuper = getClassExecutor(classSuper);
				if (executorSuper != null) {
					checkAndExecutePatchedClinit(classSuper, executorSuper, jsmudCl);
				}
			}
			
			String className = clazz.getName();
			final String methodName = JsmudClassLoader.InitializerAdapter.METHOD_JSMUD_CLINIT;
			final Executable pMethod;
			try {
				pMethod = clazz.getDeclaredMethod(methodName);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new JvmException(String.format("Can't execute patched static initializer (%s) in (%s)",
						methodName, className), e);
			}
			String methodDesc = "()V";
			OperandStack args = new OperandStack(0);
			try {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Execute patched static initializer of (%s) of (%s)",
							className, clazz.getClassLoader()));
				}
				executor.executeMethod(Opcodes.INVOKESTATIC, pMethod, methodDesc, args);
			} catch (final Throwable e) {
				throw new JvmException(String.format("Error while executing static initializer (%s) in (%s)",
						methodName, className), e);
			}
		}
	}

	/**
	 * Stores the bytecode of a class defined at runtime.
	 * @param classLoader class-loader of defined class
	 * @param className binary name of defined class, e.g. "net.sf.cglib.proxy.Enhancer$EnhancerKey$$KeyFactoryByCGLIB$$7fb24d72"
	 * @param buf bytecode
	 */
	public void defineClass(final ClassLoader classLoader, final String className, final byte[] buf) {
		final Map<String, byte[]> mapClassNameBytecode = mapClassLoaderDefinedClasses.computeIfAbsent(classLoader, cl -> new ConcurrentHashMap<>());
		final String folderClasses = configuration.folderDumpDefinedClasses;
		if (folderClasses != null) {
			final File fileDefinedClass = new File(folderClasses, className + ".class");
			LOG.debug(String.format("Dump defined class of (%s) into (%s)", classLoader, fileDefinedClass));
			try {
				Files.write(fileDefinedClass.toPath(), buf);
			} catch (IOException e) {
				throw new JvmException(String.format("IO-error while dumping class (%s) into file (%s)",
						className, fileDefinedClass), e);
			}
		}
		mapClassNameBytecode.put(className, buf);
	}

	/** {@inheritDoc} */
	@Override
	public byte[] getBytecodeOfDefinedClass(ClassLoader classLoader, String className) {
		ClassLoader classLoaderUsed = classLoader;
		if (classLoader == null) {
			classLoaderUsed = classLoaderDefault;
		}
		final Map<String, byte[]> mapDefinedClasses = mapClassLoaderDefinedClasses.get(classLoaderUsed);
		if (mapDefinedClasses == null) {
			// No known class had been defined in this class-loader at runtime.
			return null;
		}
		// Return bytecode or null.
		return mapDefinedClasses.get(className);
	}

	/** {@inheritDoc} */
	@Override
	public void redefineClass(final VMReferenceTypeID refType, final Class<?> classUntilNow, final byte[] aClassbytes) {
		final JsmudClassLoader classLoader = (JsmudClassLoader) classLoaderDefault;
		final Class<?> classNew = classLoader.redefineJsmudClass(classUntilNow.getName(), aClassbytes,
				classUntilNow);
		mapLoadedClasses.put(classNew.getName(), classNew);
	}

	/**
	 * Register a class.
	 * @param clazz class
	 * @param className name of class
	 * @return ref-type
	 */
	private RefTypeBean registerClass(Class<?> clazz, String className) {
		mapLoadedClasses.put(className, clazz);
		
		final TypeTag typeTag;
		final long id = objectIdCounter.incrementAndGet();
		final VMReferenceTypeID refTypeId;
		if (clazz.isInterface()) {
			typeTag = TypeTag.INTERFACE;
			refTypeId = new VMInterfaceID(id);
		}
		else if (clazz.isArray()) {
			typeTag = TypeTag.ARRAY;
			refTypeId = new VMArrayTypeID(id);
		}
		else {
			typeTag = TypeTag.CLASS;
			refTypeId = new VMClassID(id);
		}
		final String signature = Type.getDescriptor(clazz);
		final String genericSignature = ""; // TODO compute genericSignature
		final int status = RefTypeBean.STATUS_INITIALIZED | RefTypeBean.STATUS_PREPARED | RefTypeBean.STATUS_VERIFIED;
		final RefTypeBean refTypeBean = new RefTypeBean(typeTag, refTypeId, signature, genericSignature, status);
		mapClassSignatures.put(signature, refTypeBean);
		mapClassRefType.put(clazz, refTypeBean);
		mapObjects.put(refTypeId, new WeakReference<>(clazz));
		return refTypeBean;
	}

	/**
	 * Checks if the class has a JSMUD-patched default-constructor.
	 * @param classInit class to be checked
	 * @return <code>true</code> if class' default-constructor is patched
	 */
	public boolean isClassConstructorJsmudPatched(Class<?> classInit) {
		boolean isPatched = false;
		if (classLoaderDefault instanceof JsmudClassLoader) {
			final JsmudClassLoader jsmudCL = (JsmudClassLoader) classLoaderDefault;
			isPatched = jsmudCL.isDefaultConstructorPatched(classInit);
		}
		return isPatched;
	}

	/**
	 * Checks if the class has a added default-constructor.
	 * An added constructor means that the original-class doesn't had an default-constructor!
	 * @param classInit class to be checked
	 * @return <code>true</code> if class' default-constructor is added
	 */
	public boolean isClassConstructorJsmudAdded(Class<?> classInit) {
		boolean isPatched = false;
		if (classLoaderDefault instanceof JsmudClassLoader) {
			final JsmudClassLoader jsmudCL = (JsmudClassLoader) classLoaderDefault;
			isPatched = jsmudCL.isDefaultConstructorAdded(classInit);
		}
		return isPatched;
	}

	/**
	 * Gets the call-site-generator.
	 * @return call-site-generator
	 */
	public CallSiteGenerator getCallSiteGenerator() {
		return callSiteGenerator;
	}

	/**
	 * Gets the thread-class-generator.
	 * @return thread-class-generator
	 */
	public ThreadClassGenerator getThreadClassGenerator() {
		return threadClassGenerator;
	}

	/**
	 * Gets the source-file-writer of this class (if present).
	 * @param clazz class
	 * @return source-file-writer or <code>null</code>
	 */
	public SourceFileWriter getSourceFileWriter(Class<?> clazz) {
		return mapClassSourceFiles.get(clazz);
	}

	/**
	 * Gets the call-site-registry.
	 * @return registry
	 */
	public CallSiteRegistry getCallSiteRegistry() {
		return callSiteRegistry;
	}

	/** {@inheritDoc} */
	@Override
	public boolean registerThread(final Thread thread) {
		return registerThread(thread, null);
	}

	/** {@inheritDoc} */
	@Override
	public boolean registerThread(final Thread thread, final JvmExecutionVisitor parentVisitor) {
		final Long threadKey = Long.valueOf(thread.getId());
		final boolean isThreadWasNotRegistered = !mapThreads.containsKey(threadKey);
		if (isThreadWasNotRegistered) {
			final VMThreadID vmThreadID = new VMThreadID(objectIdCounter.incrementAndGet());
			final VMThreadGroupID vmThreadGroupID = new VMThreadGroupID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmThreadID, new WeakReference<>(thread));
			mapObjects.put(vmThreadGroupID, new WeakReference<>(thread.getThreadGroup()));

			mapThreads.put(threadKey, vmThreadID);
			mapThreadGroups.put(threadKey, vmThreadGroupID);		
			mapThreadSuspendCounter.put(threadKey, new AtomicInteger(0));
			final JvmExecutionVisitor visitor = visitorProvider.create(this, thread, parentVisitor);
			mapThreadVisitor.put(threadKey, visitor);
		}
		return isThreadWasNotRegistered;
	}

	/** {@inheritDoc} */
	@Override
	public void unregisterThread(final Thread thread) {
		try {
			final Long threadKey = Long.valueOf(thread.getId());
			final VMThreadID vmThreadID = mapThreads.remove(threadKey);
			if (vmThreadID != null) {
				mapObjects.remove(vmThreadID);
				mapThreads.remove(threadKey);
				mapThreadSuspendCounter.remove(threadKey);
			}
			final VMThreadGroupID vmThreadGroupID = mapThreadGroups.remove(threadKey);
			if (vmThreadGroupID != null) {
				mapObjects.remove(vmThreadGroupID);
			}
			final JvmExecutionVisitor visitor = mapThreadVisitor.remove(threadKey);
			visitor.close();
		}
		finally {
			if (Thread.currentThread().equals(thread)) {
				tlMapClassExecutors.remove();
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public int enterMonitor(final Object objMonitor) {
		final Thread currentThread = Thread.currentThread();
		ThreadMonitor threadMonitor;
		int tryCounter = 0;
		mapContentedMonitor.put(currentThread, objMonitor);
		try {
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("enterMonitor: thread=%s, objMonitor=%s", currentThread, objMonitor));
			}
			do {
				fMonitorLock.lock();
				try {
					threadMonitor = mapMonitorObjects.computeIfAbsent(objMonitor, o -> new ThreadMonitor(this, o, currentThread));
					threadMonitor.addContendingThread(currentThread);
				}
				finally {
					fMonitorLock.unlock();
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("enterMonitor: thread=%s, objMonitor=%s, threadMonitor=%s",
							currentThread, objMonitor, threadMonitor));
				}
				if (threadMonitor.gainOwnership(currentThread)) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("enterMonitor: Thread (%s) gained ownership on (%s)",
								currentThread, objMonitor));
					}
					threadMonitor.removeContendingThread(currentThread);
					return 1;
				}
				if (threadMonitor.getThread() != currentThread) {
					// A JVM would wait forever, we want a timeout instead.
					final int maxMillis = MONITOR_MAX_MILLIS.get();
					boolean isFinished;
					try {
						isFinished = threadMonitor.await(maxMillis, TimeUnit.MILLISECONDS);
					}
					catch (InterruptedException e) {
						currentThread.interrupt();
						throw new IllegalStateException(String.format("Waiting for monitor-object (%s) in thread (%s) has been interrupted.",
								objMonitor, currentThread.getName()));
					}
					if (!isFinished) {
						throw new IllegalStateException(String.format("monitor-object (%s) in thread (%s) hasn't been released within %d milliseconds.",
								objMonitor, currentThread.getName(), Integer.valueOf(maxMillis)));
					}
				}
				tryCounter++;
				final int maxTries = MONITOR_MAX_TRIES.get();
				if (tryCounter >= maxTries) {
					throw new IllegalStateException(String.format("Couldn't get monitor-object (%s) in thread (%s) although %d tries had been made.",
							objMonitor, currentThread.getName(), Integer.valueOf(maxTries)));
				}
			}
			while (threadMonitor.getThread() != currentThread);
			threadMonitor.removeContendingThread(currentThread);
		}
		finally {
			mapContentedMonitor.remove(currentThread);

		}
		final int entryCount = threadMonitor.incrementCounter();
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("enterMonitor: Thread (%s) has entry-count %d on (%s)",
					currentThread, Integer.valueOf(entryCount), objMonitor));
		}
		return entryCount;
	}

	/**
	 * Exits a monitor.
	 * @param objMonitor monitor-object
	 * @return current monitor-counter
	 */
	@Override
	public int exitMonitor(final Object objMonitor) {
		final Thread currentThread = Thread.currentThread();
		final ThreadMonitor threadMonitor = mapMonitorObjects.get(objMonitor);
		if (threadMonitor == null) {
			throw new IllegalStateException(String.format("Can't exit monitor because of unregistered object (%s) of hash (0x%x) and type (%s) in thread (%s)",
					objMonitor, Integer.valueOf(System.identityHashCode(objMonitor)), (objMonitor != null) ? objMonitor.getClass().getName() : null, currentThread));
		}
		final Thread monitorThread = threadMonitor.getThread();
		if (monitorThread != currentThread) {
			throw new IllegalStateException(String.format("Can't exit monitor because monitor-object (%s) belongs to thread (%s) instead of current thread (%s)",
					objMonitor, monitorThread, currentThread));
		}
		final int counter = threadMonitor.decrementCounter();
		fMonitorLock.lock();
		try {
			if (counter == 0 && !threadMonitor.hasWaitingThreads()) {
				// release the monitor.
				mapMonitorObjects.remove(objMonitor);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("exitMonitor: monitorObject=%s, released", objMonitor));
				}
			}
			else if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("exitMonitor: monitorObject=%s, counter=%d, waiting=%s, contending=%s",
						objMonitor, Integer.valueOf(counter),
						threadMonitor.peekWaitingThread(), threadMonitor.peekContentingThread()));
			}
			if (counter == 0) {
				threadMonitor.releaseMonitor();
			}
			return counter;
		}
		finally {
			fMonitorLock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public VMTaggedObjectId getCurrentContentedMonitor(final Thread thread) {
		final Object objMonitor = mapContentedMonitor.get(thread);
		final VMObjectID vmObjectId = getVMObjectId(objMonitor);
		final VMTaggedObjectId taggedId = new VMTaggedObjectId(vmObjectId);
		return taggedId;
	}

	/** {@inheritDoc} */
	@Override
	public List<VMTaggedObjectId> getOwnedMonitors(Thread thread) {
		final List<VMTaggedObjectId> listMonObjs = new ArrayList<>();
		for (Entry<Object, ThreadMonitor> entry : mapMonitorObjects.entrySet()) {
			final Object objMonitor = entry.getKey();
			final ThreadMonitor threadMonitor = entry.getValue();
			if (threadMonitor.getThread() == thread) {
				final VMObjectID vmObjectId = getVMObjectId(objMonitor);
				final VMTaggedObjectId taggedId = new VMTaggedObjectId(vmObjectId);
				listMonObjs.add(taggedId);
			}
		}
		return listMonObjs;
	}

	/** {@inheritDoc} */
	@Override
	public VMStringID createString(String utf8) {
		final VMStringID stringId = mapStrings.computeIfAbsent(utf8,
				key -> {
					final VMStringID sId = new VMStringID(objectIdCounter.incrementAndGet());
					mapObjects.put(sId, new WeakReference<>(key));
					return sId;
				});
		return stringId;
	}

	/** {@inheritDoc} */
	@Override
	public List<RefTypeBean> getAllClassesWithGeneric() {
		return getAllClasses();
	}

	/** {@inheritDoc} */
	@Override
	public VMThreadID getCurrentThreadId() {
		return getThreadId(Thread.currentThread());
	}

	/** {@inheritDoc} */
	@Override
	public VMThreadGroupID getCurrentThreadGroupId(final Thread thread) {
		return getThreadGroupId(thread);
	}

	/**
	 * Gets all known classes.
	 * @return ref-type-beans
	 */
	public List<RefTypeBean> getAllClasses() {
		final Collection<RefTypeBean> refTypeBeans = mapClassSignatures.values();
		final List<RefTypeBean> list = new ArrayList<>(refTypeBeans);
		return list;
	}

	/** {@inheritDoc} */
	@Override
	public List<RefTypeBean> getClassesBySignature(String signature) {
		final List<RefTypeBean> beans = new ArrayList<>(1);
		final RefTypeBean refTypeBean = mapClassSignatures.get(signature);
		if (refTypeBean != null) {
			beans.add(refTypeBean);
		}
		return beans;
	}

	/** {@inheritDoc} */
	@Override
	public RefTypeBean getClassRefTypeBean(Class<? extends Object> objClass) {
		RefTypeBean refType = mapClassRefType.get(objClass);
		if (refType == null) {
			final String className = objClass.getName();
			refType = registerClass(objClass, className);
		}
		return refType;
	}

	/** {@inheritDoc} */
	@Override
	public VMClassLoaderID getClassLoader(Class<?> classRef) {
		final VMClassLoaderID classLoaderId;
		ClassLoader cl = classRef.getClassLoader();
		if (cl == null) {
			// Bootstrap-class-loader.
			classLoaderId = new VMClassLoaderID(0L);
		}
		else {
			classLoaderId = mapClassLoader.computeIfAbsent(cl, key -> {
				final VMClassLoaderID clId = new VMClassLoaderID(objectIdCounter.incrementAndGet());
				mapObjects.put(clId, new WeakReference<>(key));
				return clId;
			});
		}
		return classLoaderId;
	}

	/** {@inheritDoc} */
	@Override
	public VMClassID getSuperClass(VMClassID classId) {
		VMClassID superClassId = null;
		final WeakReference<Object> refClass = mapObjects.get(classId);
		final Object oClass = (refClass != null) ? refClass.get() : null;  
		if (oClass instanceof Class) {
			final Class<?> clazz = (Class<?>) oClass;
			final Class<?> superclass = clazz.getSuperclass();
			if (superclass == null) {
				superClassId = new VMClassID(0L);
			}
			else {
				Class<?> lSuperClass;
				try {
					lSuperClass = loadClass(superclass.getName(), superclass);
					final RefTypeBean refTypeBean = mapClassRefType.get(lSuperClass);
					superClassId = new VMClassID(refTypeBean.getTypeID().getValue());
				} catch (ClassNotFoundException e) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("getSuperClass: super-class (%s) of (%s) can't be found",
								superclass.getName(), classId));
					}
				}
			}
		}
		return superClassId;
	}

	/** {@inheritDoc} */
	@Override
	public void setClassStaticValues(final Class<?> clazz, final RefFieldBean[] aFields, final VMDataField[] aValues) {
		for (int i = 0; i < aFields.length; i++) {
			final RefFieldBean refFieldBean = aFields[i];
			final String jniSignature = refFieldBean.getSignature();
			Class<?> classField = clazz;
			while (classField != null) {
				try {
					final Field field = classField.getDeclaredField(refFieldBean.getName());
					final VMDataField vmValue = aValues[i];
					final Object oValue = convertVmValueIntoObject((byte) jniSignature.charAt(0), vmValue);
					field.setAccessible(true);
					try {
						field.set(classField, oValue);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						// We expect setObjectValues to be called without illegal arguments.
						throw new JvmException(String.format("Error while setting field (%s) with signature (%s) to value of type (%s)",
								field, jniSignature, (oValue != null) ? oValue.getClass() : null), e);
					}
					break;
				}
				catch (NoSuchFieldException e) {
					classField = classField.getSuperclass();
					continue;
				}
				catch (SecurityException e) {
					throw new JvmException(String.format("Field (%s) in class (%s) may not be accessed",
							refFieldBean.getName(), classField), e);
				}
				
			}
			if (classField == null) {
				throw new JvmException(String.format("Field (%s) is not in class (%s) or one of its super-classes",
						refFieldBean.getName(), clazz));
			}
		}

	}

	/** {@inheritDoc} */
	@Override
	public LineTable getLineTable(final Class<?> clazz, final Executable executable, final VMReferenceTypeID refType,
			VMMethodID methodID) {
		final List<LineCodeIndex> listLci = new ArrayList<>();
		final SimpleClassExecutor classExecutor = getClassExecutor(clazz);
		long start = -1;
		long end = -1;
		if (classExecutor != null) {
			final MethodNode methodNode;
			if (executable instanceof Method) {
				final Method method = (Method) executable;
				methodNode = classExecutor.loopkupMethod(executable.getName(), Type.getMethodDescriptor(method));
			}
			else if (executable instanceof Constructor) {
				final Constructor<?> constructor = (Constructor<?>) executable;
				methodNode = classExecutor.loopkupMethod("<init>", Type.getConstructorDescriptor(constructor));
			}
			else {
				throw new JvmException(String.format("Unexpected executable-type (%s) in class (%s)", executable, clazz));
			}
			if (methodNode != null) {
				final SourceFileWriter sourceFileWriter = mapClassSourceFiles.get(clazz);
				if (sourceFileWriter != null) {
					final LineTable lineTable = sourceFileWriter.computeMethodLines(classExecutor.getClassNode(), methodNode);
					final List<LineCodeIndex> listLciComputed = lineTable.getListLci();
					if (LOG.isDebugEnabled()) {
						LOG.debug(String.format("getLineTable: executable=%s, #computedLines=%d",
								executable, Integer.valueOf(listLciComputed.size())));
					}
					listLci.addAll(listLciComputed);
					start = lineTable.getStart();
					end = lineTable.getEnd();
				}
				else {
					final InsnList instructions = methodNode.instructions;
					final int numInstr = instructions.size();
					int currLine = 0;
					for (int i = 0; i < numInstr; i++) {
						final AbstractInsnNode instr = instructions.get(i);
						if (instr instanceof LineNumberNode) {
							final LineNumberNode ln = (LineNumberNode) instr;
							currLine = ln.line;
							final int index = instructions.indexOf(instr);
							listLci.add(new LineCodeIndex(index, currLine));
						}
					}
					start = 0;
					end = instructions.size() - 1;
				}
			}
		}
		return new LineTable(start, end, listLci);
	}

	/** {@inheritDoc} */
	@Override
	public List<VMInterfaceID> getClassInterfaces(Class<?> classRef) {
		final List<VMInterfaceID> listInterfaces = new ArrayList<>();
		final Class<?>[] aInterfaces = classRef.getInterfaces();
		for (final Class<?> classInterface : aInterfaces) {
			VMInterfaceID interfaceId = mapInterfaces.get(classInterface);
			if (interfaceId == null) {
				interfaceId = new VMInterfaceID(objectIdCounter.incrementAndGet());
				mapInterfaces.put(classInterface, interfaceId);
				mapObjects.put(interfaceId, new WeakReference<>(classInterface));
			}
			listInterfaces.add(interfaceId);
		}
		return listInterfaces;
	}

	/** {@inheritDoc} */
	@Override
	public List<RefFieldBean> getFieldsWithGeneric(Class<?> classRef) {
		final Field[] fields = classRef.getDeclaredFields();
		final List<RefFieldBean> list = new ArrayList<>(fields.length);
		for (final Field field : fields) {
			RefFieldBean fieldRefBean = mapRefFieldBean.get(field);
			if (fieldRefBean == null) {
				if (!Modifier.isPublic(field.getModifiers())) {
					// Is the field accessible?
					try {
						field.setAccessible(true);
					} catch (RuntimeException e) {
						// e.g. java.lang.reflect.InaccessibleObjectException in Java 9ff.
						if (isHasFieldInaccessible.getAndSet(false)) {
							LOG.error(String.format("Can't access field (%s), field is ignored in list of fields",
									field));
						}
						continue;
					}
				}
				final VMFieldID fieldId = new VMFieldID(objectIdCounter.incrementAndGet());
				final String signature = Type.getDescriptor(field.getType());
				final String genericSignature = ""; // TODO genericSignature
				fieldRefBean = new RefFieldBean(fieldId, field.getName(), signature, genericSignature, field.getModifiers());
				mapRefFieldBean.put(field, fieldRefBean);
				mapObjects.put(fieldId, new WeakReference<>(field));
			}
			list.add(fieldRefBean);
		}
		return list;
	}

	/** {@inheritDoc} */
	@Override
	public RefFieldBean getRefFieldBean(VMFieldID fieldID) {
		RefFieldBean refFieldBean = null;
		final WeakReference<Object> refField = mapObjects.get(fieldID);
		final Object oField = (refField != null) ? refField.get() : null;
		if (oField instanceof Field) {
			final Field field = (Field) oField;
			refFieldBean = mapRefFieldBean.get(field);
		}
		return refFieldBean;
	}

	/** {@inheritDoc} */
	@Override
	public VMMethodID getMethodId(Executable method) {
		final RefMethodBean bean = getMethodRefBean(method);
		return bean.getMethodID();
	}

	/**
	 * Gets a method-ref-bean of a method.
	 * @param method method
	 * @return method-ref-bean
	 */
	private RefMethodBean getMethodRefBean(Executable method) {
		RefMethodBean bean = mapRefMethodBean.computeIfAbsent(method, key -> {
			final VMMethodID methodId = new VMMethodID(objectIdCounter.incrementAndGet());
			final String name;
			final String signature;
			if (method instanceof Method) {
				name = method.getName();
				signature = Type.getMethodDescriptor((Method) method);
			}
			else if (method instanceof Constructor<?>) {
				name = "<init>";
				signature = Type.getConstructorDescriptor((Constructor<?>) method);
			}
			else {
				throw new JvmException("Unexpected executable " + method);
			}
			final String genericSignature = ""; // TODO genericSignature
			final RefMethodBean methodRefBean = new RefMethodBean(methodId, name, signature, genericSignature, method.getModifiers());
			mapObjects.put(methodId, new WeakReference<>(method));
			mapMethods.put(method, methodId);
			return methodRefBean;
		});
		return bean;
	}

	/** {@inheritDoc} */
	@Override
	public List<RefMethodBean> getMethodsWithGeneric(final Class<?> clazz) {
		final List<RefMethodBean> list = new ArrayList<>();
		// Constructors
		final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		for (Constructor<?> constructor : constructors) {
			RefMethodBean methodRefBean = getMethodRefBean(constructor);
			list.add(methodRefBean);
		}
		// Methods
		final Method[] methods = clazz.getDeclaredMethods();
		for (final Method method : methods) {
			RefMethodBean methodRefBean = getMethodRefBean(method);
			list.add(methodRefBean);
		}
		return list;
	}

	/** {@inheritDoc} */
	@Override
	public void disableCollection(VMObjectID cObjectId, Object vmObject) {
		mapObjectsGcDisabled.put(cObjectId, vmObject);
	}

	/** {@inheritDoc} */
	@Override
	public void enableCollection(VMObjectID cObjectId) {
		mapObjectsGcDisabled.remove(cObjectId);
	}

	/** {@inheritDoc} */
	@Override
	public List<VMValue> readObjectFieldValues(final Object vmObject, final List<VMFieldID> listFields) {
		final List<VMValue> values = new ArrayList<>(listFields.size());
		for (int i = 0; i < listFields.size(); i++) {
			final WeakReference<Object> refField = mapObjects.get(listFields.get(i));
			final Object oField = (refField != null) ? refField.get() : null;
			if (!(oField instanceof Field)) {
				break;
			}
			final Field field = (Field) oField;
			field.setAccessible(true);
			final Object oValue;
			try {
				oValue = field.get(vmObject);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				LOG.info(String.format("Unaccessible field (%s) in (%s): %s",
						field.getName(), vmObject.getClass(), e.getMessage()));
				break;
			}
			final Class<?> typeValue = field.getType();
			final VMValue vmValue = getVMValue(typeValue, oValue);
			values.add(vmValue);
		}
		return values;
	}

	/** {@inheritDoc} */
	@Override
	public void setObjectValues(final Object vmObject, final List<RefFieldBean> listFields, final List<VMDataField> listValues) {
		for (int i = 0; i < listFields.size(); i++) {
			final RefFieldBean refFieldBean = listFields.get(i);
			final WeakReference<Object> refField = mapObjects.get(refFieldBean.getFieldID());
			final Field field = (Field) ((refField != null) ? refField.get() : null);
			if (field == null) {
				throw new JvmException(String.format("Field (%s / %s) has been collected",
						refFieldBean.getFieldID(), refFieldBean.getName()));
			}
			final VMDataField vmValue = listValues.get(i);
			final String jniSignature = refFieldBean.getSignature();
			final Object oValue = convertVmValueIntoObject((byte) jniSignature.charAt(0), vmValue);
			field.setAccessible(true);
			try {
				field.set(vmObject, oValue);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// We expect setObjectValues to be called without illegal arguments.
				throw new JvmException(String.format("Error while setting field (%s) with signature (%s) to value of type (%s) in class (%s)",
						field, jniSignature, (oValue != null) ? oValue.getClass() : null,
						(vmObject != null) ? vmObject.getClass() : null), e);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void setArrayValues(final Object objArray, final Tag tag, final int firstIndex, final VMDataField[] values) {
		for (int i = 0; i < values.length; i++) {
			final int destIndex = firstIndex + i;
			if (tag == Tag.BOOLEAN) {
				final VMBoolean vmBoolean = (VMBoolean) values[i];
				Array.setBoolean(objArray, destIndex, vmBoolean.getValue() != (byte) 0);
			}
			else if (tag == Tag.BYTE) {
				final VMByte vmByte = (VMByte) values[i];
				Array.setByte(objArray, destIndex, vmByte.getValue());
			}
			else if (tag == Tag.CHAR) {
				final VMShort vmShort = (VMShort) values[i];
				Array.setChar(objArray, destIndex, (char) vmShort.getValue());
			}
			else if (tag == Tag.SHORT) {
				final VMShort vmShort = (VMShort) values[i];
				Array.setShort(objArray, destIndex, vmShort.getValue());
			}
			else if (tag == Tag.INT) {
				final VMInt vmInt = (VMInt) values[i];
				Array.setInt(objArray, destIndex, vmInt.getValue());
			}
			else if (tag == Tag.LONG) {
				final VMLong vmLong = (VMLong) values[i];
				Array.setLong(objArray, destIndex, vmLong.getValue());
			}
			else if (tag == Tag.FLOAT) {
				final VMInt vmInt = (VMInt) values[i];
				Array.setFloat(objArray, destIndex, Float.intBitsToFloat(vmInt.getValue()));
			}
			else if (tag == Tag.DOUBLE) {
				final VMLong vmLong = (VMLong) values[i];
				Array.setDouble(objArray, destIndex, Double.longBitsToDouble(vmLong.getValue()));
			}
			else if (tag == Tag.STRING || tag == Tag.ARRAY || tag == Tag.OBJECT) {
				final VMObjectID vmObjId = (VMObjectID) values[i];
				final WeakReference<Object> refVal = mapObjects.get(vmObjId);
				final Object value = (refVal != null) ? refVal.get() : null;
				Array.set(objArray, destIndex, value);
			}
			else {
				throw new IllegalArgumentException(String.format("Unexpected tag (%s)", tag));
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public Tag getVMTag(final Class<?> typeValue) {
		final Tag tag;
		if (boolean.class.equals(typeValue)) {
			tag = Tag.BOOLEAN;
		}
		else if (byte.class.equals(typeValue)) {
			tag = Tag.BYTE;
		}
		else if (char.class.equals(typeValue)) {
			tag = Tag.CHAR;
		}
		else if (short.class.equals(typeValue)) {
			tag = Tag.SHORT;
		}
		else if (int.class.equals(typeValue)) {
			tag = Tag.INT;
		}
		else if (long.class.equals(typeValue)) {
			tag = Tag.LONG;
		}
		else if (float.class.equals(typeValue)) {
			tag = Tag.FLOAT;
		}
		else if (double.class.equals(typeValue)) {
			tag = Tag.DOUBLE;
		}
		else if (String.class.equals(typeValue)) {
			tag = Tag.STRING;
		}
		else if (typeValue.isArray()) {
			tag = Tag.ARRAY;
		}
		else {
			tag = Tag.OBJECT;
		}
		return tag;
	}

	/** {@inheritDoc} */
	@Override
	public VMValue getVMValue(final Class<?> typeValue, final Object oValue) {
		final VMValue vmValue;
		final VMDataField dfValue;
		final Tag tag;
		if (boolean.class.equals(typeValue)) {
			tag = Tag.BOOLEAN;
			dfValue = new VMBoolean(((Boolean) oValue).booleanValue());
		}
		else if (byte.class.equals(typeValue)) {
			tag = Tag.BYTE;
			dfValue = new VMByte(((Byte) oValue).byteValue());
		}
		else if (char.class.equals(typeValue)) {
			tag = Tag.CHAR;
			dfValue = new VMShort((short) ((Character) oValue).charValue());
		}
		else if (short.class.equals(typeValue)) {
			tag = Tag.SHORT;
			dfValue = new VMShort(((Short) oValue).shortValue());
		}
		else if (int.class.equals(typeValue)) {
			tag = Tag.INT;
			dfValue = new VMInt(((Integer) oValue).intValue());
		}
		else if (long.class.equals(typeValue)) {
			tag = Tag.LONG;
			dfValue = new VMLong(((Long) oValue).longValue());
		}
		else if (float.class.equals(typeValue)) {
			tag = Tag.FLOAT;
			final float fValue = ((Float) oValue).floatValue();
			dfValue = new VMInt(Float.floatToRawIntBits(fValue));
		}
		else if (double.class.equals(typeValue)) {
			tag = Tag.DOUBLE;
			final double dValue = ((Double) oValue).doubleValue();
			dfValue = new VMLong(Double.doubleToRawLongBits(dValue));
		}
		else if (String.class.equals(typeValue)) {
			tag = Tag.STRING;
			dfValue = getVMObjectId(oValue);
		}
		else if (typeValue.isArray()) {
			tag = Tag.ARRAY;
			dfValue = getVMObjectId(oValue);
		}
		else {
			tag = Tag.OBJECT;
			dfValue = getVMObjectId(oValue);
		}
		vmValue = new VMValue(tag.getTag(), dfValue);
		return vmValue;
	}

	/** {@inheritDoc} */
	@Override
	public List<VariableSlot> getVariableSlots(Method pMethod) {
		List<VariableSlot> slots = new ArrayList<>();
		final SimpleClassExecutor executor = getClassExecutor(pMethod.getDeclaringClass());
		if (executor != null) {
			final String methodDesc = Type.getMethodDescriptor(pMethod);
			final MethodNode methodNode = executor.loopkupMethod(pMethod.getName(), methodDesc);
			final List<LocalVariableNode> localVariables = methodNode.localVariables;
			if (localVariables != null && localVariables.size() > 0) {
				for (final LocalVariableNode varNode : localVariables) {
					final long startIndex = methodNode.instructions.indexOf(varNode.start);
					final long endIndex = methodNode.instructions.indexOf(varNode.end);
					final String genericSignature = ""; // TODO genericSignature
					final int length = (int) (endIndex - startIndex);
					String signature = varNode.signature;
					if (signature == null) {
						signature = varNode.desc;
					}
					final VariableSlot slot = new VariableSlot(startIndex, varNode.name, signature,
							genericSignature, length, varNode.index); 
					slots.add(slot);
				}
			}
			else {
				// No debug-information.
				final int maxLocals = methodNode.maxLocals;
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("getVariableSlots: no debug-information, maxLocals=%d", Integer.valueOf(maxLocals)));
				}
				for (int i = 0; i < maxLocals; i++) {
					final long startIndex = 0;
					final long endIndex = methodNode.instructions.size();
					final String genericSignature = "";
					final int length = (int) (endIndex - startIndex);
					final String signature = "Ljava/lang/Object;"; // We don't know the type.
					final String name = "local" + i;
					final VariableSlot slot = new VariableSlot(startIndex, name, signature,
							genericSignature, length, i); 
					slots.add(slot);
				}
			}
		}
		return slots;
	}

	/** {@inheritDoc} */
	@Override
	public List<VMValue> getVariableValues(MethodFrame methodFrame, List<SlotRequest> slotRequests) {
		final int slots = slotRequests.size();
		final Object[] aLocals = methodFrame.getLocals();
		final List<VMValue> aValues = new ArrayList<>(slots);
		for (int i = 0; i < slots; i++) {
			final SlotRequest slotRequest = slotRequests.get(i);
			final byte tag = slotRequest.getTag();
			final Tag eTag = Tag.lookupByTag(tag);
			final int slot = slotRequest.getSlot();
			if (slot < 0 || slot >= aLocals.length) {
				// A wrong slot.
				LOG.error(String.format("getVariableValues: mf=%s, i=%d, slot=%d, aLocals.length=%d, aLocals=%s",
						methodFrame, Integer.valueOf(i), Integer.valueOf(slot),
						Integer.valueOf(aLocals.length), Arrays.toString(aLocals)));
				break;
			}
			Object valueJvm = aLocals[slot];
			final Object value = MethodFrame.convertJvmTypeIntoFieldType(eTag.getClassTag(), valueJvm);
			final Tag tagReply;
			if (LOG.isDebugEnabled()) {
				String sValue = null;
				if (value != null) {
					try {
						sValue = value.toString();
					} catch (Exception e) {
						// e.g. proxy which doesn't implement toString().
						sValue = String.format("instance of (%s) without toString: %s",
								value.getClass(), e);
					}
				}
				LOG.debug(String.format("getVariableValues: method=%s, slot=%d, value=%s",
					methodFrame.getMethod().getName(), Integer.valueOf(slot),
					sValue));
			}
			if (value instanceof String) {
				tagReply = Tag.STRING;
			}
			else {
				tagReply = eTag;
			}
			final VMDataField dfValue = getVmValue(tagReply, value);
			aValues.add(new VMValue(tagReply.getTag(), dfValue));
		}
		return aValues;
	}

	/** {@inheritDoc} */
	@Override
	public VMArrayRegion readArrayValues(Object objArray, int firstIndex, int length) {
		final VMDataField[] values = new VMDataField[length];
		final Tag tag;
		if (objArray instanceof byte[]) {
			tag = Tag.BYTE;
			for (int i = 0; i < length; i++) {
				final byte bValue = Array.getByte(objArray, firstIndex + i);
				values[i] = new VMByte(bValue);
			}
		}
		else if (objArray instanceof char[]) {
			tag = Tag.CHAR;
			for (int i = 0; i < length; i++) {
				final char c = Array.getChar(objArray, firstIndex + i);
				values[i] = new VMShort((short) c);
			}
		}
		else if (objArray instanceof int[]) {
			tag = Tag.INT;
			for (int i = 0; i < length; i++) {
				final int iValue = Array.getInt(objArray, firstIndex + i);
				values[i] = new VMInt(iValue);
			}
		}
		else if (objArray instanceof boolean[]) {
			tag = Tag.BOOLEAN;
			for (int i = 0; i < length; i++) {
				final boolean bValue = Array.getBoolean(objArray, firstIndex + i);
				values[i] = new VMBoolean(bValue);
			}
		}
		else if (objArray instanceof short[]) {
			tag = Tag.SHORT;
			for (int i = 0; i < length; i++) {
				final short sValue = Array.getShort(objArray, firstIndex + i);
				values[i] = new VMShort(sValue);
			}
		}
		else if (objArray instanceof long[]) {
			tag = Tag.LONG;
			for (int i = 0; i < length; i++) {
				final long lValue = Array.getLong(objArray, firstIndex + i);
				values[i] = new VMLong(lValue);
			}
		}
		else if (objArray instanceof float[]) {
			tag = Tag.FLOAT;
			for (int i = 0; i < length; i++) {
				final float fValue = Array.getFloat(objArray, firstIndex + i);
				values[i] = new VMInt(Float.floatToRawIntBits(fValue));
			}
		}
		else if (objArray instanceof double[]) {
			tag = Tag.DOUBLE;
			for (int i = 0; i < length; i++) {
				final double dValue = Array.getDouble(objArray, firstIndex + i);
				values[i] = new VMLong(Double.doubleToRawLongBits(dValue));
			}
		}
		else {
			tag = Tag.OBJECT;
			for (int i = 0; i < length; i++) {
				final Object oValue = Array.get(objArray, firstIndex + i);
				values[i] = getVMObjectId(oValue);
			}
		}
		final VMArrayRegion arrayregion = new VMArrayRegion (tag, values);
		return arrayregion;
	}

	/**
	 * Gets the VM-value of a value.
	 * @param tag tag of the value
	 * @param value value
	 * @return VM-value
	 */
	private VMDataField getVmValue(final Tag tag, final Object value) {
		final VMDataField dfValue;
		switch (tag) {
		case BYTE:
			dfValue = new VMByte(((Byte) value).byteValue());
			break;
		case BOOLEAN:
			dfValue = new VMBoolean(((Boolean) value).booleanValue());
			break;
		case CHAR:
			dfValue = new VMShort((short) ((Character) value).charValue());
			break;
		case SHORT:
			dfValue = new VMShort(((Short) value).shortValue());
			break;
		case INT:
			dfValue = new VMInt(((Integer) value).intValue());
			break;
		case LONG:
			dfValue = new VMLong(((Long) value).longValue());
			break;
		case FLOAT:
			final int iFloat = Float.floatToRawIntBits(((Float) value).floatValue());
			dfValue = new VMInt(iFloat);
			break;
		case DOUBLE:
			final long lDouble = Double.doubleToRawLongBits(((Double) value).doubleValue());
			dfValue = new VMLong(lDouble);
			break;
		case VOID:
			dfValue = new VMVoid();
			break;
		default:
			final VMObjectID vmObjectID = getVMObjectId(value);
			dfValue = vmObjectID;
			break;
		}
		return dfValue;
	}

	/**
	 * Gets the object-id of a value.
	 * Creates a new object-id if the value is unknown.
	 * @param value value
	 * @return object-id
	 */
	private VMObjectID getVMObjectId(final Object value) {
		VMObjectID vmObjectID;
		if (value == null) {
			vmObjectID = new VMObjectID(0L);
		}
		else {
			vmObjectID = mapVariableValues.get(value);
			if (vmObjectID == null) {
				vmObjectID = new VMObjectID(objectIdCounter.incrementAndGet());
				mapObjects.put(vmObjectID, new WeakReference<>(value));
				mapVariableValues.put(value, vmObjectID);
			}
		}
		return vmObjectID;
	}

	/** {@inheritDoc} */
	@Override
	public boolean setVariableValues(final MethodFrame methodFrame, final List<SlotValue> slotVariables) {
		boolean isOk = true;
		final Object[] aLocals = methodFrame.getLocals();
		for (SlotValue slotValue : slotVariables) {
			final int slot = slotValue.getSlot();
			final byte tag = slotValue.getVariable().getTag();
			final VMDataField dfValue = slotValue.getVariable().getValue();
			if (slot < 0 || slot >= aLocals.length) {
				isOk = false;
				break;
			}
			final Object oValue = convertVmValueIntoObject(tag, dfValue);
			aLocals[slot] = oValue;
		}
		return isOk;
	}

	/**
	 * Converts a tag and an untagged-value into an object.
	 * @param tag tag
	 * @param dfValue untagged-value
	 * @return object
	 */
	private Object convertVmValueIntoObject(final byte tag, final VMDataField dfValue) {
		final Object oValue;
		switch(tag) {
		case 'B':
			oValue = Byte.valueOf(((VMByte) dfValue).getValue());
			break;
		case 'Z':
			final byte bVal = ((VMByte) dfValue).getValue();
			oValue = Boolean.valueOf(bVal != (byte) 0);
			break;
		case 'C':
			final short sVal = ((VMShort) dfValue).getValue();
			oValue = Character.valueOf((char) sVal);
			break;
		case 'S':
			oValue = Short.valueOf(((VMShort) dfValue).getValue());
			break;
		case 'I':
			oValue = Integer.valueOf(((VMInt) dfValue).getValue());
			break;
		case 'J':
			oValue = Long.valueOf(((VMLong) dfValue).getValue());
			break;
		case 'F':
			final int iFloat = ((VMInt) dfValue).getValue();
			oValue = Float.valueOf(Float.intBitsToFloat(iFloat));
			break;
		case 'D':
			final long lDouble = ((VMLong) dfValue).getValue();
			oValue = Double.valueOf(Double.longBitsToDouble(lDouble));
			break;
		case 'V':
			// TODO Void.TYPE or null in case of type 'V'?
			oValue = Void.TYPE;
			break;
		default:
			VMObjectID vmObjectID = (VMObjectID) dfValue;
			final WeakReference<Object> refVal = mapObjects.get(vmObjectID);
			oValue = (refVal != null) ? refVal.get() : null;
			break;
		}
		return oValue;
	}

	/** {@inheritDoc} */
	@Override
	public VMObjectOrExceptionID createNewInstance(final Class<?> clazz, final Thread thread,
			final Constructor<?> constructor, final List<VMValue> argValues) {
		// TODO consider the thread wanted
		final Object[] aArgs = new Object[argValues.size()];
		for (int i = 0; i < argValues.size(); i++) {
			final VMValue vmValue = argValues.get(i);
			aArgs[i] = convertVmValueIntoObject(vmValue.getTag(), vmValue.getValue());
		}
		final Object newObject;
		try {
			newObject = constructor.newInstance(aArgs);
		}
		catch (InstantiationException | IllegalAccessException e) {
			// An exception occurred while trying to execute the constructor.
			final VMObjectID eId = getVMObjectId(e);
			return new VMObjectOrExceptionID(VMTaggedObjectId.NULL, new VMTaggedObjectId(eId));
		}
		catch (IllegalArgumentException e) {
			LOG.error(String.format("createNewInstance: clazz=%s, constructor=%s, args=%s",
					clazz, constructor, Arrays.toString(aArgs)), e);
			final VMObjectID eId = getVMObjectId(e);
			return new VMObjectOrExceptionID(VMTaggedObjectId.NULL, new VMTaggedObjectId(eId));
		}
		catch (InvocationTargetException e) {
			// An exception occurred while executing the constructor.
			final VMObjectID eId = getVMObjectId(e);
			return new VMObjectOrExceptionID(VMTaggedObjectId.NULL, new VMTaggedObjectId(eId));
		}
		final VMObjectID vmObjectId = getVMObjectId(newObject);
		final VMTaggedObjectId taggedObjectId = new VMTaggedObjectId(vmObjectId);
		return new VMObjectOrExceptionID(taggedObjectId, VMTaggedObjectId.NULL);
	}
	
	/** {@inheritDoc} */
	@Override
	public VMDataField[] executeMethod(final VMObjectID cObjectId, final VMClassID classId, final VMMethodID methodId,
			final List<SlotValue> values) {
		final Object oObject = getVMObject(cObjectId);
		final Method method = (Method) getVMObject(methodId);
		method.setAccessible(true);
		Object[] args = new Object[values.size()];
		final VMDataField[] dfResAndExc = new VMDataField[2];
		try {
			dfResAndExc[0] = new VMTaggedObjectId(new VMObjectID(0L));
			final Object oResult = method.invoke(oObject, args);
			Tag tag;
			if (oResult == null) {
				tag = Tag.OBJECT;
			}
			else {
				final Class<?> cResult = oResult.getClass();
				if (boolean.class.equals(cResult)) {
					tag = Tag.BOOLEAN;
				}
				else if (byte.class.equals(cResult)) {
					tag = Tag.BYTE;
				}
				else if (short.class.equals(cResult)) {
					tag = Tag.SHORT;
				}
				else if (int.class.equals(cResult)) {
					tag = Tag.INT;
				}
				else if (long.class.equals(cResult)) {
					tag = Tag.LONG;
				}
				else if (float.class.equals(cResult)) {
					tag = Tag.FLOAT;
				}
				else if (double.class.equals(cResult)) {
					tag = Tag.DOUBLE;
				}
				else if (String.class.equals(cResult)) {
					tag = Tag.STRING;
				}
				else if (void.class.equals(cResult)) {
					tag = Tag.VOID;
				}
				else {
					tag = Tag.OBJECT;
				}
			}
			final VMDataField dfValue = getVmValue(tag, oResult);
			dfResAndExc[0] = new VMValue(tag.getTag(), dfValue);
			dfResAndExc[1] = new VMTaggedObjectId(new VMObjectID(0L));
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Execute: oResult=%s, result=%s",
						oResult, dfResAndExc[0]));
			}
		}
		catch (IllegalAccessException | IllegalArgumentException e) {
			e.printStackTrace();
			final VMObjectID vmExceptionId = new VMObjectID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmExceptionId, new WeakReference<>(e));
			dfResAndExc[1] = new VMTaggedObjectId(vmExceptionId);
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
			final Throwable eCause = e.getCause();
			final VMObjectID vmExceptionId = new VMObjectID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmExceptionId, new WeakReference<>(eCause));
			dfResAndExc[1] = new VMTaggedObjectId(vmExceptionId);
		}
		return dfResAndExc;
	}

	/** {@inheritDoc} */
	@Override
	public VMThreadID getThreadId(Thread thread) {
		return mapThreads.get(Long.valueOf(thread.getId()));
	}

	/**
	 * Gets the thread-group-id of a thread.
	 * @param thread thread
	 * @return thread-group-id or <code>null</code>
	 */
	public VMThreadGroupID getThreadGroupId(Thread thread) {
		return mapThreadGroups.get(Long.valueOf(thread.getId()));
	}

	/** {@inheritDoc} */
	@Override
	public Object getVMObject(VMObjectID objectId) {
		final WeakReference<Object> refObj = mapObjects.get(objectId);
		return (refObj != null) ? refObj.get() : null;
	}
	
	/**
	 * Pushes a method-frame onto the stack.
	 * @param thread current thread
	 * @param frame method-frame
	 */
	public void pushMethodFrame(final Thread thread, final MethodFrame frame) {
		final Stack<MethodFrame> stack = mapStacks.computeIfAbsent(thread, t -> new Stack<>());
		stack.push(frame);
	}
	
	/**
	 * Removes the method-frame on top of the stack.
	 * @param thread current thread
	 */
	public void popMethodFrame(final Thread thread) {
		final Stack<MethodFrame> stack = mapStacks.get(thread);
		final MethodFrame mf = stack.pop();
		final RefFrameBean rfBean = mapRefFrameBean.remove(mf);
		if (rfBean != null) {
			final VMFrameID frameId = rfBean.getFrameId();
			mapObjects.remove(frameId);
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<RefFrameBean> getThreadFrames(VMThreadID cThreadId, int startFrame, int length) {
		final List<RefFrameBean> frames = new ArrayList<>();
		final WeakReference<Object> refThread = mapObjects.get(cThreadId);
		final Thread thread = (Thread) ((refThread != null) ? refThread.get() : null);
		final Stack<MethodFrame> stack = (thread != null) ? mapStacks.get(thread) : null;
		if (thread != null && stack != null) {
			final int stackSize = stack.size();
			final int endFrame = (length == -1) ? stackSize : Math.min(stackSize, startFrame + length);
			for (int i = startFrame; i < endFrame; i++) {
				final MethodFrame mf = stack.get(stackSize - 1 - i);
				RefFrameBean rfBean = mapRefFrameBean.get(mf);
				if (rfBean == null) {
					final Class<?> clazz = mf.getFrameClass();
					final String signature = Type.getDescriptor(clazz);
					RefTypeBean refTypeBean = mapClassSignatures.get(signature);
					if (refTypeBean == null) {
						// The signature is not known yet (example: Ljava/util/stream/ReduceOps$ReduceOp;).
						refTypeBean = registerClass(clazz, clazz.getName());
					}
					if (refTypeBean == null) {
						throw new JvmException(String.format("getThreadFrames: Unknown class (%s)", signature));
					}
					final Executable method = mf.getMethod();
					VMMethodID vmMethodID = mapMethods.get(method);
					if (vmMethodID == null) {
						// Some method we haven't analyzed yet.
						if (LOG.isDebugEnabled()) {
							LOG.debug(String.format("getThreadFrames: analyze method (%s)", method));
						}
						getMethodRefBean(method);
						vmMethodID = mapMethods.get(method);
					}
					if (vmMethodID == null) {
						if (LOG.isDebugEnabled()) {
							Comparator<Executable> classComp = (m1, m2)
									-> m1.getDeclaringClass().getName().compareTo(m2.getDeclaringClass().getName());
							final String packagePrefix = method.getDeclaringClass().getName().replaceFirst("[.][^.]*$", ".");
							mapMethods.keySet()
								.stream().filter(m -> m.getDeclaringClass().getName().startsWith(packagePrefix))
								.sorted(classComp)
								.forEach(m -> LOG.debug("registered method: " + m));
							// .sorted(Comparator.comparing(Executable::getName))
						}
						throw new JvmException(String.format("Method (%s) of (%s) isn't registered.", method, clazz));
					}
					final VMFrameID frameId = new VMFrameID(objectIdCounter.incrementAndGet());
					final long index = mf.instrNum;
					rfBean = new RefFrameBean(frameId, refTypeBean.getTypeTag(), refTypeBean.getTypeID(), vmMethodID, index);
					mapObjects.put(frameId, new WeakReference<>(mf));
					mapRefFrameBean.put(mf, rfBean);
				}
				else {
					rfBean.setIndex(mf.instrNum);
				}
				frames.add(rfBean);
			}
		}
		return frames;
	}

	/** {@inheritDoc} */
	@Override
	public VMTaggedObjectId getThisObject(MethodFrame methodFrame) {
		final Executable method = methodFrame.getMethod();
		final Object[] locals = methodFrame.getLocals();
		if (locals.length == 0) {
			throw new DebuggerException(String.format("Method (%s) without locals", method));
		}
		final Object oThis = locals[0];
		final VMObjectID vmThisId = getVMObjectId(oThis);
		final VMTaggedObjectId taggedId = new VMTaggedObjectId(vmThisId);
		return taggedId;
	}

	/** {@inheritDoc} */
	@Override
	public long[] getInstanceCounts(VMReferenceTypeID[] aRefTypes) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("getInstanceCounts: %s", Arrays.toString(aRefTypes)));
		}
		final int num = aRefTypes.length;
		long[] aInstanceCounts = new long[num];
		// The implementation is in O(n²) only.
		for (int i = 0; i < num; i++) {
			final VMReferenceTypeID refType = aRefTypes[i];
			if (refType == null) {
				continue;
			}
			final Object oRefType = getVMObject(refType);
			final Class<?> classRefType = (Class<?>) oRefType;
			long count = 0;
			for (WeakReference<Object> refObject : mapObjects.values()) {
				final Object object = refObject.get();
				if (classRefType.isInstance(object)) {
					count++;
				}
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("  class=%s, count=%d", classRefType, Long.valueOf(count)));
			}
			aInstanceCounts[i] = count;
		}
		return aInstanceCounts;
	}

	/** {@inheritDoc} */
	@Override
	public List<VMTaggedObjectId> getInstances(Class<?> classRefType, int maxInstances) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("getInstances: class=%s, maxInstances=%d",
					classRefType, Integer.valueOf(maxInstances)));
		}
		final List<VMTaggedObjectId> listInstances = new ArrayList<>();
		for (Entry<VMObjectID, WeakReference<Object>> entry : mapObjects.entrySet()) {
			final Object object = entry.getValue().get();
			if (classRefType.isInstance(object)) {
				final VMObjectID key = entry.getKey();
				final VMTaggedObjectId taggedObjectId;
				if (String.class.equals(classRefType)) {
					taggedObjectId = new VMTaggedObjectId(Tag.STRING, key);
				}
				else {
					taggedObjectId = new VMTaggedObjectId(key);
				}
				listInstances.add(taggedObjectId);
				if (maxInstances > 0 && listInstances.size() >= maxInstances) {
					break;
				}
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("  numInstances=%d", Integer.valueOf(listInstances.size())));
		}
		return listInstances;
	}

	/** {@inheritDoc} */
	@Override
	public Integer getSuspendCount(VMThreadID cThreadId) {
		final WeakReference<Object> refThread = mapObjects.get(cThreadId);
		Object oThread = (refThread != null) ? refThread.get() : null;
		Integer iSuspCounter = null;
		if (oThread instanceof Thread) {
			final Thread thread = (Thread) oThread;
			final AtomicInteger suspCounter = mapThreadSuspendCounter.get(Long.valueOf(thread.getId()));
			if (suspCounter != null) {
				//if (LOG.isDebugEnabled()) {
				//	LOG.debug(String.format("getSuspendCount: suspCounter=%d, vmSuspendCounter=%d",
				//			Integer.valueOf(suspCounter.intValue()), Integer.valueOf(vmSuspendCounter.get())));
				//}
				iSuspCounter = Integer.valueOf(suspCounter.intValue() + vmSuspendCounter.get());
			}
		}
		return iSuspCounter;
	}

	/** {@inheritDoc} */
	@Override
	public void suspend() {
		vmSuspendCounter.incrementAndGet();
	}

	/** {@inheritDoc} */
	@Override
	public boolean suspendThread(final VMThreadID cThreadId) {
		final WeakReference<Object> refThread = mapObjects.get(cThreadId);
		Object oThread = (refThread != null) ? refThread.get() : null;
		boolean isValid = false;
		if (oThread instanceof Thread) {
			final Thread thread = (Thread) oThread;
			final AtomicInteger suspCounter = mapThreadSuspendCounter.get(Long.valueOf(thread.getId()));
			if (suspCounter != null) {
				suspCounter.incrementAndGet();
				isValid = true;
			}
		}
		return isValid;
	}

	/** {@inheritDoc} */
	@Override
	public void resume() {
		vmSuspendCounter.decrementAndGet();
	}

	/** {@inheritDoc} */
	@Override
	public boolean resumeThread(VMThreadID cThreadId) {
		final WeakReference<Object> refThread = mapObjects.get(cThreadId);
		Object oThread = (refThread != null) ? refThread.get() : null;
		boolean isValid = false;
		if (oThread instanceof Thread) {
			final Thread thread = (Thread) oThread;
			final AtomicInteger suspCounter = mapThreadSuspendCounter.get(Long.valueOf(thread.getId()));
			if (suspCounter == null) {
				throw new DebuggerException(String.format("Thread (%s, %s) without internal suspend-counter",
						cThreadId, thread.getName()));
			}
			final int counter = suspCounter.updateAndGet(c -> Math.max(c - 1, 0));
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("resumeThread: thread=%s, suspCounter=%d",
						thread.getName(), Integer.valueOf(counter)));
			}
			isValid = true;
		}
		return isValid;
	}

	/** {@inheritDoc} */
	@Override
	public void interrupt(Thread thread) {
		thread.interrupt();
	}

	/**
	 * Waits for the call of notify/notifyAll.
	 * @param monitorObj monitor-object
	 * @param timeout timeout in milliseconds
	 * @param nanos timeout in nanoseconds
	 * @throws InterruptedException in case of an interruption
	 */
	void doObjectWait(Object monitorObj, long timeout, int nanos) throws InterruptedException {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("doObjectWait: monitor-object (%s)", monitorObj));
		}
		final ThreadMonitor threadMonitor = mapMonitorObjects.get(monitorObj);
		if (threadMonitor == null) {
			throw new IllegalMonitorStateException(String.format("no monitor-object (%s)",
					monitorObj));
		}
		final CountDownLatch latch = threadMonitor.addWaitThread(Thread.currentThread());
		exitMonitor(monitorObj);
		try {
			if (timeout == 0 && nanos == 0) {
				latch.await();
			}
			else if (nanos == 0) {
				latch.await(timeout, TimeUnit.MILLISECONDS);
			}
			else {
				latch.await(1000000 * timeout + nanos, TimeUnit.NANOSECONDS);
			}
		}
		finally {
			enterMonitor(monitorObj);
		}
	}

	/**
	 * Notifies one waiting thread.
	 * @param monitorObj monitor-object
	 */
	void doNotify(Object monitorObj) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("doNotify: monitor-object (%s)", monitorObj));
		}
		final ThreadMonitor threadMonitor = mapMonitorObjects.get(monitorObj);
		if (threadMonitor == null) {
			throw new IllegalMonitorStateException(String.format("no monitor-object (%s)",
					monitorObj));
		}
		threadMonitor.sendNotify();
	}

	/**
	 * Notifies all waiting threads.
	 * @param monitorObj monitor-object
	 */
	void doNotifyAll(Object monitorObj) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("doNotifyAll: monitor-object (%s)", monitorObj));
		}
		final ThreadMonitor threadMonitor = mapMonitorObjects.get(monitorObj);
		if (threadMonitor == null) {
			throw new IllegalMonitorStateException(String.format("no monitor-object (%s)",
					monitorObj));
		}
		threadMonitor.sendNotifyAll();
	}

	/**
	 * Sets the maximum wait-time for a monitor.
	 * @param maxMillis maximum time to wait in milli-seconds
	 */
	public static void setMonitorMaxMillis(final int maxMillis) {
		MONITOR_MAX_MILLIS.set(maxMillis);
	}
	
	/**
	 * Sets the maximum number of tries for waiting for a monitor-slot.
	 * @param maxTries number of tries per thread
	 */
	public static void setMonitorMaxTries(final int maxTries) {
		MONITOR_MAX_TRIES.set(maxTries);
	}

	/** {@inheritDoc} */
	@Override
	public void generateSourceFile(Class<?> clazz, final SourceFileRequester sourceFileRequester) throws IOException {
		final String sourceFileGuessed = Utils.guessSourceFile(clazz, sourceFileRequester.getExtension());
		SourceFileWriter sourceFileWriter = mapSourceSourceFiles.get(sourceFileGuessed);
		if (sourceFileWriter == null) {
			final ClassLoader classLoader = Utils.getClassLoader(clazz, classLoaderDefault);
			
			final Class<?> clazzOuter;
			final String className = clazz.getName();
			if (className.contains("$")) {
				final String classNameOuter = className.replaceFirst("[$].*", "");
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Start with outer-class %s of %s", className, classNameOuter));
				}
				try {
					clazzOuter = loadClass(classNameOuter, clazz);
				} catch (ClassNotFoundException e) {
					throw new JvmException(String.format("Can't load outer-class (%s)", classNameOuter), e);
				}
			}
			else {
				clazzOuter = clazz;
			}
			final ClassReader reader = SimpleClassExecutor.createClassReader(this, classLoader, clazzOuter);
			final ClassNode node = new ClassNode();
			reader.accept(node, 0);
	
			final Function<String, ClassNode> innerClassProvider = (internalName -> {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Load inner-class (%s) of (%s)", internalName, clazz));
				}
				final Class<?> classInner;
				try {
					classInner = loadClass(internalName.replace('/', '.'), clazz);
				} catch (ClassNotFoundException e) {
					throw new JvmException(String.format("Can't load inner-class (%s)", internalName), e);
				}
				final ClassReader innerReader = SimpleClassExecutor.createClassReader(this, classLoader, classInner);
				final ClassNode innerClassNode = new ClassNode();
				innerReader.accept(innerClassNode, 0);
				return innerClassNode;
			});
	
			if (LOG.isDebugEnabled()) {
				LOG.debug("Generate source-file of class " + clazz);
			}
			try (final BufferedWriter bw = sourceFileRequester.createBufferedWriter(clazz)) {
				final String extension = sourceFileRequester.getExtension();
				final String lineBreak = sourceFileRequester.lineBreak();
				final String indentation = null;
				
				sourceFileWriter = new SourceFileWriter(extension, node, innerClassProvider);
				final List<SourceLine> sourceLines = new ArrayList<>(100);
				sourceFileWriter.getSourceBlockList().collectLines(sourceLines, 0);
				sourceFileWriter.writeLines(bw, sourceLines, indentation, lineBreak);
				mapSourceSourceFiles.put(sourceFileGuessed, sourceFileWriter);
			}
		}
		mapClassSourceFiles.put(clazz, sourceFileWriter);
	}

	/** {@inheritDoc} */
	@Override
	public String getExtensionAttribute(Object classRef) {
		String extension = null;
		
		// JSR-045, smap:
		// source-debug-extension can be used to map from .class-lines to source-lines,
		// e.g. in jsp-files.
		
		//extension = "SMAP\nParser.java\njsp\n*S jsp\n*F\n1 Parser.jsp\n*L\n1#1,5:10,2\n*E\n";

		return extension;
	}

}
