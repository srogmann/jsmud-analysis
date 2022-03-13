package org.rogmann.jsmud.vm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JUnit-tests of {@link SimpleWeakIdentityHashMap}.
 */
@SuppressWarnings("static-method")
class SimpleWeakIdentityHashMapTest {

	@Test
	void testSimple() {
		final SimpleWeakIdentityHashMap<String, Integer> map = new SimpleWeakIdentityHashMap<>();
		map.put("A", Integer.valueOf(1));
		map.put("B", Integer.valueOf(2));
		map.put("C", Integer.valueOf(3));
		map.put("D", Integer.valueOf(4));
		Assertions.assertEquals(Integer.valueOf(1), map.get("A"));
		Assertions.assertEquals(Integer.valueOf(2), map.get("B"));
		Assertions.assertEquals(Integer.valueOf(3), map.get("C"));
		Assertions.assertEquals(Integer.valueOf(4), map.get("D"));
		Assertions.assertEquals(null, map.get("E"));
	}

	@Test
	void testLargeMap() {
		final SimpleWeakIdentityHashMap<String, Integer> map = new SimpleWeakIdentityHashMap<>();
		int numObj = 65536;
		int numHashCols = 3;
		final String[][] keys = new String[numHashCols][numObj];
		for (int h = 0; h < numHashCols; h++) {
			final int range = numHashCols * 1000000;
			for (int i = 0; i < numObj; i++) {
				// We use new String to get numHashCols different instances with the same hash-code.
				final String key = new String(Integer.toString(i));
				keys[h][i] = key;
				map.put(key, Integer.valueOf(range + i));
			}
		}
		for (int h = 0; h < numHashCols; h++) {
			final int range = numHashCols * 1000000;
			for (int i = 0; i < numObj; i++) {
				final String key = keys[h][i];
				Assertions.assertEquals(Integer.valueOf(range + i), map.get(key),
						"h=" + h + ", i=" + i);
			}
		}
	}

}
