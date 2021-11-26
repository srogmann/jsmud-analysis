package org.rogmann.jsmud.source;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Block of source-code consisting of header, inner blocks and a trailing block.
 */
public class SourceBlockList extends SourceBlock {
	/** header-block */
	private SourceLines header;
	/** list of inner blocks */
	private List<SourceBlock> list = new ArrayList<>();
	/** trailing-block */
	private SourceLines tail;

	/**
	 * Constructor.
	 * @param level indentation-level
	 */
	public SourceBlockList(final int level) {
		super(level);
	}

	/**
	 * Gets the optional header-block.
	 * @return header-block
	 */
	public SourceLines getHeader() {
		return header;
	}

	/**
	 * Sets the optional header-block
	 * @param header header-block
	 */
	public void setHeader(SourceLines header) {
		this.header = header;
	}

	/**
	 * Gets a list of inner blocks.
	 * @return inner blocks
	 */
	public List<SourceBlock> getList() {
		return list;
	}

	/**
	 * Creates a block of source-lines and appends it.
	 * @return block of source-lines to be filled
	 */
	public SourceLines createSourceLines() {
		final SourceLines block = new SourceLines(level + 1);
		list.add(block);
		return block;
	}

	/**
	 * Creates a list of blocks and appends it.
	 * @return list of blocks to be filled
	 */
	public SourceBlockList createSourceBlockList() {
		final SourceBlockList block = new SourceBlockList(level + 1);
		list.add(block);
		return block;
	}

	/**
	 * Gets the optional trailing block.
	 * @return trailing block
	 */
	public SourceLines getTail() {
		return tail;
	}

	/**
	 * Sets the optional trailing block.
	 * @param trailing block
	 */
	public void setTail(SourceLines tail) {
		this.tail = tail;
	}

	/** {@inheritDoc} */
	@Override
	public void writeLines(final BufferedWriter bw, final String indentation, final String lineBreak) throws IOException {
		if (header != null) {
			header.writeLines(bw, indentation, lineBreak);
		}
		for (final SourceBlock sourceBlock : list) {
			sourceBlock.writeLines(bw, indentation, lineBreak);
		}
		if (tail != null) {
			tail.writeLines(bw, indentation, lineBreak);
		}
	}
	
}
