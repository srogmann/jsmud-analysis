package org.rogmann.jsmud.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A block of lines of code.
 */
public class SourceLines extends SourceBlock {

	/** lines of code */
	private final List<SourceLine> lines = new ArrayList<>();

	/**
	 * Constructor.
	 * @param level indentation-level
	 */
	public SourceLines(final int level) {
		super(level);
	}

	/**
	 * Gets the lines of code of this block.
	 * @return lines of code
	 */
	public List<SourceLine> getLines() {
		return lines;
	}

	/**
	 * Appends a line without known expected line.
	 * @param line line-number
	 * @param sourceLine source-line
	 */
	public void addLine(final int line, final String sourceLine) {
		lines.add(new SourceLine(line, 0, sourceLine));
	}

	/**
	 * Appends a line without known expected line.
	 * @param line line-number
	 * @param lineExpected line-number as given in debug-info
	 * @param sourceLine source-line
	 */
	public void addLine(final int line, final int lineExpected, final String sourceLine) {
		lines.add(new SourceLine(line, lineExpected, sourceLine));
	}

	/** {@inheritDoc} */
	@Override
	public int collectLines(List<SourceLine> sourceLines, final int lastLine) throws IOException {
		int currentLine = lastLine + 1;
		for (final SourceLine sourceLine : lines) {
			sourceLine.setLineCurrent(currentLine);
			sourceLine.setIndentationLevel(level);
			sourceLines.add(sourceLine);

			currentLine++;
		}
		return currentLine - 1;
	}

}
