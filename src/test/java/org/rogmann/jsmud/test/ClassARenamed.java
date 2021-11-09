package org.rogmann.jsmud.test;

import java.util.function.Supplier;

/**
 * Test-class to be used in a different class-loader.
 * This functions creates a supplier using ClassB.
 */
public class ClassARenamed implements Supplier<String> {

	@Override
	public String get() {
		final Supplier<String> supplier = () -> ClassBRenamed.getName();
		return supplier.get();
	}

}
