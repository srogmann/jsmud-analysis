package org.rogmann.jsmud.source;

import java.io.BufferedWriter;
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

	/** {@inheritDoc} */
	@Override
	public void writeLines(final BufferedWriter bw, final String indentation, final String lineBreak) throws IOException {
		final StringBuilder sb = new StringBuilder(100);
		for (final SourceLine sourceLine : lines) {
			sb.setLength(0);
			if (indentation != null) {
				for (int i = 0; i < level; i++) {
					sb.append(indentation);
				}
			}
			sb.append(sourceLine.getSourceLine());
			sb.append(lineBreak);
			bw.write(sb.toString());
		}
	}
}
