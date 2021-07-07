package org.rogmann.jsmud;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Uninitialized instance of a class, placed on the stack in case of a new class-instance.
 */
public class UninitializedInstance {

	/** Id-Counter */
	private static final AtomicLong COUNTER = new AtomicLong();

	/** type */
	private final Class<?> fType;
	
	/** Id */
	private final long fId;

	/**
	 * Constructor
	 * @param type type of the uninitialized class
	 */
	public UninitializedInstance(final Class<?> type) {
		fType = type;
		fId = COUNTER.incrementAndGet();
	}
	
	/**
	 * Gets the id of the instance.
	 * @return id
	 */
	public long getId() {
		return fId;
	}
	
	/**
	 * Gets the type of the uninitialized class.
	 * @return class
	 */
	public Class<?> getType() {
		return fType;
	}
	
	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(20);
		sb.append(getClass().getSimpleName());
		sb.append('{');
		sb.append(fId);
		sb.append(':');
		sb.append(fType);
		sb.append('}');
		return sb.toString();
	}
}
