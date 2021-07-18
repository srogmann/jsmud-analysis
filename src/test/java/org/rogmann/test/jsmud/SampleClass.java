package org.rogmann.test.jsmud;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Example class.
 */
public class SampleClass {
	
	/** sample-field */
	private int myField1;
	
	/**
	 * Entry
	 * @param args none
	 */
	public static void main(final String[] args) {
		// System.setProperty("java.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE", "true");
		// example8();
		SampleClass.example(3, 5);
	}
	
	public SampleClass(final int mf) {
		myField1 = mf;
	}
	
	public SampleClass(final int mf, boolean flag) {
		this(mf);
	}
	
	/**
	 * Computes the maximum of two values.
	 * @param a
	 * @param b
	 * @return
	 */
	public int max(final int a, final int b) {
		return (a <= b) ? b : a;
	}
	
	public int max(final int a, final int b, final int c) {
		return max(max(a, b), c);
	}
	
	public void exampleMax() {
		final PrintStream psOut = System.out;
		psOut.println("max 3, 90, -200: " + max(3, 90, -200));

		final int[] aInts = { 3, -4, 20, 8, 9};
		int m = 0;
		for (int i : aInts) {
			m = max(m, i);
		}
		myField1 = m;
		psOut.println("max aInts: " + myField1);
	}

	/**
	 * Prints the sum of the two arguments to stdout.
	 * @param a argument 1
	 * @param b argument 2
	 */
	public static void example(final int a, final int b) {
		final int c = a + b;

		// Ausflug?
		//example2(9, 16);
		long erg = example5(2);
		System.out.println(erg);
		
		System.out.println("c = " + c);
	}
	
	public static void example2(final int a, final int b) {
		final PrintStream psOut = System.out;
		psOut.println("a = " + a);
		psOut.println("b = " + b);
		final String name = "Frosch";
		try {
			psOut.println("Frosch-Teil: " + name.substring(a, b));
		} catch (StringIndexOutOfBoundsException e) {
			psOut.println("Fehler beim Substring: " + e.getMessage());
			e.printStackTrace();
		}
		psOut.println("Fertig! :-)");
	}
	
	public static void example3(final long a, final long b) {
		final PrintStream psOut = System.out;
		psOut.println("a = " + a);
		psOut.println("b = " + b);
		final long c = a * b;
		psOut.println("Produkt: " + c);
	}

	public long example4(final long a, final long b) {
		final PrintStream psOut = System.out;
		psOut.println("a = " + a);
		psOut.println("b = " + b);
		final long c = a * b;
		psOut.println("Produkt: " + c);
		return c;
	}
	
	public static long example5(int n) {
		long sum = example5b(0);
		for (int i = 0; i <= n; i++) {
			sum += i*i;
		}
		//System.out.println("Square-Sum: " + sum);
		return sum;
	}
	
	public static int example5b(int n) {
		return n;
	}

	public static long example6() {
		final int[] aInts = { 1, 3, 5, 7 };
		int sum = 0;
		for (int i : aInts) {
			sum += i;
		}
		System.out.println("Array-Sum: " + sum);
		return sum;
	}
	
	public static void example7() {
		final StringBuilder sb = new StringBuilder(100);
		
		for (int i = 0; i <= 6; i++) {
			switch (i) {
			case 2:
				sb.append("Zwei");
				break;
			case 3:
				sb.append("Drei");
				break;
			case 4:
				sb.append("Vier");
				break;
			case 5:
				sb.append("Fünf");
				break;
			case 6:
				sb.append("Ende");
				//$FALL-THROUGH$
			default:
				sb.append("Default");
				break;
			}
		}
		sb.append('/');
		for (int i = 0; i <= 9; i++) {
			switch (i) {
			case 2:
				sb.append("[2]");
				break;
			case 7:
				sb.append("[2]");
				break;
			default:
				sb.append(i);
				break;
			}
		}

		System.out.println("Ergebnis: " + sb);
	}

	public static void example8() {
		final List<String> list = new ArrayList<>();
		list.add("Berta");
		list.add("Frosch");
		list.add("Guntram");

		// Example Stream<T> filter(Predicate<? super T> predicate);:
		// .filter(n -> n.contains("a"))
		// Handle: java/lang/invoke/LambdaMetafactory.metafactory
		//             (Ljava/lang/invoke/MethodHandles$Lookup;
		//              Ljava/lang/String;Ljava/lang/invoke/MethodType;
		//              Ljava/lang/invoke/MethodType;
		//              Ljava/lang/invoke/MethodHandle;
		//              Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)
		// Args: [(Ljava/lang/Object;)Z,
		//        org/rogmann/classscan/test/SampleClass.lambda$0(Ljava/lang/String;)Z (6), (Ljava/lang/String;)Z]
		// Desc: ()Ljava/util/function/Predicate;
		//
		// caller: MethodHandle$Lookup with class org.rogmann.classscan.test.SampleClass
		// invokedName: "test"
		// invokedType: ()Predicate
		// samMethodType: (Object)boolean
		// implMethod: MethodHandle(String)boolean
		// instanciatedMethodType: (String)boolean
		//
		// java.lang.invoke.CallSite.makeSite(MethodHandle, String, MethodType, Object, Class<?>)
		// return linkCallSiteImpl(caller, bootstrapMethod, name, type,
        //     staticArguments, appendixResult);
        // caller: class org.rogmann.classscan.test.SampleClass
		// bootstrapMethod: MethodHandle(Lookup,String,MethodType,MethodType,MethodHandle,MethodType)CallSite
		// name: "test" 
		// type: ()Predicate
		// staticArguments: [(Object)boolean, MethodHandle(String)boolean, (String)boolean]
		// appendixResults: [null]
		OptionalInt maxLen = list.stream().filter(
				n ->
				n.contains("a")
			)
			.mapToInt(n -> n.length())
			.max();
		if (maxLen.isPresent()) {
			System.out.println("Maximale Länge: " + maxLen.getAsInt());
		}
	}

	/**
	 * Gets the sample-field.
	 * @return field
	 */
	public int getMyField1() {
		return myField1;
	}

}
