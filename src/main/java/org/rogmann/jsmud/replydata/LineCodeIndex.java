package org.rogmann.jsmud.replydata;

/**
 * Initial index of a source-line.
 */
public class LineCodeIndex {

	/** line-code index */
	private final long lineCodeIndex;
	/* line-number */
	private final int lineNumber;

	/**
	 * Constructor
	 * @param lineCodeIndex line-code index
	 * @param lineNumber line-number
	 */
	public LineCodeIndex(long lineCodeIndex, int lineNumber) {
		this.lineCodeIndex = lineCodeIndex;
		this.lineNumber = lineNumber;
	}

	/**
	 * Gets the line-code index
	 * @return index
	 */
	public long getLineCodeIndex() {
		return lineCodeIndex;
	}
	
	/**
	 * Gets the line-number.
	 * @return line-number
	 */
	public int getLineNumber() {
		return lineNumber;
	}
	
}
