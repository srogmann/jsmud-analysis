package org.rogmann.jsmud.debugger;

import java.nio.charset.StandardCharsets;

/**
 * Class for reading a buffer containing a command.
 */
public class CommandBuffer {
	/** buffer */
	private final byte[] buf;
	/** start-offset of the command in the buffer */
	private final int offsetStart;
	/** end-offset of the command in the buffer */
	private final int offsetEnd;
	/** current offset in the buffer */
	private int offset;

	/**
	 * Constructor
	 * @param buf buffer
	 * @param offset start-offset of the command in the buffer
	 * @param offsetEnd end-offse of the command in the buffer
	 */
	public CommandBuffer(byte[] buf, int offset, int offsetEnd) {
		this.buf = buf;
		this.offsetStart = offset;
		this.offsetEnd = offsetEnd;
		this.offset = this.offsetStart;
	}

	/**
	 * Reads a byte.
	 * @return byte
	 */
	public byte readByte() {
		final int offsetNext = offset + 1;
		if (offsetNext > offsetEnd) {
			throw new IllegalStateException(String.format("Buffer too short for reading a byte: %s", toString()));
		}
		final byte b = buf[offset];
		offset = offsetNext;
		return b;
	}

	/**
	 * Reads an 16-bit signed-integer.
	 * @return short
	 */
	public short readShort() {
		final int offsetNext = offset + 2;
		if (offsetNext > offsetEnd) {
			throw new IllegalStateException(String.format("Buffer too short for reading a short: %s", toString()));
		}
		final short value = (short) ((buf[offset] << 8)
				+ (buf[offset + 1] & 0xff));
		offset = offsetNext;
		return value;
	}

	/**
	 * Reads an 32-bit signed-integer.
	 * @return int
	 */
	public int readInt() {
		final int offsetNext = offset + 4;
		if (offsetNext > offsetEnd) {
			throw new IllegalStateException(String.format("Buffer too short for reading an int: %s", toString()));
		}
		final int value = ((buf[offset] & 0xff) << 24)
				+ ((buf[offset + 1] & 0xff) << 16)
				+ ((buf[offset + 2] & 0xff) << 8)
				+ (buf[offset + 3] & 0xff);
		offset = offsetNext;
		return value;
	}

	/**
	 * Reads an 64-bit signed-long.
	 * @return long
	 */
	public long readLong() {
		final int offsetNext = offset + 8;
		if (offsetNext > offsetEnd) {
			throw new IllegalStateException(String.format("Buffer too short for reading a long: %s", toString()));
		}
		final long value = ((long)(buf[offset] & 0xff) << 56)
				+ ((long)(buf[offset + 1] & 0xff) << 48)
				+ ((long)(buf[offset + 2] & 0xff) << 40)
				+ ((long)(buf[offset + 3] & 0xff) << 32)
				+ ((long)(buf[offset + 4] & 0xff) << 24)
				+ ((buf[offset + 5] & 0xff) << 16)
				+ ((buf[offset + 6] & 0xff) << 8)
				+ (buf[offset + 7] & 0xff);
		offset = offsetNext;
		return value;
	}

	/**
	 * Reads an UTF-8-string with given length
	 * @param buf buffer
	 * @param offset offset in buffer
	 * @param numBytes number of UTF-8-bytes (given length)
	 * @return string
	 */
	public String readString(final int numBytes) {
		final int offsetNext = offset + numBytes;
		if (offsetNext > offsetEnd) {
			throw new IllegalStateException(String.format("Buffer too short for reading a string of length %d: %s", Integer.valueOf(numBytes), toString()));
		}
		final String value = new String(buf, offset, numBytes, StandardCharsets.UTF_8);
		offset = offsetNext;
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(50);
		sb.append("CommandBuffer:{");
		sb.append("start:").append(offsetStart);
		sb.append(", end:").append(offsetEnd);
		sb.append(", cur:").append(offset);
		sb.append(", msg:...");
		sb.append("}");
		return sb.toString();
	}
	
}
