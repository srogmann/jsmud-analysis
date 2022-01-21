package org.rogmann.jsmud.dumps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Watches a stream and informs a listener about each packet transferred.
 */
public class ConnectionStreamProxy implements Runnable {

	/** input-stream */
	private final InputStream is;
	/** output-stream */
	private final OutputStream os;

	/** listener */
	private ConnectionStreamListener listener;

	/** stop-flag */
	private AtomicBoolean shouldStop;

	/** exception-listener */
	private final Consumer<IOException> consumerIoExc;

	/** statistics-consumer */
	private final BiConsumer<Long, Long> consumerStats;

	/** buffer */
	private final byte[] buf = new byte[65536];

	/**
	 * Constructor
	 * @param is input-stream
	 * @param os output-stream
	 * @param listener listener on stream
	 * @param shouldStop stop-flag
	 * @param consumerIoExc exception-consumer
	 * @param consumerStats statistics-consumer (number of packets, sum of sizes)
	 */
	ConnectionStreamProxy(final InputStream is, final OutputStream os,
			final ConnectionStreamListener listener,
			final AtomicBoolean shouldStop,
			final Consumer<IOException> consumerIoExc,
			final BiConsumer<Long, Long> consumerStats) {
		this.is = is;
		this.os = os;
		this.listener = listener;
		this.shouldStop = shouldStop;
		this.consumerIoExc = consumerIoExc;
		this.consumerStats = consumerStats;
	}

	/** {@inheritDoc} */
	@Override
	public void run() {
		long numPackets = 0;
		long sumSize = 0;
		while (!shouldStop.get()) {
			int len;
			try {
				len = is.read(buf);
			} catch (IOException e) {
				shouldStop.set(true);
				if (shouldStop.get() || e.getMessage().contains("Connection reset")) {
					break;
				}
				consumerIoExc.accept(new IOException(String.format("IO-error while reading at offset %d",
						Long.valueOf(sumSize)), e));
				break;
			}
			if (len <= 0) {
				break;
			}
			numPackets++;
			listener.addPacket(buf, 0, len);
			try {
				os.write(buf, 0, len);
			} catch (IOException e) {
				shouldStop.set(true);
				if (shouldStop.get()) {
					break;
				}
				consumerIoExc.accept(new IOException(String.format("IO-error while writing at offset %d",
						Long.valueOf(sumSize)), e));
				break;
			}
			sumSize += len;
		}
		consumerStats.accept(Long.valueOf(numPackets), Long.valueOf(sumSize));
	}

}
