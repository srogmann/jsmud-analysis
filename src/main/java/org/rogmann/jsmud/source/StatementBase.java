package org.rogmann.jsmud.source;

/**
 * Expression or instruction.
 */
public abstract class StatementBase {

	/**
	 * Gets <code>true</code> if the statement is visible in the generated source-file.
	 * @return visible-flag
	 */
	@SuppressWarnings("static-method")
	public boolean isVisible() {
		return true;
	}

	/**
	 * Renders an expression or instruction.
	 * @param sb string-builder
	 */
	public abstract void render(final StringBuilder sb);
}
