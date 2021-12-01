package org.rogmann.jsmud.source;

/**
 * Line of code in the source-file.
 */
public class SourceLine {
	/** current line in source-file */
	private int lineCurrent;

	/** line-number in class-file (0 if unknown) */
	private final int lineExpected;

	/** indentation-level */
	private int level;

	/** line in the source-file (without indentation) */
	private final String sourceLine;

	/**
	 * Constructor
	 * @param lineCurrent current line-number
	 * @param lineExpected expected line-number (0 if unknown)
	 * @param sourceLine line in the source-file (without indentation) 
	 */
	public SourceLine(final int lineCurrent, final int lineExpected,
			final String sourceLine) {
		this.lineCurrent = lineCurrent;
		this.lineExpected = lineExpected;
		this.sourceLine = sourceLine;
	}

	/**
	 * Gets the current line-number in the source-file.
	 * @return line-number
	 */
	public int getLineCurrent() {
		return lineCurrent;
	}

	/**
	 * Gets the line-number as defined in the class-file.
	 * @return expected line-number, 0 if unknown
	 */
	public int getLineExpected() {
		return lineExpected;
	}

	/**
	 * Gets the indetation-level.
	 * @return level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Gets the source-line (without indentation).
	 * @return source-line
	 */
	public String getSourceLine() {
		return sourceLine;
	}

	/**
	 * Sets the current line-number.
	 * @param lineCurrent line-number
	 */
	public void setLineCurrent(int lineCurrent) {
		this.lineCurrent = lineCurrent;
	}

	/**
	 * Sets the current indentation-level.
	 * @param level indentation-level
	 */
	public void setIndentationLevel(int level) {
		this.level = level;
	}
}
