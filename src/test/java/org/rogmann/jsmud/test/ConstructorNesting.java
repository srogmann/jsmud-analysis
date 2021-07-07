package org.rogmann.jsmud.test;

/**
 * Example of constructor-nesting.
 */
public class ConstructorNesting {
	
	private final int a;
	private final char b;
	private final boolean c;
	private int counter = 0;

	ConstructorNesting(int a, char b, boolean c) {
		this.a = a;
		this.b = b;
		this.c = c;
		counter = (counter * 10) + 1;
	}
	
	ConstructorNesting(int a, char b) {
		this(a, b, true);
		counter = (counter * 10) + 2;
	}
	
	public ConstructorNesting(int a) {
		this(a, 'E');
		counter = (counter * 10) + 1;
	}
	
	public static String test(int n) {
		ConstructorNesting cn = new ConstructorNesting(n);
		return cn.toString();
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(a);
		sb.append(b);
		sb.append(c);
		sb.append('#');
		sb.append(counter);
		return sb.toString();
	}
}
