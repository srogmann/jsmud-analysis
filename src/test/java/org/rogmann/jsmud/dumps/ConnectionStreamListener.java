package org.rogmann.jsmud.dumps;

/**
 * Listener on a stream.
 */
public interface ConnectionStreamListener {

	/**
	 * Adds a block of bytes.
	 * @param buf buffer
	 * @param offset offset of block in buffer
	 * @param len length of block in buffer
	 */
	void addPacket(final byte[] buf, final int offset, final int len);

}
