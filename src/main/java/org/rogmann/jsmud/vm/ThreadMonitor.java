package org.rogmann.jsmud.vm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Implementation of a monitor on an object.
 */
public class ThreadMonitor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ThreadMonitor.class);
	
	/** monitor-object */
	private final Object objMonitor;

	/** thread which owns the monitor */
	private final Thread thread;
	
	/** latch for waiting */
	private final CountDownLatch latch;
	
	/** usage-counter in the owner-thread */
	private final AtomicInteger counter = new AtomicInteger(0);

	/** queue of waiting threads (used for wait/notify-support) */
	private final BlockingQueue<WaitingThread> waitingThreads = new LinkedBlockingQueue<>(); 

	/** internal class: thread which waits for notify/notifyAll */
	static class WaitingThread {
		final Thread thread;
		final CountDownLatch latch = new CountDownLatch(1);
		public WaitingThread(final Thread thread) {
			this.thread = thread;
		}
	}
	
	/**
	 * Constructor
	 * @param objMonitor monitor-object
	 * @param thread owner-thread
	 */
	public ThreadMonitor(final Object objMonitor, final Thread thread) {
		this.objMonitor = objMonitor;
		this.thread = thread;
		this.latch = new CountDownLatch(1);
	}

	/**
	 * Gets the monitor-object
	 * @return monitor-object
	 */
	public Object getObjMonitor() {
		return objMonitor;
	}
	
	/**
	 * Gets the thread which owns the monitor.
	 * @return owner-thread
	 */
	public Thread getThread() {
		return thread;
	}
	
	/**
	 * Waits for the monitor to be released
	 * @param timeout maximum time to wait
	 * @param unit time unit of the timeout argument
	 * @return <code>true</code> if the monitor has been released, <code>false</code> in case of a timeout
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
		boolean isFinished = latch.await(timeout, unit);
		return isFinished;
	}
	
	/**
	 * Increments the usage-counter.
	 * @return current counter
	 */
	public int incrementCounter() {
		return counter.incrementAndGet();
	}
	
	/**
	 * Decrements the usage-counter.
	 * @return current counter
	 */
	public int decrementCounter() {
		return counter.decrementAndGet();
	}
	
	/**
	 * Releases the monitor.
	 */
	public void releaseMonitor() {
		latch.countDown();
	}

	/**
	 * Adds a thread which called a {@link Object#wait()}-method.
	 * @param thread waiting thread
	 * @return latch to wait for notify
	 */
	public CountDownLatch addWaitThread(final Thread thread) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("Thread (%s) waits for monitor (%s) of thread (%s)",
					thread, objMonitor, this.thread));
		}
		final WaitingThread waitingThread = new WaitingThread(thread);
		waitingThreads.add(waitingThread);
		return waitingThread.latch;
	}

	/**
	 * Releases one waiting thread.
	 */
	public void sendNotify() {
		final WaitingThread waitingThread = waitingThreads.poll();
		if (waitingThread == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("No waiting Thread on monitor (%s) via (%s)",
						objMonitor, this.thread));
			}
		}
		else {
			// The waiting thread should continue now.
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Waiting Thread (%s) got notify on monitor (%s) via (%s)",
						waitingThread, objMonitor, this.thread));
			}
			waitingThread.latch.countDown();
		}
	}

	/**
	 * Releases all waiting threads.
	 */
	public void sendNotifyAll() {
		while (waitingThreads.peek() != null) {
			sendNotify();
		}
	}
}
