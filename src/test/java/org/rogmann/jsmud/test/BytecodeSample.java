package org.rogmann.jsmud.test;

import java.util.ArrayList;
import java.util.List;

public class BytecodeSample {
	private String name;
	private int num;
	
	static enum JavaFeature {
		ASSIGNMENTS,
		ARRAYS;
	}

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

	public static int arrays() {
		final byte[] aBuf = { 0, 0x31, (byte) 0xe0 };
		final long[] aLong = new long[5];
		final int s = (aBuf[2] & 0xff) + (aLong.length << 2);
		return s;
	}

	public static void collections() {
		final List<String> names = new ArrayList<>();
		names.add("Java");
		names.add("Rust");
		int sum = 0;
		for (String name : names) {
			sum += name.length();
		}
		System.out.println("sum of lengths: " + sum);
	}

	public static void loops() {
		int sum = 0;
		for (int i = 0; i < 5; i++) {
			sum += i;
		}
		while (sum > 10) {
			sum -= 10;
		}
		System.out.println("Sum: " + sum);
	}

	public static String switchs(JavaFeature jf) {
		// lookupswitch
		//
		String comment;
		switch (jf.toString()) {
		case "ASSIGNMENTS": comment = "/* assignment */"; break;
		case "ARRAYS": comment = "/* arrays */"; break;
		default: comment = "/* ? */"; break;
		}

		// tableswitch
		//
		final String example;
		switch (jf) {
		case ASSIGNMENTS: example = "int a = 5;"; break;
		case ARRAYS: example = "byte[] b = { 1, 2, 3};"; break;
		default: example = "// nothing here"; break;
		}
		return example + ' ' + comment;
	}

	public String getName() {
		return name;
	}

	public int getNumSquare() {
		return num * num;
	}
}
