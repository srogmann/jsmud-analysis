package org.rogmann.jsmud.vm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
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
 */
public class ReflectionHelper {
	/** Logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionHelper.class);

	/** Field#modifiers or <code>null</code> */
	private final Field fieldModifiers;

	/** VarHandle-instance for Field#modifiers or <code>null</code> */
	private final Object varHandleModifiers;

	/** Method VarHandle#set or <code>null</code> */
	private final Method methodVarHandleSet;
	
	private final MethodHandle mh;

	/**
	 * Constructor
	 */
	public ReflectionHelper() {
		Field fieldMods;
		try {
			fieldMods = Field.class.getDeclaredField("modifiers");
			fieldMods.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			// We are not allowed to access Field#modifiers (e.g. in Java 17).
			fieldMods = null;
		}
		fieldModifiers = fieldMods;
		
		if (fieldMods != null) {
			varHandleModifiers = null;
			methodVarHandleSet = null;
			mh = null;
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
			this.mh = mh;
		}
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
				mh.invoke(varHandleModifiers, field, iModifiersNew);
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
