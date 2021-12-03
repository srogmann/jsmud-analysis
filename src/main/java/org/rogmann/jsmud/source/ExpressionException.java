package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;

/**
 * Exception on stack at the beginning of a catch-block.
 */
public class ExpressionException extends ExpressionInstrZeroConstant {

	/** type of the exception */
	private final Type typeException;

	/**
	 * Constructor
	 * @param insn type-instruction, e.g. BIPUSH 
	 */
	public ExpressionException(final Type typeException) {
		super(null);
		this.typeException = typeException;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		final String className = SourceFileWriter.simplifyClassName(typeException);
		sb.append("catched ").append(className);
	}

}
