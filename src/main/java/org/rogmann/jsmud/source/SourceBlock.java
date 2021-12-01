package org.rogmann.jsmud.source;

import java.io.IOException;
import java.util.List;

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
	 * Collect the lines into a list of source-lines.
	 * @param sourceLines list to be filled
	 * @param lastLine number of last written line
	 * @return number of last written line after execution
	 * @throws IOException in case of an IO-error
	 */
	public abstract int collectLines(List<SourceLine> sourceLines, int lastLine) throws IOException;

}
