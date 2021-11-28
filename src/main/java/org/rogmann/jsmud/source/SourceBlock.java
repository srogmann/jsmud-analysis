package org.rogmann.jsmud.source;

import java.io.IOException;
import java.io.Writer;

/**
 * Block of source-code consisting of header, inner blocks and a trailing block.
 */
public abstract class SourceBlock {

	/** level of indentation */
	protected int level;

	/**
	 * Constructor.
	 * @param level indentation-level
	 */
	public SourceBlock(final int level) {
		this.level = level;
	}

	/**
	 * Gets the indentation-level.
	 * @return level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Writes the lines into a writer.
	 * @param bw writer
	 * @param indentation optional indentation, e.g. "    " or tabulator
	 * @param lineBreak line-break
	 * @throws IOException in case of an IO-error
	 */
	public abstract void writeLines(final Writer bw, final String indentation, final String lineBreak) throws IOException;

}
