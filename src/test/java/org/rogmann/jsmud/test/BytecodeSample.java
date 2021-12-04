package org.rogmann.jsmud.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

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

	public static int exceptions(final String sInt) {
		int iNum;
		try {
			iNum = Integer.parseInt(sInt);
		}
		catch (NumberFormatException e) {
			throw new RuntimeException("Unexpected number: " + sInt, e);
		}
		return iNum;
	}

	public static void lambdas(final String a, final int b) {
		final BiFunction<Short, Long, String> fct = (s, l) -> {
			String p1 = String.format("(%d, %d)", Short.valueOf(s), Long.valueOf(l));
			String p2 = String.format("(%s, %d)", a, Integer.valueOf(b));
			return p1 + p2;
		};
		System.out.println(fct.apply(Short.valueOf("5"), Long.valueOf("7")));
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
