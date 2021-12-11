package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;

/**
 * Value-category of an expression on stack.
 */
public enum ValueCategory {

	/** category 1 (needs one element on stack) */
	CAT1("cat1"),
	/** category 2 (needs two elements on stack: long or double */
	CAT2("cat2");

	/** display-text */
	private final String shortName;

	/**
	 * Constructor
	 * @param shortName display-text
	 */
	private ValueCategory(final String shortName) {
		this.shortName = shortName;
	}

	/**
	 * Gets a short display-text.
	 * @return display-text
	 */
	public String getShortName() {
		return shortName;
	}

	/**
	 * Checks if the type of an expression belongs to category 2 (long or double).
	 * @param expr expression
	 * @return <code>true</code> if long or double
	 */
	public static boolean isCat2(final ExpressionBase<?> expr) {
		return lookup(expr) == ValueCategory.CAT2;
	}

	/**
	 * Computes the category of the expression.
	 * @param expr expression
	 * @return value-category
	 */
	public static ValueCategory lookup(final ExpressionBase<?> expr) {
		final ValueCategory category;
		final Type type = expr.getType();
		if (type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE) {
			category = ValueCategory.CAT2;
		}
		else {
			category = ValueCategory.CAT1;
		}
		return category;
	}

	/**
	 * Gets the short-name.
	 * @return short-name
	 */
	@Override
	public String toString() {
		return getShortName();
	}
}
