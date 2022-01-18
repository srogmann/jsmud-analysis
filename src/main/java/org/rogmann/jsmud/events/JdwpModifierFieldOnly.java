package org.rogmann.jsmud.events;

import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;

/**
 * Case FieldOnly (used to watch field access or modification).
 */
public class JdwpModifierFieldOnly extends JdwpEventModifier {

	/** reference-type */
	private final VMReferenceTypeID classId;

	/** class */
	private final Class<?> clazz;

	/** field-type */
	private final VMFieldID fieldId;

	/** field-name */
	private final String fieldName;

	/**
	 * Constructor
	 * @param classId class-id
	 * @param fieldId field-id
	 * @param clazz class
	 * @param fieldName field name
	 */
	public JdwpModifierFieldOnly(final VMReferenceTypeID classId, final VMFieldID fieldId,
			final Class<?> clazz, final String fieldName) {
		super(ModKind.FIELD_ONLY);
		this.classId = classId;
		this.fieldId = fieldId;
		this.clazz = clazz;
		this.fieldName = fieldName;
	}

	/**
	 * Gets the reference-type-id
	 * @return reference-type-id
	 */
	public VMReferenceTypeID getClassId() {
		return classId;
	}

	/**
	 * Gets the class.
	 * @return class
	 */
	public Class<?> getClazz() {
		return clazz;
	}

	/**
	 * Gets the field-id
	 * @return field-id
	 */
	public VMReferenceTypeID getFieldId() {
		return fieldId;
	}

	/**
	 * Gets the field-name.
	 * @return field-name
	 */
	public String getFieldName() {
		return fieldName;
	}
}
