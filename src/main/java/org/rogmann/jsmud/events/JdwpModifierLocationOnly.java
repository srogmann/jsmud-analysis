package org.rogmann.jsmud.events;

import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMMethodID;

/**
 * Details of a BREAKPOINT-event-request.
 * The breakpoint hat a location which consists of type-tag, class-id, method-id and index.
 */
public class JdwpModifierLocationOnly extends JdwpEventModifier {

	/** type-tag */
	private final byte typeTag;
	/** class-id */
	private final VMClassID classID;
	/** method-id */
	private final VMMethodID methodId;
	/** index in method */
	private final long index;

	/**
	 * Constructor
	 * @param typeTag type-tag
	 * @param classID class-id
	 * @param methodId method-id
	 * @param index index in method
	 */
	public JdwpModifierLocationOnly(final byte typeTag,
			final VMClassID classID, final VMMethodID methodId, final long index) {
		super(ModKind.LOCATION_ONLY);
		this.typeTag = typeTag;
		this.classID = classID;
		this.methodId = methodId;
		this.index = index;
	}

	/**
	 * Gets the type-tag.
	 * @return type-tag
	 */
	public byte getTypeTag() {
		return typeTag;
	}

	/**
	 * Gets the class-id.
	 * @return class-id
	 */
	public VMClassID getClassID() {
		return classID;
	}

	/**
	 * Gets the method-id.
	 * @return method-id
	 */
	public VMMethodID getMethodId() {
		return methodId;
	}

	/**
	 * Gets the index of the location in the method (VM-dependend).
	 * @return index
	 */
	public long getIndex() {
		return index;
	}

}
