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
	 * @param typeException type of exception, <code>null</code> in case of a finally-block
	 */
	public ExpressionException(final Type typeException) {
		super(null);
		this.typeException = typeException;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (typeException != null) {
			final String className = SourceFileWriter.simplifyClassName(typeException);
			sb.append("catched ").append(className);
		}
		else {
			final Type typeThrowable = Type.getType(Throwable.class);
			sb.append("finally ").append(SourceFileWriter.simplifyClassName(typeThrowable));
		}
	}

}
