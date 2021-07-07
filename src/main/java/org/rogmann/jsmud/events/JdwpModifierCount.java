package org.rogmann.jsmud.events;

/**
 * Case count.
 */
public class JdwpModifierCount extends JdwpEventModifier {

	/** count */
	private int count;

	/**
	 * Constructor
	 * @param count count
	 */
	public JdwpModifierCount(final int count) {
		super(ModKind.COUNT);
		this.count = count;
	}

	/**
	 * Gets the count of the modifier.
	 * @return count
	 */
	public int getCount() {
		return count;
	}
}
