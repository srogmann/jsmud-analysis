package org.rogmann.jsmud;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
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
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.replydata.RefFieldBean;
import org.rogmann.jsmud.replydata.RefFrameBean;
import org.rogmann.jsmud.replydata.RefMethodBean;
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.replydata.TypeTag;
import org.rogmann.jsmud.replydata.VariableSlot;

/**
 * Registry of classes whose execution should be simulated.
 * <p>This class simulates the JVM.</p>
 */
public class ClassRegistry implements VM {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ClassRegistry.class);

	/** version */
	public static String VERSION = "jsmud 0.2.2 (2021-07-16)";

	/** maximal wait-time in a monitor (this would be infinity in a read JVM) */
	private static final AtomicInteger MONITOR_MAX_MILLIS = new AtomicInteger(60000);

	/** maximum number of while waiting for a monitor-slot */ 
	private static final AtomicInteger MONITOR_MAX_TRIES = new AtomicInteger(100);

	/** map from class to executor */
	private final Map<Class<?>, SimpleClassExecutor> mapClassExecutors = new HashMap<>(500);

	/** execution-filter */
	final ClassExecutionFilter executionFilter;

	/** class-loader (default) */
	private final ClassLoader classLoaderDefault;
	
	/** <code>true</code> if reflection should be emulated too */
	private final boolean simulateReflection;
	
	/** JVM-visitor */
	private final JvmExecutionVisitor visitor;

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

	/** map of all monitor-objects */
	private final Map<Object, ThreadMonitor> mapMonitorObjects = Collections.synchronizedMap(new IdentityHashMap<>());

	/** map from thread to an monitor-object the thread is waiting for */
	private final ConcurrentMap<Thread, Object> mapContentedMonitor = new ConcurrentHashMap<>();

	/** object-id-counter */
	private final AtomicLong objectIdCounter = new AtomicLong();
	
	/** map from object-id to object */
	private final ConcurrentMap<VMObjectID, Object> mapObjects = new ConcurrentHashMap<>(5000);
	
	/** map of known class-loaders */
	private final ConcurrentMap<ClassLoader, VMClassLoaderID> mapClassLoader = new ConcurrentHashMap<>();
	
	/** map of loaded classes */
	private final ConcurrentMap<String, Class<?>> mapLoadedClasses = new ConcurrentHashMap<>();

	/** map containing ref-type-beans of class-signatures */
	private final ConcurrentMap<String, RefTypeBean> mapClassSignatures = new ConcurrentHashMap<>(100);

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
	
	/** map from string to string-id */
	private final ConcurrentMap<String, VMStringID> mapStrings = new ConcurrentHashMap<>();
	
	/** map from object to object-id of a variable-value */
	private final ConcurrentMap<Object, VMObjectID> mapVariableValues = new ConcurrentHashMap<>(100);
	
	/** method-stack */
	private final Stack<MethodFrame> stack = new Stack<>();

	/**
	 * Constructor
	 * @param executionFilter determines classes to be simulated
	 * @param classLoader class-loader to be used as default
	 * @param simulateReflection <code>true</code> if reflection should be emulated
	 * @param visitor JVM-visitor
	 * @param invocationHandler invocation-handler
	 */
	public ClassRegistry(final ClassExecutionFilter executionFilter,
			final ClassLoader classLoader,
			final boolean simulateReflection,
			final JvmExecutionVisitor visitor, final JvmInvocationHandler invocationHandler) {
		this.executionFilter = executionFilter;
		this.classLoaderDefault = classLoader;
		this.simulateReflection = simulateReflection;
		this.visitor = visitor;
		this.invocationHandler = invocationHandler;
		callSiteRegistry = new CallSiteRegistry(classLoader);
	}
	
	/**
	 * Looks for an executor.
	 * Only classes whose package-prefix is in the list of simulated packages will be simulated.
	 * @param clazz class to be simulated
	 * @return executor or <code>null</code>
	 */
	public SimpleClassExecutor getClassExecutor(final Class<?> clazz) {
		SimpleClassExecutor executor = mapClassExecutors.get(clazz);
		// We don't want to analyze ourself (i.e. JsmudClassLoader).
		if (executor == null && !JsmudClassLoader.class.equals(clazz)) {
			boolean doSimulation = executionFilter.isClassToBeSimulated(clazz);
			if (doSimulation) {
				executor = new SimpleClassExecutor(this, clazz, visitor, invocationHandler);
				mapClassExecutors.put(clazz, executor);
				visitor.visitLoadClass(clazz);
			}
		}
		return executor;
	}
	
	/**
	 * Gets the default class-loader.
	 * @return class-loader
	 */
	public ClassLoader getClassLoader() {
		return classLoaderDefault;
	}

	/**
	 * Loads a class.
	 * The context-class is used to determine the class-loader to be used.
	 * @param className qualified class-name, e.g. "java.lang.String"
	 * @param ctxClass context-class, i.e. a class which knows the class to be loaded
	 * @return loaded class
	 * @throws ClassNotFoundException if the class can't be found
	 */
	public Class<?> loadClass(String className, final Class<?> ctxClass) throws ClassNotFoundException {
		// This implementation discards the used class-loader.
		// The handling of classes with the same name in different class-loaders is not correct.
		Class<?> clazz = mapLoadedClasses.get(className);
		if (clazz == null) {
			final ClassLoader classLoaderClass;
			final ClassLoader ctxClassLoader = (ctxClass != null) ? ctxClass.getClassLoader() : null;
			if (ctxClassLoader instanceof JsmudClassLoader) {
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
			if (classLoaderDefault instanceof JsmudClassLoader) {
				final JsmudClassLoader jsmudClassLoader = (JsmudClassLoader) classLoaderDefault;
				clazz = jsmudClassLoader.findClass(className, classLoaderClass);
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
			final SimpleClassExecutor executor = getClassExecutor(clazz);
			if (classLoaderDefault instanceof JsmudClassLoader && executor != null) {
				final JsmudClassLoader jsmudCl = (JsmudClassLoader) classLoaderDefault;
				if (jsmudCl.isStaticInitializerPatched(clazz)) {
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
							LOG.debug(String.format("Execute patched static initializer of (%s)", className));
						}
						executor.executeMethod(Opcodes.INVOKESTATIC, pMethod, methodDesc, args);
					} catch (final Throwable e) {
						throw new JvmException(String.format("Error while executing static initializer (%s) in (%s)",
								methodName, className), e);
					}
				}
			}
		}
		return clazz;
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
		mapObjects.put(refTypeId, clazz);
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
	 * Returns <code>true</code> if some method-calls in java.lang.reflect should be emulated.
	 * @return simulate-reflection-flag
	 */
	public boolean isSimulateReflection() {
		return simulateReflection;
	}

	/**
	 * Gets the call-site-registry.
	 * @return registry
	 */
	public CallSiteRegistry getCallSiteRegistry() {
		return callSiteRegistry;
	}

	/**
	 * Registers a thread and the corresponding thread-group.
	 * @param thread thread
	 */
	public void registerThread(final Thread thread) {
		final Long threadKey = Long.valueOf(thread.getId());
		if (!mapThreads.containsKey(threadKey)) {
			final VMThreadID vmThreadID = new VMThreadID(objectIdCounter.incrementAndGet());
			final VMThreadGroupID vmThreadGroupID = new VMThreadGroupID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmThreadID, thread);
			mapObjects.put(vmThreadGroupID, thread.getThreadGroup());

			mapThreads.put(threadKey, vmThreadID);
			mapThreadGroups.put(threadKey, vmThreadGroupID);		
			mapThreadSuspendCounter.put(threadKey, new AtomicInteger(0));
		}
	}

	/**
	 * Removes a thread and the corresonding thread-group.
	 * @param thread thread
	 */
	public void unregisterThread(final Thread thread) {
		final Long threadKey = Long.valueOf(thread.getId());
		final VMThreadID vmThreadID = mapThreads.remove(threadKey);
		if (vmThreadID != null) {
			mapObjects.remove(vmThreadID);
			mapThreads.remove(threadKey);
			mapThreadSuspendCounter.remove(threadKey);
		}
		final VMThreadGroupID vmThreadGroupID = mapThreadGroups.remove(threadKey);
		if (vmThreadGroupID != null) {
			mapObjects.remove(vmThreadGroupID, thread.getThreadGroup());
		}
	}

	/**
	 * Enters a monitor.
	 * @param objMonitor monitor-object
	 * @return current monitor-counter
	 */
	public Integer enterMonitor(final Object objMonitor) {
		final Thread currentThread = Thread.currentThread();
		ThreadMonitor threadMonitor;
		int tryCounter = 0;
		mapContentedMonitor.put(currentThread, objMonitor);
		try {
			do {
				threadMonitor = mapMonitorObjects.computeIfAbsent(objMonitor, o -> new ThreadMonitor(o, currentThread));
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
		}
		finally {
			mapContentedMonitor.remove(currentThread);

		}
		return Integer.valueOf(threadMonitor.incrementCounter());
	}

	/**
	 * Exits a monitor.
	 * @param objMonitor monitor-object
	 * @return current monitor-counter
	 */
	public Integer exitMonitor(final Object objMonitor) {
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
		if (counter == 0) {
			// release the monitor.
			mapMonitorObjects.remove(objMonitor);
			threadMonitor.decrementCounter();
		}
		return Integer.valueOf(counter);
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
					mapObjects.put(sId, key);
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
				mapObjects.put(clId, key);
				return clId;
			});
		}
		return classLoaderId;
	}

	/** {@inheritDoc} */
	@Override
	public VMClassID getSuperClass(VMClassID classId) {
		VMClassID superClassId = null;
		final Object oClass = mapObjects.get(classId);
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
					superClassId = null;
				}
			}
		}
		return superClassId;
	}

	/** {@inheritDoc} */
	@Override
	public List<LineCodeIndex> getLineTable(Class<?> clazz, Executable executable, VMReferenceTypeID refType,
			VMMethodID methodID) {
		final List<LineCodeIndex> lineTable = new ArrayList<>();
		final SimpleClassExecutor classExecutor = getClassExecutor(clazz);
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
				final InsnList instructions = methodNode.instructions;
				final int numInstr = instructions.size();
				int currLine = 0;
				int lastDebugLine = 0;
				for (int i = 0; i < numInstr; i++) {
					AbstractInsnNode instr = instructions.get(i);
					final int opcode = instr.getOpcode();
					if (instr instanceof LineNumberNode) {
						final LineNumberNode ln = (LineNumberNode) instr;
						currLine = ln.line;
					}
					else if (opcode >= 0) {
						if (currLine > lastDebugLine) {
							// We place the line-number-index at the first opcode of a line.
							final int index = instructions.indexOf(instr);
							lineTable.add(new LineCodeIndex(index, currLine));
							lastDebugLine = currLine;
						}
					}
				}
			}
		}
		return lineTable;
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
				mapObjects.put(interfaceId, classInterface);
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
				final VMFieldID fieldId = new VMFieldID(objectIdCounter.incrementAndGet());
				final String signature = Type.getDescriptor(field.getType());
				final String genericSignature = ""; // TODO genericSignature
				fieldRefBean = new RefFieldBean(fieldId, field.getName(), signature, genericSignature, field.getModifiers());
				mapRefFieldBean.put(field, fieldRefBean);
				mapObjects.put(fieldId, field);
			}
			list.add(fieldRefBean);
		}
		return list;
	}

	/** {@inheritDoc} */
	@Override
	public RefFieldBean getRefFieldBean(VMFieldID fieldID) {
		RefFieldBean refFieldBean = null;
		final Object oField = mapObjects.get(fieldID);
		if (oField instanceof Field) {
			final Field field = (Field) oField;
			refFieldBean = mapRefFieldBean.get(field);
		}
		return refFieldBean;
	}

	/**
	 * Gets the id of a method.
	 * @param method method
	 * @return method-id
	 */
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
			mapObjects.put(methodId, method);
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
	public List<VMValue> readObjectFieldValues(final Object vmObject, final List<VMFieldID> listFields) {
		final List<VMValue> values = new ArrayList<>(listFields.size());
		for (int i = 0; i < listFields.size(); i++) {
			final Object oField = mapObjects.get(listFields.get(i));
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
			final Field field = (Field) mapObjects.get(refFieldBean.getFieldID());
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
			if (localVariables != null) {
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
			if (slot < 0 && slot >= aLocals.length) {
				// A wrong slot.
				break;
			}
			final Object valueJvm = aLocals[slot];
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
				final char iValue = Array.getChar(objArray, firstIndex + i);
				values[i] = new VMShort((short) iValue);
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
				mapObjects.put(vmObjectID, value);
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
			oValue = mapObjects.get(vmObjectID);
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
				LOG.debug(String.format("Execute: oResult=%s, result=%s.%s",
						oResult, tag, dfResAndExc[0]));
			}
		}
		catch (IllegalAccessException | IllegalArgumentException e) {
			e.printStackTrace();
			final VMObjectID vmExceptionId = new VMObjectID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmExceptionId, e);
			dfResAndExc[1] = new VMTaggedObjectId(vmExceptionId);
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
			final Throwable eCause = e.getCause();
			final VMObjectID vmExceptionId = new VMObjectID(objectIdCounter.incrementAndGet());
			mapObjects.put(vmExceptionId, eCause);
			dfResAndExc[1] = new VMTaggedObjectId(vmExceptionId);
		}
		return dfResAndExc;
	}

	/**
	 * Gets the thread-id of a thread.
	 * @param thread thread
	 * @return thread-id or <code>null</code>
	 */
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
		return mapObjects.get(objectId);
	}
	
	/**
	 * Pushes a method-frame onto the stack.
	 * @param frame method-frame
	 */
	public void pushMethodFrame(final MethodFrame frame) {
		stack.push(frame);
	}
	
	/**
	 * Removes the method-frame on top of the stack.
	 */
	public void popMethodFrame() {
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
		final Thread curThread = Thread.currentThread();
		if (cThreadId.equals(getThreadId(curThread))) {
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
					mapObjects.put(frameId, mf);
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
	public Integer getSuspendCount(VMThreadID cThreadId) {
		Object oThread = mapObjects.get(cThreadId);
		Integer iSuspCounter = null;
		if (oThread instanceof Thread) {
			final Thread thread = (Thread) oThread;
			final AtomicInteger suspCounter = mapThreadSuspendCounter.get(Long.valueOf(thread.getId()));
			if (suspCounter != null) {
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
		Object oThread = mapObjects.get(cThreadId);
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
		Object oThread = mapObjects.get(cThreadId);
		boolean isValid = false;
		if (oThread instanceof Thread) {
			final Thread thread = (Thread) oThread;
			final AtomicInteger suspCounter = mapThreadSuspendCounter.get(Long.valueOf(thread.getId()));
			if (suspCounter == null) {
				throw new DebuggerException(String.format("Thread (%s, %s) without internal suspend-counter",
						cThreadId, thread.getName()));
			}
			final int counter = suspCounter.decrementAndGet();
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

}
