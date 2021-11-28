package org.rogmann.jsmud.source;

/**
 * null-value.
 */
public class ExpressionNull extends ExpressionInstrZeroConstant {
	/**
	 * Constructor
	 * @param insn instruction
	 */
	public ExpressionNull() {
		super(null);
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("null");
	}

}
