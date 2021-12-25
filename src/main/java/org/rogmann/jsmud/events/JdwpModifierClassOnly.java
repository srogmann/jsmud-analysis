package org.rogmann.jsmud.events;

import org.rogmann.jsmud.datatypes.VMReferenceTypeID;

/**
 * Case ClassOnly.
 */
public class JdwpModifierClassOnly extends JdwpEventModifier {

	/** reference-type */
	private VMReferenceTypeID clazz;

	/**
	 * Constructor
	 * @param clazz class-id
	 */
	public JdwpModifierClassOnly(final VMReferenceTypeID clazz) {
		super(ModKind.CLASS_ONLY);
		this.clazz = clazz;
	}

	/**
	 * Gets the reference-type-id
	 * @return reference-type-id
	 */
	public VMReferenceTypeID getClazz() {
		return clazz;
	}
}
