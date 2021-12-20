package org.rogmann.jsmud.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Block of source-code consisting of header, inner blocks and a trailing block.
 */
public class SourceBlockList extends SourceBlock {

	/** display name of this block-list */
	private final String name;

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
	public SourceBlockList(final int level, final String name) {
		super(level);
		this.name = name;
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
	 * @param name display-name
	 * @return list of blocks to be filled
	 */
	public SourceBlockList createSourceBlockList(final String name) {
		final SourceBlockList block = new SourceBlockList(level + 1, name);
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
	public int collectLines(final List<SourceLine> sourceLines, final int lastLine) throws IOException {
		int mLastLine = lastLine;
		if (header != null) {
			mLastLine = header.collectLines(sourceLines, mLastLine);
		}
		for (final SourceBlock sourceBlock : list) {
			mLastLine = sourceBlock.collectLines(sourceLines, mLastLine);
		}
		if (tail != null) {
			mLastLine = tail.collectLines(sourceLines, mLastLine);
		}
		return mLastLine;
	}

	/** {@inheritDoc} */
	@Override
	public void dumpStructure(final StringBuilder sb, final int level) {
		for (int i = 0; i < level; i++) {
			sb.append(' ');
		}
		sb.append("SBL ").append(name); 
		sb.append(System.lineSeparator());
		if (header != null) {
			header.dumpStructure(sb, level + 1);
		}
		for (SourceBlock sourceBlock : list) {
			sourceBlock.dumpStructure(sb, level + 1);
		}
		if (tail != null) {
			tail.dumpStructure(sb, level + 1);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void refreshSourceBlockStatistics() {
		int expMin = Integer.MAX_VALUE;
		int expMax = Integer.MIN_VALUE;
		if (header != null) {
			expMin = Math.min(expMin, header.expectedLineMin);
			expMax = Math.max(expMax, header.expectedLineMax);
			numLines += header.numLines;
		}
		for (final SourceBlock sourceBlock : list) {
			sourceBlock.refreshSourceBlockStatistics();
			expMin = Math.min(expMin, sourceBlock.expectedLineMin);
			expMax = Math.max(expMax, sourceBlock.expectedLineMax);
			numLines += sourceBlock.numLines;
		}
		if (tail != null) {
			expMin = Math.min(expMin, tail.expectedLineMin);
			expMax = Math.max(expMax, tail.expectedLineMax);
			numLines += tail.numLines;
		}
	}
	
}
