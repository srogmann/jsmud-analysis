package org.rogmann.jsmud.vm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Implementation of a monitor on an object.
 */
public class ThreadMonitor {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(ThreadMonitor.class);

	/** object-monitor */
	private final ObjectMonitor monitor; 

	/** monitor-object */
	private final Object objMonitor;

	/** thread which owns the monitor currently */
	private final AtomicReference<Thread> threadOwner = new AtomicReference<>();
	
	/** latch for waiting */
	private final CountDownLatch latch;
	
	/** usage-counter in the owner-thread */
	private final AtomicInteger counter = new AtomicInteger(0);

	/** queue of threads waiting for gaining ownership */
	private final BlockingQueue<Thread> contendingThreads = new LinkedBlockingQueue<>();

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
	public ThreadMonitor(final ObjectMonitor monitor, final Object objMonitor, final Thread thread) {
		this.monitor = monitor;
		this.objMonitor = objMonitor;
		threadOwner.set(thread);
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
		return threadOwner.get();
	}

	/**
	 * Adds a thread waiting for gaining the monitor.
	 * @param thread thread
	 */
	public void addContendingThread(Thread thread) {
		contendingThreads.add(thread);
	}

	/**
	 * Removes a thread waiting for gaining the monitor.
	 * @param thread thread
	 */
	public void removeContendingThread(Thread thread) {
		contendingThreads.remove(thread);
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
			LOG.debug(String.format("Thread (%s) waits for monitor (%s) of (object (%s) of thread (%s)",
					thread, this, objMonitor, threadOwner.get()));
		}
		final WaitingThread waitingThread = new WaitingThread(thread);
		waitingThreads.add(waitingThread);
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("addWaitThread: objMonitor=%s, threadMonitor=%s, waitingThreads=%s",
					objMonitor, this, waitingThreads));
		}
		return waitingThread.latch;
	}

	/**
	 * Checks if there are waiting threads.
	 * @return non-empty-flag
	 */
	public boolean hasWaitingThreads() {
		return waitingThreads.peek() != null || contendingThreads.peek() != null;
	}

	/**
	 * Releases one waiting thread.
	 */
	public void sendNotify() {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("sendNotify: objMonitor=%s, threadMonitor=%s, waitingThreads=%s",
					objMonitor, this, waitingThreads));
		}
		final WaitingThread waitingThread = waitingThreads.poll();
		if (waitingThread == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("No waiting Thread on monitor (%s) of (%s) via (%s)",
						this, objMonitor, threadOwner.get()));
			}
		}
		else {
			// The waiting thread should continue now.
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Waiting Thread (%s) got notify on monitor (%s) via (%s)",
						waitingThread, objMonitor, threadOwner.get()));
			}
			monitor.exitMonitor(objMonitor);
			waitingThread.latch.countDown();
			monitor.enterMonitor(objMonitor);
		}
	}

	/**
	 * Releases all waiting threads.
	 */
	public void sendNotifyAll() {
		sendNotify();
		while (waitingThreads.peek() != null) {
			sendNotify();
		}
	}

	/**
	 * Try to gain ownership of the monitor.
	 * The entry count has to be zero to gain ownership.
	 * If successful the entry count will be set to 1.
	 * @param currentThread thread which wants to obtain ownership
	 * @return <code>true</code> if the thread gained ownership
	 */
	public boolean gainOwnership(Thread currentThread) {
		final boolean isGainedOwnership = counter.compareAndSet(0, 1);
		if (isGainedOwnership) {
			threadOwner.set(currentThread);
		}
		return isGainedOwnership;
	}

}
