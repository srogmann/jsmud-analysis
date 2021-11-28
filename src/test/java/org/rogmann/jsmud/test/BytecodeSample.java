package org.rogmann.jsmud.test;

public class BytecodeSample {
	private String name;
	private int num;

	public static void main(final String[] args) {
		BytecodeSample sample = new BytecodeSample(args[0], 1);
		System.out.println("Name: " + sample.getName());
	}
	
	public BytecodeSample(final String name, final int num) {
		this.name = name;
		this.num = num;
	}

	public static void assignments() {
		int a = 5;
		int b = a + 3;
		int c = ++b;
		short d = (short) (7 + a);
		c += d;
		System.out.println(c);
	}

	public static void loops() {
		int sum = 0;
		for (int i = 0; i < 5; i++) {
			sum += i;
		}
		System.out.println("Sum: " + sum);
	}

	public String getName() {
		return name;
	}

	public int getNumSquare() {
		return num * num;
	}
}
