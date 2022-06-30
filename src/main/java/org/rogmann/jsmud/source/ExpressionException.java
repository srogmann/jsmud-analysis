package org.rogmann.jsmud.source;

import org.objectweb.asm.Type;

/**
 * Exception on stack at the beginning of a catch-block.
 */
public class ExpressionException extends ExpressionInstrZeroConstant {

	/** type of the exception */
	private final Type typeException;

	/** source-name renderer */
	private final SourceNameRenderer sourceNameRenderer;

	/**
	 * Constructor
	 * @param typeException type of exception, <code>null</code> in case of a finally-block
	 * @param sourceNameRenderer source-name renderer
	 */
	public ExpressionException(final Type typeException, final SourceNameRenderer sourceNameRenderer) {
		super(null);
		this.typeException = typeException;
		this.sourceNameRenderer = sourceNameRenderer;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		if (typeException != null) {
			final String className = sourceNameRenderer.renderType(typeException);
			sb.append("catched ").append(className);
		}
		else {
			final Type typeThrowable = Type.getType(Throwable.class);
			sb.append("finally ").append(sourceNameRenderer.renderType(typeThrowable));
		}
	}

}
