package org.rogmann.jsmud.vm;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of a monitor on an object.
 */
public class ThreadMonitor {
	
	/** monitor-object */
	private final Object objMonitor;

	/** thread which owns the monitor */
	private final Thread thread;
	
	/** latch for waiting */
	private final CountDownLatch latch;
	
	/** usage-counter in the owner-thread */
	private final AtomicInteger counter = new AtomicInteger(0);
	
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
}
