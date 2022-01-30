package org.rogmann.jsmud.test2;

public class BuilderInTest2 {

	/**
	 * Build an instance of ClassInTest2 via {@link Class#newInstance()}.
	 * @return instance
	 */
	public static ClassInTest2 build() {
		final Class<ClassInTest2> clazz = ClassInTest2.class;
		try {
			final ClassInTest2 obj = clazz.newInstance();
			return obj;
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException("Can't build class " + clazz, e);
		}
	}
}
