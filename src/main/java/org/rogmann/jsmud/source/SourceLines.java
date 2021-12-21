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

	/** {@inheritDoc} */
	@Override
	public void dumpStructure(final StringBuilder sb, final int level) {
		for (int i = 0; i < level; i++) {
			sb.append(' ');
		}
		sb.append("SL");
		int currMin = Integer.MAX_VALUE;
		int currMax = Integer.MIN_VALUE;
		int expMin = Integer.MAX_VALUE;
		int expMax = Integer.MIN_VALUE;
		for (SourceLine sourceLine : lines) {
			final int currLine = sourceLine.getLineCurrent();
			final int expLine = sourceLine.getLineExpected();
			currMin = Math.min(currLine, currMin);
			currMax = Math.max(currLine,  currMax);
			if (expLine > 0) {
				expMin = Math.min(expLine, expMin);
				expMax = Math.max(expLine, expMax);
			}
		}
		if (currMin > 0 || currMax > 0) {
			sb.append(' ').append(currMin).append("..").append(currMax);
		}
		if (expMin < Integer.MAX_VALUE || expMax > 0) {
			sb.append(" -> ");
			sb.append(expMin).append("..").append(expMax);
		}
		if (lines.size() == 1) {
			sb.append(", 1 line");
		}
		else {
			sb.append(", ").append(lines.size()).append(" lines");
		}
		sb.append(System.lineSeparator());
	}

	/** {@inheritDoc} */
	@Override
	protected void refreshSourceBlockStatistics(boolean doReordering) {
		int expMin = Integer.MAX_VALUE;
		int expMax = 0;
		for (SourceLine sourceLine : lines) {
			final int expLine = sourceLine.getLineExpected();
			if (expLine > 1) {
				expMin = Math.min(expLine, expMin);
			}
			if (expLine > 0) {
				expMax = Math.max(expLine, expMax);
			}
		}
		expectedLineMin = expMin;
		expectedLineMax = expMax;

		numLines = lines.size();
	}

}
