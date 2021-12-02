package org.rogmann.jsmud.source;

/**
 * Block comment.
 */
public class StatementComment extends StatementBase {

	private final String comment;

	/**
	 * Constructor
	 * @param comment
	 */
	public StatementComment(final String comment) {
		this.comment = comment;
	}

	/** {@inheritDoc} */
	@Override
	public void render(StringBuilder sb) {
		sb.append("/** ");
		if (comment != null) {
			sb.append(comment.replace("*/", "*°/"));
		}
		sb.append(" */");
	}

}
