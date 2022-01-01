package org.rogmann.jsmud.vm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Helper-class doing reflection.
 * 
 * <p>In Java 8 java.lang.reflection is used to change {@link Field#modifiers}.
 * In Java 9 and later java.lang.invoke.VarHandle is used to change {@link Field#modifiers}.</p>
 * 
 * <p>Requires JVM-option {@code --add-opens java.base/java.lang.reflect=ALL-UNNAMED}
 * starting with Java 16</p>
 * 
 * <p>The JDK-internal class sun.reflect.ReflectionFactory is used for instantiating bare classes.
 * See also org.objenesis.instantiator.sun.SunReflectionFactoryInstantiator.</p>
 */
public class ReflectionHelper {
	/** Logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionHelper.class);

	// instantiation
	//

	/** JDK-internal class sun.reflect.ReflectionFactory to be used to instantiate classes or <code>null</code> */
	protected final Class<?> classReflectionFactory;

	/** instance of ReflectionFactory or <code>null</code> */
	private final Object reflectionFactory;

	/** constructor of {@link Object} or <code>null</code> */
	private final Constructor<?> constrObject;

	/** method "newConstructorForSerialization" of ReflectionFactory or <code>null</code> */
	private final Method methodConstrSer;

	// field modification
	//

	/** Field#modifiers or <code>null</code> */
	private final Field fieldModifiers;

	/** VarHandle-instance for Field#modifiers or <code>null</code> */
	private final Object varHandleModifiers;

	/** Method VarHandle#set or <code>null</code> */
	private final Method methodVarHandleSet;
	
	/** method-handle of VarHandle#set */
	private final MethodHandle mhVarHandleSet;

	/**
	 * Constructor
	 */
	public ReflectionHelper() {
		classReflectionFactory = getClassReflectionFactory();

		Constructor<?> constrObject = null;
		Object reflectionFactory = null;
		Method methodSerial = null;
		if (classReflectionFactory != null) {
			try {
				final Method methodGetRF = getClassReflectionFactory().getDeclaredMethod("getReflectionFactory");
				reflectionFactory = methodGetRF.invoke(getClassReflectionFactory());
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new JvmException("Can't instantiate reflection factory", e);
			}
			try {
				constrObject = Object.class.getConstructor((Class<?>[]) null);
				methodSerial = classReflectionFactory.getDeclaredMethod("newConstructorForSerialization",
						Class.class, Constructor.class);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new JvmException("Can't instantiate reflection factory", e);
			}
		}
		this.constrObject = constrObject;
		this.reflectionFactory = reflectionFactory; 
		this.methodConstrSer = methodSerial;

		Field fieldMods;
		try {
			fieldMods = Field.class.getDeclaredField("modifiers");
			fieldMods.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			// We are not allowed to access Field#modifiers (e.g. in Java 17).
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("No reflection-access to Field#modifiers: " + e.getMessage());
			}
			fieldMods = null;
		}
		fieldModifiers = fieldMods;
		
		if (fieldMods != null) {
			varHandleModifiers = null;
			methodVarHandleSet = null;
			mhVarHandleSet = null;
		}
		else {
			Object vhMods;
			Method mVHSet;
			MethodHandle mh;
			try {
				final Class<?> classVarHandle = Class.forName("java.lang.invoke.VarHandle");
				final Method mPrivLookup = MethodHandles.class.getDeclaredMethod("privateLookupIn",
						new Class<?>[] {Class.class, Lookup.class});
				final Lookup lookup = (Lookup) mPrivLookup.invoke(MethodHandles.class,
						Field.class, MethodHandles.lookup());
				// Call public VarHandle findVarHandle(Class<?> recv, String name, Class<?> type)
				final Method methodFindVarHandle = Lookup.class.getDeclaredMethod("findVarHandle",
						new Class<?>[] {Class.class, String.class, Class.class});
				vhMods = methodFindVarHandle.invoke(lookup, Field.class, "modifiers", int.class);
				
				mVHSet = classVarHandle.getDeclaredMethod("set", Object[].class);
				final Lookup publicLookup = MethodHandles.publicLookup();
				final MethodType mt = MethodType.methodType(Void.TYPE, Field.class, Integer.class);
				mh = publicLookup.findVirtual(classVarHandle, "set", mt);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | ClassNotFoundException e) {
				vhMods = null;
				mVHSet = null;
				mh = null;
				LOGGER.error("Can't build VarHandle for Field#modifiers. The JVM-option --add-opens java.base/java.lang.reflect=ALL-UNNAMED may be neeed.", e);
			}
			varHandleModifiers = vhMods;
			methodVarHandleSet = mVHSet;
			this.mhVarHandleSet = mh;
		}
	}

	/**
	 * Gets the class sun.reflect.ReflectionFactory or <code>null</code>.
	 * @return class or <code>null</code>
	 */
	static Class<?> getClassReflectionFactory() {
		Class<?> clazzRefl = null;
		try {
			clazzRefl = Class.forName("sun.reflect.ReflectionFactory");
		}
		catch (ClassNotFoundException e) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("No class ReflectionFactory: " + e.getMessage());
			}
		}
		return clazzRefl;
	}

	/**
	 * Gets <code>true</code>, if final fields can be modified.
	 * @return final-field-modifiable flag
	 */
	public boolean canModifyFinalFields() {
		return fieldModifiers != null || (varHandleModifiers != null && methodVarHandleSet != null);
	}

	/**
	 * Gets <code>true</code> if a bare instance of a class can be built via
	 * JDK-internal class ReflectionFactory. 
	 * @return flag
	 */
	public boolean canConstructViaReflectionFactory() {
		return methodConstrSer != null;
	}

	/**
	 * Instantiates a base instance of a class.
	 * @param clazz class to be initialized
	 * @return instance of class
	 */
	public Object instantiateClass(final Class<?> clazz) {
		final Object obj;
		if (methodConstrSer != null) {
			try {
				final Constructor<?> constrClazz = (Constructor<?>) methodConstrSer.invoke(reflectionFactory,
						clazz, constrObject);
				obj = constrClazz.newInstance();
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| InstantiationException | ClassCastException e) {
				throw new JvmException(String.format("Couldn't instantiate %s via %s",
						clazz, classReflectionFactory), e);
			}
		}
		else {
			try {
				final Constructor<?> constrDefault = clazz.getDeclaredConstructor();
				constrDefault.setAccessible(true);
				obj = constrDefault.newInstance();
			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				throw new JvmException(String.format("Couldn't instantiate %s via default-constructor", clazz.getName()), e);
			}
		}
		return obj;
	}

	/**
	 * Removes the final modifier of a Field-instance.
	 * @param field field
	 * @throws JvmException in case of an error
	 */
	public void removeFieldsFinalModifier(final Field field) throws JvmException {
		final int modifiers = field.getModifiers();
		final Integer iModifiersNew = Integer.valueOf(modifiers & ~Modifier.FINAL);
		if (fieldModifiers != null) {
			try {
				fieldModifiers.set(field, iModifiersNew);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new JvmException(String.format("Can't modify final field (%s) via reflection.", field), e);
			}
		}
		else if (varHandleModifiers != null && methodVarHandleSet != null) {
			try {
				mhVarHandleSet.invoke(varHandleModifiers, field, iModifiersNew);
			} catch (Throwable e) {
				throw new JvmException(String.format("Can't modify final field (%s) via VarHandle %s.",
						field, varHandleModifiers), e);
			}
		}
		else {
			throw new JvmException(String.format("No method to modify final field (%s)", field));
		}
	}
}
