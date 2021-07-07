package org.rogmann.jsmud.events;

/**
 * Abstract base-class of a event-modifier.
 */
public abstract class JdwpEventModifier {
	
	/** modifier kind */
	private final ModKind modKind;
	
	/**
	 * Constructor
	 * @param modKind modifier kind
	 */
	protected JdwpEventModifier(final ModKind modKind) {
		this.modKind = modKind;
	}

	/**
	 * Gets the modifier kind.
	 * @return modifier kind
	 */
	public ModKind getModKind() {
		return modKind;
	}

}
