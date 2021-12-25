package org.rogmann.jsmud.source;

/**
 * null-value.
 */
public class ExpressionNull extends ExpressionInstrZeroConstant {
	/**
	 * Constructor
	 */
	public ExpressionNull() {
		super(null);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("null");
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("%s(null);",
				getClass().getSimpleName());
	}

}
