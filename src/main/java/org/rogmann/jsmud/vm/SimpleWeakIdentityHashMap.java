package org.rogmann.jsmud.vm;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of a synchronized simple weak IdentityHashMap.
 * 
 * <p>Simple means that get and set are supported only.
 * The key == null is not supported in this implementation.</p>
 * 
 * <p>See {@link java.util.WeakHashMap} and {@link java.util.IdentityHashMap} and its implementations (OpenJDK).
 * 
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class SimpleWeakIdentityHashMap<K,V> {

    /** default initial capacity -- MUST be a power of two. */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /** load factor */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

	/** lock */
	private final Lock lock = new ReentrantLock();

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    Entry<K,V>[] table;

    /**
     * The number of key-value mappings contained in this weak hash map.
     */
    private int size;

    /** The next size value at which to resize (capacity * load factor).
     */
    private int threshold;

	/** load factor */
	private final float loadFactor;

    /**
     * Reference queue for cleared WeakEntries
     */
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    /**
     * Constructs a new, empty map.
     */
    public SimpleWeakIdentityHashMap() {
    	final int capacity = DEFAULT_INITIAL_CAPACITY;
        table = newTable(capacity);
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        threshold = (int)(capacity * loadFactor);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     * 
     * @param key key
     * @return value or <code>null</code>
     */
    public V get(K key) {
        int h = hash(key);
        lock.tryLock();
        try {
	        final Entry<K,V>[] tab = getTable();
	        final int index = indexFor(h, tab.length);
	        Entry<K,V> e = tab[index];
	        while (e != null) {
	            if (e.hash == h && eq(key, e.get())) {
	                return e.value;
	            }
	            e = e.next;
	        }
	        return null;
        }
        finally {
        	lock.unlock();
        }
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V put(K key, V value) {
        int h = hash(key);
        lock.tryLock();
        try {
	        Entry<K,V>[] tab = getTable();
	        int i = indexFor(h, tab.length);
	
	        for (Entry<K,V> e = tab[i]; e != null; e = e.next) {
	            if (h == e.hash && eq(key, e.get())) {
	                V oldValue = e.value;
	                if (value != oldValue) {
	                    e.value = value;
	                }
	                return oldValue;
	            }
	        }
	
	        Entry<K,V> e = tab[i];
	        tab[i] = new Entry<>(key, value, queue, h, e);
	        if (++size >= threshold) {
	            resize(tab.length * 2);
	        }
	        return null;
        }
        finally {
        	lock.unlock();
        }
    }

    /**
     * Checks for equality of non-null reference x and y.
     * @param x key x
     * @param y key y
     * @return <code>true</code> if x == y (identity)
     */
    private static <K> boolean eq(final K x, final K y) {
        return x == y;
    }

    /**
     * Expunges stale entries from the table.
     */
    private void expungeStaleEntries() {
        for (Reference<? extends K> x; (x = queue.poll()) != null; ) {
            @SuppressWarnings("unchecked")
			final Entry<K,V> e = (Entry<K, V>) x;
            final int i = indexFor(e.hash, table.length);

            Entry<K,V> prev = table[i];
            Entry<K,V> p = prev;
            while (p != null) {
                Entry<K,V> next = p.next;
                if (p == e) {
                    if (prev == e) {
                        table[i] = next;
                    }
                    else {
                        prev.next = next;
                    }
                    // Must not null out e.next;
                    // stale entries may be in use by a HashIterator
                    e.value = null; // Help GC
                    size--;
                    break;
                }
                prev = p;
                p = next;
            }
        }
    }

    /**
     * Returns the table after first expunging stale entries.
     * @return table
     */
    private Entry<K,V>[] getTable() {
        expungeStaleEntries();
        return table;
    }

    /**
     * Returns a hash-code of the key based on System.identityHashCode.
     * @param k key
     * @return hash-code
     */
    final int hash(K k) {
        int h = System.identityHashCode(k);

        // Multiply by -127, and left-shift to use least bit as part of hash.
        return ((h << 1) - (h << 8));
    }

    /**
     * Returns index for hash code h.
     * @param h hash-code
     * @param length length of table
     * @return index
     */
    private static int indexFor(int h, int length) {
		return h & (length - 1);
    }

    private Entry<K,V>[] newTable(int n) {
        @SuppressWarnings("unchecked")
		final Entry<K,V>[] entries = (Entry<K,V>[]) new Entry<?,?>[n];
		return entries;
    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *        must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value
     *        is irrelevant).
     */
    void resize(int newCapacity) {
        Entry<K,V>[] oldTable = getTable();
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry<K,V>[] newTable = newTable(newCapacity);
        transfer(oldTable, newTable);
        table = newTable;

        /*
         * If ignoring null elements and processing ref queue caused massive
         * shrinkage, then restore old table.  This should be rare, but avoids
         * unbounded expansion of garbage-filled tables.
         */
        if (size >= threshold / 2) {
            threshold = (int)(newCapacity * loadFactor);
        } else {
            expungeStaleEntries();
            transfer(newTable, oldTable);
            table = oldTable;
        }
    }

    /**
     * Transfers all entries from source to destination tables.
     * @param src source tables
     * @param dest destination tables
     */
    private void transfer(Entry<K,V>[] src, Entry<K,V>[] dest) {
        for (int j = 0; j < src.length; ++j) {
            Entry<K,V> e = src[j];
            src[j] = null;
            while (e != null) {
                Entry<K,V> next = e.next;
                Object key = e.get();
                if (key == null) {
                    e.next = null;  // Help GC
                    e.value = null; //  "   "
                    size--;
                } else {
                    int i = indexFor(e.hash, dest.length);
                    e.next = dest[i];
                    dest[i] = e;
                }
                e = next;
            }
        }
    }

    /**
     * The entries in this hash table extend WeakReference, using its main ref
     * field as the key.
     * 
     * <p>The implementation uses reference-equality in place of object-equality when comparing keys.
     * It doesn't comply with the equals/hashCode-contract.</p>
     */
    private static class Entry<K,V> extends WeakReference<K> {
    	/** value */
        V value;
        /** hash-code */
        final int hash;
        /** next entry */
        Entry<K,V> next;

        /**
         * Creates new entry.
         */
        Entry(final K key, final V value, final ReferenceQueue<K> queue,
              int hash, Entry<K,V> next) {
            super(key, queue);
            this.value = value;
            this.hash  = hash;
            this.next  = next;
        }

        /**
         * Gets the key.
         * @return key
         */
		public K getKey() {
            return get();
        }

		/**
		 * Gets the value.
		 * @return value
		 */
		public V getValue() {
            return value;
        }

		/** {@inheritDoc} */
        @Override
		public String toString() {
            return getKey() + "=" + getValue();
        }
    }

}
