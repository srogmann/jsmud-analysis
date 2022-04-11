package org.rogmann.jsmud.vm;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassWriter;
import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * A non-sophisticated class mapper working on the constant pool only.
 * A sophisticated solution would use the remapper in asm-common.
 */
public class ClassRemapperSymbolTable implements ClassRemapper {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ClassRemapperSymbolTable.class);

	/** The tag value of CONSTANT_Class_info JVMS structures. */
	static final int CONSTANT_CLASS_TAG = 7;
	/** tag value of UTF8-strings */
	private static final int CONSTANT_UTF8 = 1;
	/** tag value of FieldRef */
	private static final int CONSTANT_FIELD_REF = 9;
	/** tag value of MethodRef */
	private static final int CONSTANT_METHOD_REF = 10;
	/** tag value of InterfaceMethodref */
	private static final int CONSTANT_INTERFACE_METHOD_REF = 11;
	/** tag value of String */
	private static final int CONSTANT_STRING = 8;
	/** tag value of Integer */
	private static final int CONSTANT_INTEGER = 3;
	/** tag value of Float */
	private static final int CONSTANT_FLOAT = 4;
	/** tag value of Long */
	private static final int CONSTANT_LONG = 5;
	/** tag value of Double */
	private static final int CONSTANT_DOUBLE = 6;
	/** tag value of NameAndType */
	private static final int CONSTANT_NAME_AND_TYPE = 12;
	/** tag value of MethodHandle */
	private static final int CONSTANT_METHOD_HANDLE = 15;
	/** tag value of MethodType */
	private static final int CONSTANT_METHOD_TYPE = 16;
	/** tag value of InvokeDynamic */
	private static final int CONSTANT_INVOKE_DYNAMIC = 18;

	/** map from internal JRE-class name to mapped internal class-name, e.g. "java/lang/Record" to "jdksim/java/lang/Record" */
	private final ConcurrentMap<String, String> mapInternal = new ConcurrentHashMap<>();
	/** map from class name to mapped class-name, e.g. "java.lang.Record" to "jdksim.java.lang.Record" */
	private final ConcurrentMap<String, String> mapClassName = new ConcurrentHashMap<>();
	
	/** internal field of symbol table in ClassWriter in asm */
	private final Field fieldSymbolTable;

	/** internal field of ByteVector in SymbolTable in asm */
	private final Field fieldConstantPool;
	/** internal field of entries in SymbolTable in asm */
	private final Field fieldEntries;

	/** internal field of next entry in Entry in asm */
	private final Field fieldNextEntry;

	/** internal field of index in Symbol (type int) */
	protected final Field fieldSymbolIndex;

	/** internal field of tag in Symbol (type int) */
	protected final Field fieldSymbolTag;

	/** internal field of owner in Symbol (type String) */
	private final Field fieldSymbolOwner;

	/** internal field of name in Symbol (type String) */
	private final Field fieldSymbolName;

	/** internal field of value in Symbol (type String) */
	private final Field fieldSymbolValue;

	/** internal field of data in Symbol (type long) */
	protected final Field fieldSymbolData;

	/** internal field "data" of ByteVector (type byte[]) */ 
	private final Field fieldByteVectorData;

	/** internal field "length" of ByteVector (type int) */ 
	private final Field fieldByteVectorLength;

	/**
	 * Constructor
	 * @throws JvmException in case of internal changes in asm
	 */
	public ClassRemapperSymbolTable() {
		final Class<ClassWriter> classClassWriter = ClassWriter.class;
		fieldSymbolTable = getInternalField(classClassWriter, "symbolTable");

		final Class<?> classSymbolTable = fieldSymbolTable.getType();
		fieldEntries = getInternalField(classSymbolTable, "entries");
		fieldConstantPool = getInternalField(classSymbolTable, "constantPool");

		final Class<?> classArrayEntry = fieldEntries.getType();
		final Class<?> classEntry = classArrayEntry.getComponentType();
		fieldNextEntry = getInternalField(classEntry, "next");
		
		final Class<?> classSymbol = classEntry.getSuperclass();
		fieldSymbolIndex = getInternalField(classSymbol, "index");
		fieldSymbolTag = getInternalField(classSymbol, "tag");
		fieldSymbolOwner = getInternalField(classSymbol, "owner");
		fieldSymbolName = getInternalField(classSymbol, "name");
		fieldSymbolValue = getInternalField(classSymbol, "value");
		fieldSymbolData = getInternalField(classSymbol, "data");
		
		fieldByteVectorData = getInternalField(ByteVector.class, "data");
		fieldByteVectorLength = getInternalField(ByteVector.class, "length");
	}

	/** {@inheritDoc} */
	@Override
	public String remapName(String className) {
		return mapClassName.get(className);
	}

	/** {@inheritDoc} */
	@Override
	public void put(final String internalName, final String mappedInternalName) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("put %s to %s", internalName, mappedInternalName));
		}
		mapInternal.put(internalName, mappedInternalName);
		mapClassName.put(internalName.replace('/', '.'), mappedInternalName.replace('/', '.'));
	}

	/** {@inheritDoc} */
	@Override
	public ClassWriter remapClassWriter(ClassWriter classWriter, String className) {
		try {
			final Object objSymbolTable = fieldSymbolTable.get(classWriter);
			final Object[] aEntries = (Object[]) fieldEntries.get(objSymbolTable);
			for (Object loopEntry : aEntries) {
				Object entry = loopEntry;
				if (entry == null) {
					continue;
				}
				// final int tag = fieldSymbolTag.getInt(entry);
				mapEntryField(entry, fieldSymbolName);
				mapEntryField(entry, fieldSymbolOwner);
				mapEntryField(entry, fieldSymbolValue);
				entry = fieldNextEntry.get(entry);
			}

			final ByteVector byteVector = (ByteVector) fieldConstantPool.get(objSymbolTable);
			final byte[] data = (byte[]) fieldByteVectorData.get(byteVector);
			final int length = ((Integer) fieldByteVectorLength.get(byteVector)).intValue();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
			for (int idx = 0; idx < length;) {
				final byte tag = data[idx];
				if (tag == CONSTANT_UTF8) {
					final int len = ((data[idx + 1] & 0xff) << 8) + (data[idx + 2] & 0xff);
					final String value = new String(data, idx + 3, len, StandardCharsets.UTF_8);
					final String valueMapped = mapValue(value);
					final int lenMapped = valueMapped.length();
					baos.write((byte) CONSTANT_UTF8);
					baos.write((byte) (lenMapped >> 8));
					baos.write((byte) (lenMapped & 0xff));
					final byte[] bufValueMapped = valueMapped.getBytes(StandardCharsets.UTF_8);
					baos.write(bufValueMapped, 0, bufValueMapped.length);
					idx += 1 + 2 + len;
				}
				else {
					final int lenTagContent;
					switch (tag) {
					case CONSTANT_CLASS_TAG: lenTagContent = 2; break;
					case CONSTANT_FIELD_REF:
					case CONSTANT_METHOD_REF:
					case CONSTANT_INTERFACE_METHOD_REF: lenTagContent = 2 + 2; break;
					case CONSTANT_STRING: lenTagContent = 2; break;
					case CONSTANT_INTEGER:
					case CONSTANT_FLOAT: lenTagContent = 4; break;
					case CONSTANT_LONG:
					case CONSTANT_DOUBLE: lenTagContent = 8; break;
					case CONSTANT_NAME_AND_TYPE: lenTagContent = 2 + 2; break;
					case CONSTANT_METHOD_HANDLE: lenTagContent = 1 + 2; break;
					case CONSTANT_METHOD_TYPE: lenTagContent = 2; break;
					case CONSTANT_INVOKE_DYNAMIC: lenTagContent = 2 + 2; break;
					default:
						throw new JvmException(String.format("Unexpected tag (%d) at index (%d) in constant-pool of %s",
								Integer.valueOf(tag & 0xff), Integer.valueOf(idx--), className));
					}
					final int lenTag = 1 + lenTagContent;
					baos.write(data, idx, lenTag);
					idx += lenTag;
				}
			}
			final byte[] bufDataMapped = baos.toByteArray();
			fieldByteVectorData.set(byteVector, bufDataMapped);
			fieldByteVectorLength.set(byteVector, Integer.valueOf(bufDataMapped.length));
		}
		catch (IllegalArgumentException e) {
			throw new JvmException(String.format("Argument exception when trying to remap the internal symbol table of %s", classWriter), e);
		}
		catch (IllegalAccessException e) {
			throw new JvmException(String.format("Access exception when trying to remap the internal symbol table of %s", classWriter), e);
		}
		
		return classWriter;
	}

	/**
	 * Maps a value.
	 * @param value value to be mapped
	 * @return mapped value
	 */
	private String mapValue(String value) {
		String valueMapped = value;
		for (Entry<String, String> entryPattern : mapInternal.entrySet()) {
			final String search = entryPattern.getKey();
			final String replace = entryPattern.getValue();
			valueMapped = valueMapped.replace(search, replace);
		}
		return valueMapped;
	}

	/**
	 * Maps a value of a field of an entry.
	 * @param entry Symbol
	 * @param field field
	 */
	private void mapEntryField(Object entry, Field field) {
		try {
			final String name = (String) field.get(entry);
			if (name == null) {
				return;
			}
			String nameMapped = name;
			for (Entry<String, String> entryPattern : mapInternal.entrySet()) {
				final String search = entryPattern.getKey();
				final String replace = entryPattern.getValue();
				nameMapped = nameMapped.replace(search, replace);
			}
			if (!nameMapped.equals(name)) {
				LOG.debug(String.format("field %s: %s - %s", field, name, nameMapped));
				field.set(entry, nameMapped);
			}
		}
		catch (IllegalArgumentException e) {
			throw new JvmException(String.format("Argument exception when changing field (%s) of (%s)",
					field, entry), e);
		}
		catch (IllegalAccessException e) {
			throw new JvmException(String.format("Access exception when changing field (%s) of (%s)",
					field, entry), e);
		}
	}

	/**
	 * Gets an internal field of an possibly internal asm-class.
	 * @param clazz class
	 * @param fieldName name of the field
	 * @return field
	 * @throws JvmException in case of an access-error or change in asm (internal class!)
	 */
	private static Field getInternalField(final Class<?> clazz, String fieldName) {
		final Field field;
		try {
			field = clazz.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new JvmException(String.format("There is no internal field (%s) in asm-class (%s)",
					fieldName, clazz), e);
		} catch (SecurityException e) {
			throw new JvmException(String.format("Access to field (%s) in asm-class (%s) is not allowed",
					fieldName, clazz), e);
		}
		field.setAccessible(true);
		return field;
	}
	
}
