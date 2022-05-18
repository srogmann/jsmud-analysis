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
	 * @param name display-name of source-block 
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
	 * @param tail trailing block
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
	public int getFirstLineComputed() {
		int lineNumber = 0;
		if (header != null) {
			lineNumber = header.getFirstLineComputed();
		}
		for (SourceBlock sourceBlock : list) {
			if (lineNumber == 0) {
				lineNumber = sourceBlock.getFirstLineComputed();
			}
		}
		if (lineNumber == 0 && tail != null) {
			lineNumber = tail.getFirstLineComputed();
		}
		return lineNumber;
	}

	/** {@inheritDoc} */
	@Override
	public int lowerExpectedLines(int lineNoNextBlock) {
		int nextLineNo = lineNoNextBlock;
		if (tail != null) {
			int lineNo = tail.lowerExpectedLines(nextLineNo);
			if (lineNo > 0) {
				nextLineNo = lineNo;
			}
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			final SourceBlock sourceBlock = list.get(i);
			final int lineNo = sourceBlock.lowerExpectedLines(nextLineNo);
			if (lineNo > 0) {
				nextLineNo = lineNo;
			}
		}
		if (header != null) {
			final int lineNo = header.lowerExpectedLines(nextLineNo);
			if (lineNo > 0) {
				nextLineNo = lineNo;
			}
		}
		return nextLineNo;
	}

	/** {@inheritDoc} */
	@Override
	public void lowerHeaderLines() {
		if (tail != null) {
			tail.lowerHeaderLines();
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			final SourceBlock sourceBlock = list.get(i);
			sourceBlock.lowerHeaderLines();
		}
		if (header != null) {
			header.lowerHeaderLines();
		}
		if (header != null && list.size() > 0 && level > 0) {
			final SourceBlock firstBodyBlock = list.get(0);
			final int firstBodyLine = firstBodyBlock.getFirstLineComputed();
			if (firstBodyLine > 0) {
				header.lowerLines(firstBodyLine);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void dumpStructure(final StringBuilder sb, final int level) {
		for (int i = 0; i < level; i++) {
			sb.append(' ');
		}
		sb.append("SBL ").append(name);
		if (expectedLineMin < Integer.MAX_VALUE || expectedLineMax > 0) {
			sb.append(", ").append(expectedLineMin).append("..").append(expectedLineMax);
		}
		sb.append(" (").append(numLines).append(numLines == 1 ? " line" : " lines").append(')');
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
	protected void refreshSourceBlockStatistics(boolean doReordering) {
		int expMin = Integer.MAX_VALUE;
		int expMax = 0;
		if (header != null) {
			expMin = Math.min(expMin, header.expectedLineMin);
			expMax = Math.max(expMax, header.expectedLineMax);
			numLines += header.numLines;
		}
		for (final SourceBlock sourceBlock : list) {
			sourceBlock.refreshSourceBlockStatistics(doReordering);
			expMin = Math.min(expMin, sourceBlock.expectedLineMin);
			expMax = Math.max(expMax, sourceBlock.expectedLineMax);
			numLines += sourceBlock.numLines;
		}
		if (tail != null) {
			expMin = Math.min(expMin, tail.expectedLineMin);
			expMax = Math.max(expMax, tail.expectedLineMax);
			numLines += tail.numLines;
		}

		this.expectedLineMin = expMin;
		this.expectedLineMax = expMax;

		if (doReordering) {
			for (int i = 1; i < list.size(); i++) {
				int prevMax = 0;
				for (int j = 0; j < i; j++) {
					prevMax = Math.max(prevMax, list.get(j).expectedLineMax);
				}
				final SourceBlock currBlock = list.get(i);
				final int curExpMin = currBlock.expectedLineMin;
				final int curExpMax = currBlock.expectedLineMax;
				if (curExpMax > 1 && curExpMin < Integer.MAX_VALUE && prevMax > curExpMin) {
					// Can we move this block?
					Integer destIndex = null;
					for (int j = 0; j < i; j++) {
						final int expMinNext = list.get(j).expectedLineMin;
						if (expMinNext < curExpMax && expMinNext < Integer.MAX_VALUE) {
							break;
						}
						// We can move the block.
						destIndex = Integer.valueOf(j);
						if (expMinNext > curExpMax && expMinNext < Integer.MAX_VALUE) {
							break;
						}
					}
					if (destIndex != null) {
						for(int j = i; j > destIndex.intValue(); j--) {
							list.set(j, list.get(j - 1));
						}
						list.set(destIndex.intValue(), currBlock);
					}
				}
			}
		}
	}
	
}
