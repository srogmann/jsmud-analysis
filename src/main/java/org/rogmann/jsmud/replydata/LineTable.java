package org.rogmann.jsmud.replydata;

import java.util.List;

/**
 * Line-table of a method.
 */
public class LineTable {

	/** lowest valid index of the method */
	private final long start;
	/** highest valid index of the method */
	private final long end;
	/** list of line-numbers */
	private final List<LineCodeIndex> listLci;
	
	/**
	 * Constructor
	 * @param start lowest valid index of the method
	 * @param end highest valid index of the method
	 * @param listLci list of line-numbers
	 */
	public LineTable(long start, long end, List<LineCodeIndex> listLci) {
		this.start = start;
		this.end = end;
		this.listLci = listLci;
	}

	/**
	 * Gets the lowest valid index of the method
	 * @return index
	 */
	public long getStart() {
		return start;
	}
	
	/**
	 * Gets the highest valid index of the method.
	 * @return index
	 */
	public long getEnd() {
		return end;
	}
	
	/**
	 * Gets a list of indexes of the first instruction of each line.
	 * @return line-code-indexes
	 */
	public List<LineCodeIndex> getListLci() {
		return listLci;
	}
	
}
