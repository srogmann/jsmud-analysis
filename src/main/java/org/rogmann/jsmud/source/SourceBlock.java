package org.rogmann.jsmud.source;

import java.io.IOException;
import java.util.List;

/**
 * Block of source-code consisting of header, inner blocks and a trailing block.
 */
public abstract class SourceBlock {

	/** level of indentation */
	protected int level;

	// Some source-block statistics
	//

	protected int expectedLineMin = Integer.MAX_VALUE;
	protected int expectedLineMax = 0;
	protected int numLines = 0;

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

	/**
	 * Computes the expected minimum and maximum line-numbers of the blocks.
	 * @param doReordering try to reorder the blocks
	 */
	protected abstract void refreshSourceBlockStatistics(boolean doReordering);

	/**
	 * Dumps the structure.
	 * @param sb string-builder
	 * @param level indentation level
	 */
	public abstract void dumpStructure(final StringBuilder sb, final int level);

}
