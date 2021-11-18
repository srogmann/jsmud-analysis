package org.rogmann.jsmud.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.rogmann.jsmud.gen.JsmudGeneratedClasses;

/**
 * Some simple JVM-tests.
 */
public class JvmTests {
	/** list of executed tests */
	private final List<String> executedTests = new ArrayList<>();
	
	/** internal short: 20000 */
	private static final short SHORT_20000 = Short.parseShort("20000");

	/** supplier with implementation in a different class-loader */
	static Supplier<String> SUPPLIER_CL1;
	/** supplier with implementation in a different class-loader */
	static Supplier<String> SUPPLIER_CL2;

	/** test-map */
	private final Map<String, Integer> testMap = new HashMap<>();

	/** int-instance */
	final int fIntField;
	
	public static void main(String[] args) {
		final JvmTests jvmTests = new JvmTests();
		jvmTests.tests();
	}

	/** Executes a simple test-suite */
	public void tests() {
//		testsBoolean();
//		testsByte();
//		testsChar();
//		testsShort();
//		testsLong();
//		testsFloat();
//		testsDouble();
//		testsArray();
//		testsArrayIndex();
//		testsExceptionHandling();
//		testsExceptionHandlingFinally();
//		testsInvokespecial();
//		testsInterfaceDefaultSuper();
//		testsRegexp();
//		testsSwitch();
//		testsLambda();
//		testsLambdaCallSiteConstructor();
//		testsLambdaOnArray();
//		testsLambdaBiConsumer();
//		testsLambdaClassMethodReferences();
//		testsLambdaObjectMethodReferences();
//		testsLambdaInterfaceMethodReferences();
//		testsLambdaNonStatic();
//		testsLambdaInterface();
//		testsLambdaSpecialAndThen();
//		testsLambdaStreamCollectOnly();
//		testsLambdaFunctionAndThen();
//		testsLambdaPrimitiveTypes();
//		testsLambdaStreams();
//		testsLambdaStreams2();
//		testsLambdaStreamsThis();
//		testsLambdaBiFunctionAndThen();
//		testsLambdaCollectingAndThen();
//		testsLambdaMultipleFunctions();
//		testsLambdaReuse();
//		testsLambdaCommonSubclass();
//		testsLambdaReturnPrivate();
//		testsLambdaAndSecurity();
//		testsLambdaThreadLocal();
//		testsMethodChoosing();
//		testsMethodRef();
//		testsMethodArrayArgs();
//		testsSyntheticMethod();
//		testsFields();
//		testsConstructorRef();
//		testsCatchException();
//		testsJavaTime();
//		testsProxy();
//		testsProxyThisS0();
//		testsProxySuper();
//		testsProxyViaReflection();
//		testsProxyViaReflectionMethod();
//		testsProxyPublicInterface();
//		testsProxyPublicInterfaceViaReflection();
//		testsProxyPublicInterfaceViaReflectionImpl();
//		testsProxyExecuteInternal();
//		testsReflection();
//		testsReflectionOnInterface();
//		testReflectionDeclaredConstructors();
//		testsClassForName();
//		testsReflectionAnnotation();
//		testsAccessController();
		System.out.println("Executed tests: " + executedTests);
	}

	/**
	 * Constructor
	 */
	public JvmTests() {
		this(-1);
	}

	/**
	 * Constructor
	 * @param iValue int-field-value
	 */
	public JvmTests(final int iValue) {
		this.fIntField = iValue;
	}

	/** boolean-tests */
	@JsmudTest
	public void testsBoolean() {
		final boolean[] aBoolean = new boolean[2];
		aBoolean[0] = true;
		aBoolean[1] = false;
		assertTrue("z0", aBoolean[0]);
		assertTrue("z1", !aBoolean[1]);
	}

	/** byte-tests */
	@JsmudTest
	public void testsByte() {
		final byte[] aByte = new byte[4];
		aByte[0] = (byte) 0x00;
		aByte[1] = (byte) 1;
		aByte[2] = (byte) -0x80;
		aByte[3] = (byte) -0x01;
		assertTrue("b0", aByte[0] == (byte) 0x00);
		assertTrue("b1", aByte[1] == (byte) 0x01);
		assertTrue("b2", aByte[2] == (byte) -0x80);
		assertTrue("b3", aByte[3] == (byte) -0x01);
		assertTrue("i0", aByte[0] == 0);
		assertTrue("i0", aByte[1] == 1);
		assertTrue("i0", aByte[2] == -128);
		assertTrue("i0", aByte[3] == -1);
		
		final byte[] bClone = aByte.clone();
		assertTrue("b1.clonse", bClone[1] == (byte) 0x01);
	}

	/** char-tests */
	@JsmudTest
	public void testsChar() {
		final char[] aChar = {
				' ', '\u0000', 'ü', '老' 
		};
		assertTrue("c0", aChar[0] == '\u0020');
		assertTrue("c1", aChar[1] == '\u0000');
		assertTrue("c2", aChar[2] == '\u00fc');
		assertTrue("c3", aChar[3] == '\u8001');
		assertTrue("i0", aChar[0] == 0x20);
		assertTrue("i1", aChar[1] == 0x0);
		assertTrue("i2", aChar[2] == 0xfc);
		assertTrue("i3", aChar[3] == 0x8001);
		final char cXor = (char) (aChar[0] ^ aChar[2]);
		assertTrue("cXor", cXor == '\u00dc');

		final short[] aShort = { (short) 0x20, (short) 0x00, (short) 0xf6, (short) 0x8001 };
		final char cLao = (char) aShort[3]; // SALOAD, I2C
		assertEquals("s2c/SALOAD", Character.valueOf(aChar[3]), Character.valueOf(cLao));

		final char[][][] aDim3 = new char[1][2][2];
		aDim3[0][0][0] = aChar[0];
		aDim3[0][0][1] = aChar[1];
		aDim3[0][1][0] = aChar[2];
		aDim3[0][1][1] = aChar[3];
		final Object oDi32 = aDim3.clone();
		final char[][][] aDim3Cast = (char[][][]) oDi32; // CHECKCAST
		assertEquals("cast-[[[C", Character.valueOf('ü'), Character.valueOf(aDim3Cast[0][1][0]));
	}

	@JsmudTest
	public void testsShort() {
		final short a = (short) 1;
		final short b = Short.parseShort("-1");
		final int c = doubleShort(SHORT_20000);
		assertEquals("short a + b", Short.valueOf((short) 0), Short.valueOf((short) (a + b)));
		assertEquals("short b", Short.valueOf((short) -1), Short.valueOf(b));
		assertEquals("short 2 * b", Short.valueOf((short) -2), Short.valueOf(doubleShort(b)));
		assertEquals("short 40000 - 65536", Integer.valueOf(-25536), Integer.valueOf(c));
		
		final short[] aShorts = { a, b, SHORT_20000, 0 }; // SASTORE
		aShorts[3] = aShorts[1];
		assertEquals("short[3]", Short.valueOf((short) -1), Short.valueOf(aShorts[3]));

		final int iShort_minus1 = Integer.parseInt("65535");
		final short sMinus1 = (short) iShort_minus1; // I2S
		final int iSum = sMinus1 + (-1);
		assertEquals("short ((short)65535)-1", Integer.valueOf((short) -2), Integer.valueOf(iSum));
		final int iMinus1 = sMinus1;
		assertEquals("short (int)(short)-1)", Integer.valueOf(-1), Integer.valueOf(iMinus1));
	}

	static short doubleShort(short a) {
		return (short) (2 * a);
	}

	@JsmudTest
	public void testsLong() {
		final long l = Long.parseLong("16777216");
		final long m = 10L;
		final long n = 13L;
		assertEquals("l << 16", Long.valueOf(1099511627776L), Long.valueOf(l << 16));
		final Long lSHR4Expected = Long.valueOf(1048576L);
		assertEquals("l >> 4", lSHR4Expected, Long.valueOf(l >> 4));
		assertEquals("l >>> 4", lSHR4Expected, Long.valueOf(l >>> 4));
		assertEquals("l | m", Long.valueOf(16777226L), Long.valueOf(l | m));
		assertEquals("m ^ n", Long.valueOf(7L), Long.valueOf(m ^ n));
		assertEquals("n % m", Long.valueOf(3L), Long.valueOf(n % m));
		
		final Long val4096Expected = Long.valueOf(4096L);
		assertEquals("long-test 4096", val4096Expected, Long.valueOf(testsLong2(6L, 9L, 0L, 4L)));
	}
	
	/**
	 * 
	 * @param a first long
	 * @param b second long
	 * @param c third long
	 * @param d forth long
	 * @return result
	 */
	static long testsLong2(final long a, final long b, final long c, final long d) {
		return a + 10 * b + 100 * c + 1000 * d;
	}
	
	@JsmudTest
	public void testsFloat() {
		final float a = Float.parseFloat("1357.5");
		final float b = 10.0f;
		assertEquals("a % b", Float.valueOf(7.5f), Float.valueOf(a % b));
		assertTrue("a > b", a > b);
		assertTrue("b < a", b < a);
	}

	@JsmudTest
	public void testsDouble() {
		final double a = Double.parseDouble("12345.1122334455");
		final double b = 10.0d;
		assertEquals("a % b", Double.valueOf(5.1122334455d), Double.valueOf(Math.round((a % b) * 10000000000L) / 10000000000d));
		assertTrue("a > b", a > b);
		assertTrue("b < a", b < a);
	}

	@SuppressWarnings("static-method")
	public int mult(int a, long b) {
		return a * (int) b;
	}

	@JsmudTest
	public void testsArray() {
		// ANEWARRAY int
		final int[] i1 = new int[1];
		// ANEWARRAY class "[I"
		final int[][] i2 = new int[2][];
		// ANEWARRAY class "[[I"
		final int[][][] i3 = new int[3][][];
		assertEquals("array i1", Integer.valueOf(1), Integer.valueOf(i1.length));
		assertEquals("array i2", Integer.valueOf(2), Integer.valueOf(i2.length));
		assertEquals("array i3", Integer.valueOf(3), Integer.valueOf(i3.length));
		i1[0] = 256;
		i2[1] = i1;
		i3[2] = i2;
		assertEquals("array i3.i2.i1", Integer.valueOf(256), Integer.valueOf(i3[2][1][0]));

		// MULTIANEWARRAY 3, class "[[[J"
		final long[][][] a123 = new long[1][2][3];
		a123[0][0][0] = 512;
		a123[0][1][2] = 1024;
		assertEquals("array a123.000", Long.valueOf(512), Long.valueOf(a123[0][0][0]));
		assertEquals("array a123.012", Long.valueOf(1024), Long.valueOf(a123[0][1][2]));
	}

	@JsmudTest
	public void testsArrayIndex() {
		final int[] aOrder = { 0, 1, 1, 1, 1, 1, 1, 28 };
		final int dim = 8;
		try {
			final int order = aOrder[dim];
			assertTrue("not reached", order == -1);
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue("AIOOBE-int", true);
		}

		final short[] aShort = { (short) 6, (short) 2 };
		try {
			final short s = aShort[dim];
			assertTrue("not reached", s == -1);
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue("AIOOBE-short", true);
		}

	}

	@JsmudTest
	public void testsExceptionHandling() {
		Method method = null;
		boolean hasException;
		try {
			method = searchMethod(WorkExample.class, "notExistent");
			hasException = false;
		}
		catch (NoSuchMethodException e) {
			hasException = true;
		}
		assertTrue("ExceptionHandling-1", hasException);

		try {
			method = searchMethod(WorkExample.class, "getMethod");
			hasException = false;
		}
		catch (NoSuchMethodException e) {
			hasException = true;
		}
		assertTrue("ExceptionHandling-2a", !hasException);
		if (method != null) {
			assertEquals("ExceptionHandling-2b", "getMethod", method.getName());
		}

		final String s = "I'm a string.";
		try {
			checkcastInteger(s);
		} catch (ClassCastException e) {
			assertTrue("exception handling: CHECKCAST ok", true);
		}
	}

	public static Integer checkcastInteger(final Object o) {
		return (Integer) o;
	}
	
	@JsmudTest
	public void testsExceptionHandlingFinally() {
		String s = "i";
		try {
			try {
				s += "-t";
				Integer.parseInt("Frosch");
			}
			catch (NumberFormatException e) {
				s += "-nfe";
				throw e;
			}
			catch (RuntimeException e) {
				s += "-rte";
				throw e;
			}
			catch (Exception e) {
				s += "-e";
				throw e;
			}
			finally {
				s += "-fin";
			}
		}
		catch (Exception e) {
			s += "-outerExc";
		}
		assertEquals("EHFinally", "i-t-nfe-fin-outerExc", s);
	}

	private static Method searchMethod(Class<?> clazz, String name) throws NoSuchMethodException {
		return clazz.getDeclaredMethod(name);
	}

	@JsmudTest
	public void testsInvokespecial() {
		InvokespecialImpl impl = new InvokespecialImpl();
		assertEquals("Invokespecial", "Super", impl.executeGetName());
	}

	static class InvokespecialSuper {
		@SuppressWarnings("static-method")
		private String getName() {
			return "Super";
		}
		String executeGetName() {
			return getName();
		}
	}

	static class InvokespecialImpl extends InvokespecialSuper {
		@SuppressWarnings("static-method")
		String getName() {
			return "Impl";
		}
	}

	@JsmudTest
	public void testsInterfaceDefaultSuper() {
		// DefInterfaceImpl uses a static super-default-call in its implementation.
		final DefInterface defIntf = new DefInterfaceImpl();
		final String s = defIntf.addSuffix("A-");
		assertEquals("Interface-super", "ImplDefS", s);

		final String name = defIntf.getName();
		assertEquals("Interface-super2", "ImplDefS2B-Child", name);
	}

	@JsmudTest
	public void testsRegexp() {
		final Pattern pAlpha = Pattern.compile("[A-Za-z]+");
		assertTrue("Pattern-a1", pAlpha.matcher("Carrot").matches());
		assertTrue("Pattern-a2", !pAlpha.matcher("Möhre").matches());
		
		final Matcher mFind = pAlpha.matcher("\\_oOo_/");
		assertTrue("Pattern-f1", mFind.find());
		assertEquals("Pattern-f2", "oOo", mFind.group());

		final Pattern pExtract = Pattern.compile(".*\\((.*)\\).*");
		final Matcher mExtract = pExtract.matcher("exp(iπ)?");
		assertTrue("Pattern-e1", mExtract.matches());
		assertEquals("Pattern-e2", "iπ", mExtract.group(1));

		final Pattern pMagic = Pattern.compile("(?i)(.*(magic|number):? ?)\\d+");
		final Matcher mMagic = pMagic.matcher("There is a magic numbEr 691");
		assertTrue("Pattern-m1", mMagic.matches());
		assertEquals("Pattern-m2", "numbEr", mMagic.group(2));
	}

	@JsmudTest
	public void testsSwitch() {
		final String a = "e";
		final String b = "2";
		final String c = a.concat(b);
		int result = 0;
		switch (c) {
			case "a1": result = 1; break;
			case "f3": result = 2; break;
			case "e2": result = 3; break;
			default: result = 4; break;
		}
		assertEquals("switch(string)", Integer.valueOf(3), Integer.valueOf(result));
	}
	
	/** simple lambda-tests */
	@JsmudTest
	public void testsLambda() {
		//linkCallSite org.rogmann.jsmud.test.JvmTests java.lang.invoke.LambdaMetafactory.metafactory(Lookup,String,MethodType,MethodType,MethodHandle,MethodType)CallSite/invokeStatic apply()IntFunction/[(int)Object, MethodHandle(int)Integer, (int)Integer]
		//linkMethod java.lang.invoke.MethodHandle.invoke()Object/5
		//linkMethod => java.lang.invoke.LambdaForm$MH/980546781.invoke_000_MT(Object,Object)Object/invokeStatic + ()Object
		//linkMethod java.lang.invoke.MethodHandle.invoke(Lookup,String,MethodType,Object,Object,Object)Object/5
		//linkMethod => java.lang.invoke.LambdaForm$MH/1804094807.invoke_001_MT(Object,Object,Object,Object,Object,Object,Object,Object)Object/invokeStatic + (Lookup,String,MethodType,Object,Object,Object)Object
		//linkCallSite => java.lang.invoke.LambdaForm$MH/471910020.linkToTargetMethod_000(Object)Object/invokeStatic + MethodHandle()IntFunction
		//
		// BSM:
		// bsm: java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)
		// bsm.Args: [(I)Ljava/lang/Object;, org/rogmann/jsmud/test/JvmTests.lambda$0(I)Ljava/lang/Integer; (6), (I)Ljava/lang/Integer;]
		//   Type: org.objectweb.asm.Type, (I)Ljava/lang/Object;
		//   Handle: org.objectweb.asm.Handle, org/rogmann/jsmud/test/JvmTests.lambda$0(I)Ljava/lang/Integer;
		//   Type: (I)Ljava/lang/Integer;
		// desc: ()Ljava/util/function/IntFunction;

		mult(3, 5000000000L);
		final IntFunction<Integer> iFkt = (i -> Integer.valueOf(i * i)); // bsmTag H_INVOKESTATIC
		assertEquals("iFkt 3", Integer.valueOf(9), iFkt.apply(3));
		assertEquals("iFkt 25", Integer.valueOf(25), iFkt.apply(5));

		// Check if a call of Object#getClass() is successful.
		final String packageGen = JsmudGeneratedClasses.class.getName().replaceFirst("[.][^.]*$", "");
		final String iFctClass = iFkt.getClass().getName();
		assertTrue("iFkt.class", iFctClass.contains("$Lambda") 
				|| iFctClass.contains("$jsmudLambda")
				|| iFctClass.contains("$Proxy")
				|| iFctClass.startsWith(packageGen));
	}

	@JsmudTest
	public void testsLambdaCallSiteConstructor() {
		final String s = "test";
		final boolean b = s.length() > 0;
		final char c = s.charAt(0);
		final short s16 = (short) s.charAt(1);
		final Supplier<String> supplier = () -> String.format("%s: %s %c %d",
				s, Boolean.toString(b), Character.valueOf(c), Short.valueOf(s16));
		assertEquals("LambdaCSConstructor", "test: true t 101", supplier.get());
	}

	@JsmudTest
	public void testsLambdaOnArray() {
		final Function<int[], Integer> intArrLen = (a -> Integer.valueOf(a.length));
		
		final int[] ints = { 5, 6, 7};
		assertEquals("testLambdaOnArray1", Integer.valueOf(3), intArrLen.apply(ints));

		final IntConsumer intsClearer = i -> {ints[i] = 0;};
		intsClearer.accept(1);
		assertEquals("testLambdaOnArray2", Integer.valueOf(0), Integer.valueOf(ints[1]));
	}

	@JsmudTest
	public void testsLambdaBiConsumer() {
		// The call-site has to ignore the return-type StringJoiner of StringJoiner::add.
		final BiConsumer<StringJoiner, CharSequence> accumulator = StringJoiner::add;
		final StringJoiner joiner = new StringJoiner(";");
		accumulator.accept(joiner, "a");
		accumulator.accept(joiner, "b");
		accumulator.accept(joiner, "c");
		assertEquals("testsLambdaBiConsumer", "a;b;c", joiner.toString());
	}

	/**
	 * Example of a lambda-function with method-references on a class.
	 */
	@JsmudTest
	public void testsLambdaClassMethodReferences() {
		// ClassName::instanceMethod, bsm_tag H_INVOKEVIRTUAL.
		final Function<String, String> sFkt = String::toUpperCase; // bsm_tag H_INVOKEVIRTUAL.
		assertEquals("String::toUpperCase", "UPPER", sFkt.apply("uPPeR"));
		
		// ClassName::staticMethod, bsm_tag H_INVOKESTATIC.
		final IntFunction<String> iFkt = String::valueOf;
		assertEquals("String::valueOf", "42", iFkt.apply(42));
	}

	/**
	 * Example of a lambda-function with method-references on an object-instance.
	 */
	@JsmudTest
	public void testsLambdaObjectMethodReferences() {
		// instance::instanceMethod, bsm_tag H_INVOKEVIRTUAL.
		final String s = "catalog";
		final TestMethodReference tac = new TestMethodReference(s.substring(0, 3));
		final Supplier<String> supplier = tac::getValue;
		assertEquals("obj::method", "cat", supplier.get());
		
		final Supplier<String> supplierDoubled = supplier::get;
		assertEquals("obj::method::method", "cat", supplierDoubled.get());

		final Supplier<String> supplierTriple = supplierDoubled::get;
		assertEquals("obj::method::method::method", "cat", supplierTriple.get());
	}

	/**
	 * Example of a lambda-function with method-references on an interface.
	 */
	@JsmudTest
	public void testsLambdaInterfaceMethodReferences() {
		// instance::instanceMethod, bsm_tag H_INVOKEVIRTUAL.
		final SimpleConcat sc = (s -> s.concat("-cat"));
		final Function<String, String> fct = sc::add;
		assertEquals("intf::method", "meow-cat", fct.apply("meow"));
	}

	static interface SimpleConcat {
		String add(String s);
	}

	/**
	 * Example of a lambda-function with five arguments.
	 *
	 * @param <A> Type of first argument
	 * @param <B> Type of second argument
	 * @param <C> Type of third argument
	 * @param <D> Type of forth argument
	 * @param <E> Type of fifth argument
	 * @param <R> Type of result
	 */
	@FunctionalInterface
	public interface QuintFunction<A, B, C, D, E, R> {
		R apply(A a, B b, C c, D d, E e);
	}

	/** lambda-tests with BiFunction et al. */
	@JsmudTest
	public void testsLambdaMultipleFunctions() {
		final Function <Integer, String> fct = i -> "i:" + i;
		assertEquals("Fct", "i:-37", fct.apply(Integer.valueOf(-37)));
		assertEquals("Fct", "i:42", fct.apply(Integer.valueOf(42)));
		
		final BiFunction<Long, Integer, String> biFct = (l, i) -> String.format("%d:%d", l, i);
		assertEquals("biFct", "150111222333:-53", biFct.apply(Long.valueOf(150111222333L),  Integer.valueOf(-53)));
		
		final QuintFunction<Integer, Integer, Integer, Integer, Integer, String> quintFct =
				(a, b, c, d, e) -> String.format("%d:%d:%d:%d:%d", a, b, c, d, e);
		assertEquals("quintFct", "1:0:100:-100:16777216", quintFct.apply(Integer.valueOf(1), Integer.valueOf(0),
				Integer.valueOf(100), Integer.valueOf(-100), Integer.valueOf(16777216)));
	}

	/** lambda-tests with non-static lambda-function */
	@JsmudTest
	public void testsLambdaNonStatic() {
		this.testMap.put("A", Integer.valueOf(11));
		final String key = "A";
		Predicate<Set<String>> predicate = set -> this.testMap.containsKey(key); // bsm_tag H_INVOKESPECIAL
		assertTrue("Key present", predicate.test(Collections.emptySet()));
	}

	@JsmudTest
	public void testsLambdaInterface() {
		final Consumer<Map<String, Integer>> consumer = Map::clear; // bsm_tag H_INVOKEINTERFACE.
		testMap.put("a1", Integer.valueOf(1));
		consumer.accept(testMap);
		assertEquals("Test lambda-interface", Integer.valueOf(0), Integer.valueOf(testMap.size()));
	}

	public interface FunctionWithDefault {
		String addPrefix(int i);
		default IntFunction<String> andThen(String suffix) {
			return i -> addPrefix(i) + suffix;
		}
	}

	@JsmudTest
	public void testsLambdaSpecialAndThen() {
		final FunctionWithDefault prefixA = (i -> "A" + i);
		final IntFunction<String> prefixASuffixB = prefixA.andThen("B"); // bsm_tag H_INVOKESPECIAL (interface)
		assertEquals("Test functionWithDefault-andThen", "A3B", prefixASuffixB.apply(3));
	}

	@JsmudTest
	public void testsLambdaFunctionAndThen() {
		final Function<Long, String> fctPrefixA = (i -> "A" + i);
		final Function<String, Integer> fctLen = (s -> Integer.valueOf(s.length()));
		final Function<Long, Integer> fctPreLen = fctPrefixA.andThen(fctLen);
		final Integer lenA12345678901234 =  fctPreLen.apply(Long.valueOf(12345678901234L));
		assertEquals("Test functionAndThen", Integer.valueOf(15), lenA12345678901234);
	}

	@FunctionalInterface
	public interface PrimitiveTypesFunction {
		String apply(boolean b, char c, short s, int i, long j, float f, double d);
	}

	@JsmudTest
	public void testsLambdaPrimitiveTypes() {
		final PrimitiveTypesFunction function = (b, c, s, i, j, f, d) ->
			String.format(Locale.US, "b:%s,c:%s,s:%d,i:%d,j:%d,f:%.3f,d:%.3f",
					Boolean.toString(b), Character.valueOf(c),
					Short.valueOf(s), Integer.valueOf(i), Long.valueOf(j),
					Float.valueOf(f), Double.valueOf(d));
		final Callable<String> callable = new Callable<String>() {
			@Override
			public String call() throws Exception {
				return function.apply(true, '@',
						(short) -37, 691, 123456789012l, 1.1f, 1.23);
			}
			
		};
		final String result = NativeExecutor.executeCallable(callable);
		assertEquals("Test lambda-primitive-types", "b:true,c:@,s:-37,i:691,j:123456789012,f:1.100,d:1.230",
				result);
	}

	/** lambda-stream-tests with collect only*/
	@JsmudTest
	public void testsLambdaStreamCollectOnly() {
		final List<String> list = Arrays.asList("d1", "d2", "d3");
		final String listStreamed = list.stream().collect(Collectors.joining("-"));
		assertEquals("Test listStreamCollectOnly", "d1-d2-d3", listStreamed);
	}

	/** lambda-tests with streams */
	@JsmudTest
	public void testsLambdaStreams() {
		final int[] aValues = { 1, -2, 3, -5, 8 };
		final OptionalInt max = Arrays.stream(aValues).map(i -> -i).max();
		assertTrue("max != null", max.isPresent());
		assertEquals("max == 5", Integer.valueOf(5), Integer.valueOf(max.getAsInt()));
		
		final List<String> list = Arrays.asList("a1", "a2", "b1", "c2", "c1", "d3", "d1", "d2");
		final String listStreamed = list.stream()
			.filter(s -> s.startsWith("d"))
			.map(String::toUpperCase)
			.sorted()
			.collect(Collectors.joining("-"));
		assertEquals("Test listStreamed", "D1-D2-D3", listStreamed);
	}

	@JsmudTest
	public void testsLambdaStreams2() {
	    final String[] chess = { "e2-e4", "e7-e5", "g1-f3", "b8-c6" };
	    final String result = Arrays.stream(chess)
	            .map(move -> move.substring(0, 2))
	            .sorted()
	            .collect(Collectors.joining(","));
	    assertEquals("Test lambdaStreams2", "b8,e2,e7,g1", result);
	}

	/** lambda-tests with streams: filter with instance */
	@JsmudTest
	public void testsLambdaStreamsThis() {
		this.testMap.put("a2", Integer.valueOf(11));

		final List<String> list = Arrays.asList("a1", "a2", "b1");
		final String listStreamed = list.stream().filter(s -> !this.testMap.containsKey(s)).collect(Collectors.joining("-"));
		assertEquals("Test lambdaStreamsThis", "a1-b1", listStreamed);
	}

	/** lambda-tests with function and Collectors.andThan(...) */
	@JsmudTest
	public void testsLambdaBiFunctionAndThen() {
		final Map<String, List<String>> result = new HashMap<>(1);
		final List<String> list = new ArrayList<>();
		list.add("A1a");
		list.add("A1b");
		list.add("A1b");
		list.add("A1b");
		list.add("A1c");
		result.put("A1", list);

		final BiFunction<String, List<String>, List<String>> fctReplace = ((key, vList) ->
			vList.stream().distinct().collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
		result.replaceAll(fctReplace);
		assertEquals("Test Bifunction/AndThen", "[A1a, A1b, A1c]", result.get("A1").toString());
	}

	/** lambda-tests with function and andThen */
	@JsmudTest
	public void testsLambdaCollectingAndThen() {
		final Function<Integer, String> toHex = (i -> Integer.toHexString(i.intValue()));
		final Function<String, String> addPrefix = (s -> "0x" + s);
		final Function<Integer, String> toCHex = toHex.andThen(addPrefix);
		assertEquals("Test Function#andThen", "0x2a", toCHex.apply(Integer.valueOf(42)));
	}

	/** lambda-tests (call-site-classes should be reused) */
	@SuppressWarnings("null")
	@JsmudTest
	public void testsLambdaReuse() {
		IntFunction<String> lastToHex = null;
		for (int csNo = 0; csNo < 3; csNo++) {
			final IntFunction<String> toHex = (i -> Integer.toHexString(i));
			if (csNo == 2) {
				assertEquals("testsReuse(toHex)", Integer.toString(csNo) + '0', toHex.apply(csNo * 16));
				assertEquals("testsReuse(class)", lastToHex.getClass(), toHex.getClass());
			}
			lastToHex = toHex;
		}
	}

	@JsmudTest
	public void testsLambdaCommonSubclass() {
		final int a = 5;
		final int b = 8;
		// TestConstructor extends TestConstructorBase.
		final Supplier<TestConstructorBase> supplier = () -> new TestConstructor(a, b);
		final TestConstructorBase base = supplier.get();
		assertEquals("LambdaCommonSubclass", Integer.valueOf(5), Integer.valueOf(base.getSumBase()));
		
		// Calls org.rogmann.jsmud.vm.ClassWriterCallSite.getCommonSuperClass(String, String) with
		// TestExtendedConsumerImpl and TestExtendedConsumerImpl.
		final TestExtendedConsumerSupplier<TestExtendedConsumerImpl> supplierT = () -> new TestExtendedConsumerImpl();
		final TestExtendedConsumer extConsumer = supplierT.get();
		extConsumer.accept("JSB");
		assertEquals("LambdaCommonSubclass/TEC", "JSB", extConsumer.getName());
	}

	/** lambda-tests with returning of a private-interface. */
	@JsmudTest
	public void testsLambdaReturnPrivate() {
		final PrivateInterface pi = (s -> "private-" + s);
		assertEquals("LambdaReturnPrivate", "private-value", pi.apply("value"));
	}

	/** private interface */
	private static interface PrivateInterface {
		String apply(String s);
	}

	/** lambda-tests with java.security */
	@JsmudTest
	public void testsLambdaAndSecurity() {
		final Function<Integer, String> toHex = (i -> Integer.toHexString(i.intValue()));
		final Integer iInput = Integer.valueOf("83");
		final PrivilegedAction<String> action = () -> toHex.apply(iInput);
		final String result = AccessController.doPrivileged(action);
		assertEquals("AccessController", "53", result);
		
		final String s = "catalog";
		final TestMethodReference tac = new TestMethodReference(s.substring(0, 3));
		final String sResult = doPrivileged(tac::getValue);
		assertEquals("AccessController via ::getValue", "cat", sResult);

		final Integer iInput2 = Integer.valueOf("101");
		final PrivilegedExceptionAction<String> excAction = () -> toHex.apply(iInput2);
		final String resultExc;
		try {
			resultExc = AccessController.doPrivileged(excAction);
		} catch (PrivilegedActionException e) {
			throw new RuntimeException("Unexpected PAE", e);
		}
		assertEquals("AccessController:PrExAc", "65", resultExc);
	}

	/**
	 * ThreadLocal-Initializer with package-private class.
	 */
	@JsmudTest
	public void testsLambdaThreadLocal() {
		final String text = "initWord";
		final ThreadLocal<TestMethodReference> tl = ThreadLocal.withInitial(() -> new TestMethodReference(text));
		final TestMethodReference initRef = tl.get();
		assertEquals("LambdaThreadLocal", text, initRef.getValue());
	}

	/**
	 * Tests if lambda in different class-loaders can be executed.
	 */
	@JsmudTest
	public void testsLambdaInDifferentClassLoader() {
		final Supplier<String> supplier1 = SUPPLIER_CL1;
		final Supplier<String> supplier2 = SUPPLIER_CL2;
		if (supplier1 != null && supplier2 != null) {
			// We expect the suppliers to be in different class-loaders.
			assertEquals("CL-Name1", "ClassLoader1", supplier1.getClass().getClassLoader().toString());
			assertEquals("CL-Name2", "ClassLoader2", supplier2.getClass().getClassLoader().toString());
			assertEquals("Class-Lambda-Name1", "ClassB", supplier1.get());
			assertEquals("Class-Lambda-Name2", "ClassB", supplier2.get());
			System.out.println("Supplier 2: " + supplier2);
		}
	}

	@JsmudTest
	public void testsMethodArrayArgs() {
		final int[][] arrInt = new int[0][0];
		final Class<?> aArrIntExpected = int[][].class;
		assertEquals("test arrayArg", aArrIntExpected, checkArray(arrInt, aArrIntExpected));
	}
	
	static Class<?> checkArray(int[][] arr, Class<?> aArr) {
		return aArr;
	}

	@JsmudTest
	public void testsMethodRef() {
		// method-ref on class-
		final String[] sValues = { "Gamma", "Alpha", "Beta", "Delta" };
		Arrays.sort(sValues, String::compareToIgnoreCase);
		assertEquals("sValues[0]", "Alpha", sValues[0]);
		assertEquals("sValues[1]", "Beta", sValues[1]);
		assertEquals("sValues[2]", "Delta", sValues[2]);
		assertEquals("sValues[3]", "Gamma", sValues[3]);
		
		// method-ref on instance
		final TestExtendedConsumerImpl extImpl = new TestExtendedConsumerImpl();
		final TestExtendedConsumer methInstance = extImpl;
		Arrays.stream(sValues).forEach(methInstance::accept);
		assertEquals("extImpl.name", "Gamma", extImpl.getName());
	}
	
	@JsmudTest
	public void testsFields() {
		// SETSTATIC and GETSTATIC in super-class.
		TestConstructor.staticInt = 89;
		assertEquals("Test super-static", Integer.valueOf(89), Integer.valueOf(TestConstructor.staticInt));
	}

	@JsmudTest
	public void testsConstructorRef() {
		final String[] sValues = { "Gamma", "Alpha", "Beta", "Delta" };
		final Supplier<Set<String>> setSupplier = HashSet::new; // bsm_tag H_NEWINVOKESPECIAL.
		final Set<String> set = setSupplier.get();
		Arrays.stream(sValues).forEach(s -> set.add(s));
		assertEquals("set.size", Integer.valueOf(sValues.length), Integer.valueOf(set.size()));
		assertTrue("Test Gamma", set.contains("Gamma"));
	}
	
	@JsmudTest
	public void testsMethodChoosing() {
		final ConsumerIntConsumer consumer = new ConsumerIntConsumer();
		final TestMethodChoosing test = new TestMethodChoosing();
		final TestIntConsumer testAsInterfaceParent = test;
		final boolean rc = testAsInterfaceParent.tryAdvance((Consumer<Integer>) consumer);
		assertTrue("RC", rc == false);
		assertEquals("int", Integer.valueOf(0x43), Integer.valueOf(consumer.acceptedInt));
		assertEquals("Integer", Integer.valueOf(0x44), consumer.acceptedInteger);
	}
	
	@JsmudTest
	public void testsCatchException() {
		try {
			throwIllegalArgumentException();
		}
		catch (IllegalArgumentException e) {
			assertEquals("Test Exception", "frog", e.getMessage());
		}
		try {
			throwDeepException();
		}
		catch (IllegalArgumentException e) {
			assertEquals("Test Exception", "frog", e.getMessage());
		}
	}

	@JsmudTest
	public void testsProxy() {
		final ClassLoader cl = WorkExampleNonPublic.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExampleNonPublic.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExampleNonPublic worker = (WorkExampleNonPublic) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1 = worker.addA("Beta");
		final int result2 = worker.add5(37);
		assertEquals("Proxy: A", "BetaA", result1);
		assertEquals("Proxy: B", Integer.valueOf(42), Integer.valueOf(result2));
		
		// Can we invoke a Object-method in the proxy-instance?
		assertTrue("Proxy getClass", worker.getClass().getName().contains("$Proxy"));
	}

	/**
	 * Creates a proxy which executes GETFIELD with this$0.
	 */
	@JsmudTest
	public void testsProxyThisS0() {
		final ClassLoader cl = WorkExampleNonPublic.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExampleNonPublic.class };
		final InvocationHandler ih = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				final String name = method.getName();
				if ("addA".equals(name)) {
					final String p = (String) args[0];
					// fIntField is accessed via GETFIELD this$0.
					return p + fIntField;
				}
				return null;
			}
		};
		final WorkExampleNonPublic worker = (WorkExampleNonPublic) Proxy.newProxyInstance(cl, aInterfaces, ih);

		// Call proxy-method without reflection.
		final String result1 = worker.addA("Beta");
		assertEquals("ProxyThisS0: -1", "Beta-1", result1);
		
		// Call proxy-method with reflection on interface-method.
		Object oReturnRefl;
		try {
			final Method methodAddA = WorkExampleNonPublic.class.getDeclaredMethod("addA", String.class);
			oReturnRefl = methodAddA.invoke(worker, "BetaReflection");
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Reflection-exception in addA on interface", e);
		}
		assertEquals("ProxyThisS0-on-Interface: Refl-1", "BetaReflection-1", oReturnRefl);

		// Call proxy-method with reflection on proxy-method.
		try {
			final Method methodAddA = worker.getClass().getDeclaredMethod("addA", String.class);
			oReturnRefl = methodAddA.invoke(worker, "BetaReflection");
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Reflection-exception in addA on proxy", e);
		}
		assertEquals("ProxyThisS0-on-Proxy: Refl-1", "BetaReflection-1", oReturnRefl);

		final InvocationHandler ihMeta = new ProxyThis0Handler(worker).createInvocationHandler();
		final WorkExampleNonPublic workerMeta = (WorkExampleNonPublic) Proxy.newProxyInstance(cl, aInterfaces, ihMeta);
		assertEquals("Proxy$0", "Meta-1", workerMeta.addA("Meta"));
	}

	static class ProxyThis0Handler {
		WorkExampleNonPublic fProxy;
		public ProxyThis0Handler(final WorkExampleNonPublic proxy) {
			fProxy = proxy;
		}

		public InvocationHandler createInvocationHandler() {
			return new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					final String name = method.getName();
					if ("addA".equals(name)) {
						return fProxy.addA((String) args[0]);
					}
					else if ("add5".equals(name)) {
						return Integer.valueOf(fProxy.add5(((Integer) args[1]).intValue()));
					}
					return null;
				}
			};
		}
	}

	/**
	 * Creates a proxy with invoke-method in a super-class.
	 */
	@JsmudTest
	public void testsProxySuper() {
		final ClassLoader cl = WorkExampleNonPublic.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExampleNonPublic.class };
		final InvocationHandler ih = new ProxyInvocationHandlerChild();
		final WorkExampleNonPublic worker = (WorkExampleNonPublic) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1 = worker.addA("Super");
		assertEquals("ProxySuper: A", "SuperA", result1);
	}

	@JsmudTest
	public void testsProxyViaReflection() {
		final ClassLoader cl = WorkExampleNonPublic.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExampleNonPublic.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExampleNonPublic worker = (WorkExampleNonPublic) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1;
		final int result2;
		try {
			final Method methodAddA = WorkExampleNonPublic.class.getDeclaredMethod("addA", String.class);
			result1 = (String) methodAddA.invoke(worker, "Gamma");
			final Method methodAdd5 = WorkExampleNonPublic.class.getDeclaredMethod("add5", int.class);
			result2 = ((Integer) methodAdd5.invoke(worker, Integer.valueOf(20))).intValue();
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Exception while executing proxy via reflection", e);
		}
		assertEquals("ProxyRefl: A", "GammaA", result1);
		assertEquals("ProxyRefl: B", Integer.valueOf(25), Integer.valueOf(result2));

		// Can we invoke a Object-method in the proxy-instance?
		assertTrue("ProxyRefl getClass", worker.getClass().getName().contains("$Proxy"));

		// Can we invoke a proxy via a method of the proxy itself?
		final Method methodProxy;
		try {
			methodProxy = worker.getClass().getDeclaredMethod("addA", String.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(String.format("Exception while looking for method addA in %s", worker.getClass()), e);
		}
		final String resultDelta;
		try {
			resultDelta = (String) methodProxy.invoke(worker, "Delta");
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(String.format("Exception while executing %s", methodProxy), e);
		}
		assertEquals("ProxyRefl: Impl", "DeltaA", resultDelta);
	}

	/**
	 * Checks if the invocation-handler gets an interface-method as method.
	 */
	@JsmudTest
	public void testsProxyViaReflectionMethod() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		// getMethod gets the method given the invocation-handler. It should be an interface-method.
		final Method methodProxy;
		try {
			methodProxy = worker.getClass().getDeclaredMethod("getMethod");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(String.format("Exception while looking for method addA in %s", worker.getClass()), e);
		}
		final Method methodGet;
		try {
			// We expect an interface-method, not the given proxy-method.
			methodGet = (Method) methodProxy.invoke(worker);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(String.format("Exception while executing %s", methodProxy), e);
		}
		assertEquals("ProxyReflMethod: IntMethod", WorkExample.class, methodGet.getDeclaringClass());
	}

	/**
	 * Call of a proxy using a public interface.
	 * Proxies with public interfaces may be placed under "com.sun.*".
	 */
	@JsmudTest
	public void testsProxyPublicInterface() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1 = worker.addA("Beta");
		final int result2 = worker.add5(37);
		assertEquals("ProxyPublic: A", "BetaA", result1);
		assertEquals("ProxyPublic: B", Integer.valueOf(42), Integer.valueOf(result2));
		
		// Can we invoke a Object-method in the proxy-instance?
		assertTrue("Proxy getClass", worker.getClass().getName().contains("$Proxy"));

		// We expect an interface-method, not the given proxy-method.
		final Method methodInInvocationHandler = worker.getMethod();
		assertEquals("ProxyPublicMethod: IntMethod", WorkExample.class, methodInInvocationHandler.getDeclaringClass());
	}

	/**
	 * Call of a proxy using a public interface by reflection.
	 */
	@JsmudTest
	public void testsProxyPublicInterfaceViaReflection() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1;
		final int result2;
		try {
			final Method methodAddA = WorkExample.class.getDeclaredMethod("addA", String.class);
			result1 = (String) methodAddA.invoke(worker, "Gamma");
			final Method methodAdd5 = WorkExample.class.getDeclaredMethod("add5", int.class);
			result2 = ((Integer) methodAdd5.invoke(worker, Integer.valueOf(20))).intValue();
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Exception while executing proxy via reflection", e);
		}
		assertEquals("ProxyPublicRefl: A", "GammaA", result1);
		assertEquals("ProxyPublicRefl: B", Integer.valueOf(25), Integer.valueOf(result2));

		// Can we invoke a Object-method in the proxy-instance?
		assertTrue("ProxyPublicRefl getClass", worker.getClass().getName().contains("$Proxy"));
	}

	@JsmudTest
	public void testsProxyPublicInterfaceViaReflectionImpl() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1;
		final int result2;
		try {
			final Method methodAddA = worker.getClass().getDeclaredMethod("addA", String.class);
			result1 = (String) methodAddA.invoke(worker, "Gamma");
			final Method methodAdd5 = worker.getClass().getDeclaredMethod("add5", int.class);
			result2 = ((Integer) methodAdd5.invoke(worker, Integer.valueOf(20))).intValue();
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Exception while executing proxy via reflection", e);
		}
		assertEquals("ProxyImpl: A", "GammaA", result1);
		assertEquals("ProxyImpl: B", Integer.valueOf(25), Integer.valueOf(result2));

		// Can we invoke a Object-method in the proxy-instance?
		assertTrue("ProxyImpl getClass", worker.getClass().getName().contains("$Proxy"));
	}

	/**
	 * Execute a proxy-method and use reflection to execute the method given the invocation-handler.
	 */
	@JsmudTest
	public void testsProxyExecuteInternal() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final Object internalWorker = new MockWorkingHandler();
		final InvocationHandler ih = new ProxyInvocationHandler(internalWorker);
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String internalResult = worker.executeMethod("左", "右");
		assertEquals("ProxyExecuteInternal", "左右", internalResult);
	}

	public class ProxyInvocationHandler implements InvocationHandler {
		final Object instance;
		ProxyInvocationHandler() {
			this.instance = null;
		}
		ProxyInvocationHandler(final Object instance) {
			this.instance = instance;
		}
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			final String name = method.getName();
			if ("addA".equals(name)) {
				final String s = (String) args[0];
				return s + "A";
			}
			else if ("add5".equals(name)) {
				final int i = ((Integer) args[0]).intValue();
				return Integer.valueOf(i + 5);
			}
			else if ("getMethod".equals(name)) {
				return method;
			}
			else if ("executeMethod".equals(name)) {
				if (instance == null) {
					throw new IllegalStateException("this.instance hasn't been set.");
				}
				return method.invoke(instance, args);
			}
			else if ("toString".equals(name)) {
				return "PIH";
			}
			throw new IllegalArgumentException("Unexpected method " + name);
		}
	}

	public class MockWorkingHandler implements WorkExample {
		@Override
		public String addA(String s) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int add5(int i) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Method getMethod() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String executeMethod(String a, String b) {
			return a + b;
		}
	}

	public class ProxyInvocationHandlerChild extends ProxyInvocationHandler {
		// nothing here
	}

	@JsmudTest
	public void testsReflection() {
		final JvmTests jvmTest;
		try {
			final Constructor<JvmTests> constr = JvmTests.class.getConstructor(int.class);
			jvmTest = constr.newInstance(Integer.valueOf(37));
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("reflection-error in constructor-test", e);
		}
		assertEquals("testsReflection: constructor", Integer.valueOf(37), Integer.valueOf(jvmTest.fIntField));

		final Integer iResult;
		try {
			final Method method = JvmTests.class.getMethod("getIntField");
			iResult = (Integer) method.invoke(jvmTest);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("reflection-error in getIntField-test", e);
		}
		assertEquals("testsReflection: getIntField", Integer.valueOf(37), iResult);

		Integer diff;
		try {
			final Method method = JvmTests.class.getMethod("subtract", int.class, int.class);
			diff = (Integer) method.invoke(jvmTest, Integer.valueOf(89), Integer.valueOf(55));
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("reflection-error in subtract-test", e);
		}
		assertEquals("testsReflection: subtract", Integer.valueOf(34), diff);
	}

	@JsmudTest
	public void testsReflectionOnInterface() {
		final TestExtendedConsumer impl = new TestExtendedConsumerImpl();
		final Method methodAccept;
		final Method methodGetName;
		try {
			methodAccept = Consumer.class.getDeclaredMethod("accept", Object.class);
			methodGetName = TestExtendedConsumer.class.getDeclaredMethod("getName");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException("Exception while looking for declared-methods", e);
		}
		try {
			methodAccept.invoke(impl, "王");
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Exception while invoking accept-method", e);
		}
		final String name;
		try {
			name = (String) methodGetName.invoke(impl);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Exception while invoking getName-method", e);
		}
		assertEquals("ReflectionOnInterface", "王", name);
	}

	@JsmudTest
	public void testReflectionDeclaredConstructors() {
		final Constructor<?>[] constr = TestConstructorWithoutDefault.class.getDeclaredConstructors();
		final String[] constrDesc = new String[constr.length];
		for (int i = 0; i < constr.length; i++) {
 			constrDesc[i] = constr[i].toString();
		}
		Arrays.sort(constrDesc);
		assertEquals("testReflectionDeclaredConstructors",
				String.format("[public %s$%s(int,int)]", JvmTests.class.getName(), TestConstructorWithoutDefault.class.getSimpleName()),
				Arrays.toString(constrDesc));
	}

	@JsmudTest(description = "reflection on annotation-method")
	public void testsReflectionAnnotation() {
		final Annotation annotation;
		final Method methodAnnotationType;
		try {
			final Method method = getClass().getDeclaredMethod("testsReflectionAnnotation");
			final Annotation[] annotations = method.getAnnotationsByType(JsmudTest.class);
			annotation = annotations[0];
			methodAnnotationType = JsmudTest.class.getDeclaredMethod("description");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException("Can't determine annotation-method", e);
		}
		final String description;
		try {
			// annotation-instance is a proxy.
			description = (String) methodAnnotationType.invoke(annotation);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Error while execution annotation-method", e);
		}
		assertEquals("ReflectionAnnotation", "reflection on annotation-method", description);
	}

	@JsmudTest
	public void testsClassForName() {
		final String className = JvmTests.class.getName();
		final Class<?> classJvmTests;
		try {
			classJvmTests = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Class.forName-test failed", e);
		}
		assertEquals("Test Class.forName", className, classJvmTests.getName());
		Short sValue;
		try {
			final Field fieldShort = classJvmTests.getDeclaredField("SHORT_20000");
			fieldShort.setAccessible(true);
			sValue = (Short) fieldShort.get(classJvmTests);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException("static-field-check failed", e);
		}
		assertEquals("Test Class.forName static field", Short.valueOf((short) 20000), sValue);

		// We expect that the class-loaders are identical: Either both application-classloader or
		// both JsmudClassLoader.
		assertEquals("Test Class.forName class-loader", JvmTests.class.getClassLoader(), classJvmTests.getClassLoader());
		
		String s;
		try {
			// Spring checks for classes in this way.
			Class.forName("nothing.ThisClassDoesntExist");
			s = "unexpected";
		} catch (ClassNotFoundException e) {
			s = "expectedCNFE";
		}
		assertEquals("Test CNFE", "expectedCNFE", s);
	}

	@JsmudTest
	public void testsAccessController() {
		final int iValue = 5;
		PrivilegedAction<String> action = new PrivilegedAction<String>() {
			/** {@inheritDoc} */
			@Override
			public String run() {
				return "Result" + iValue;
			}
		};
		final String result = AccessController.doPrivileged(action);
		assertEquals("Test AccessController", "Result5", result);
	}

	private static void throwDeepException() {
		throwIllegalArgumentException();
	}

	private static void throwIllegalArgumentException() {
		throw new IllegalArgumentException("frog");
	}
	
	public void testsJavaTime() {
		final LocalDateTime localDate = LocalDateTime.of(2021, 05, 04, 13, 37);
		final String dateFormatted = DateTimeFormatter.ISO_DATE_TIME.format(localDate);
		assertEquals("May the 4th", "2021-05-04T13:37:00", dateFormatted);
	}
	
	/**
	 * Throws an exception if the test-flag is not <code>true</code>.
	 * @param testname test-name
	 * @param flag test-flag
	 */
	public void assertTrue(String testname, boolean flag) {
		if (!flag) {
			throw new RuntimeException("Test failed: " + testname);
		}
		executedTests.add(testname);
	}

	public void assertEquals(String testname, final Object oExpected, final Object oGiven) {
		if (oExpected == null && oGiven != null) {
			throw new RuntimeException(String.format("Test (%s) failed: unexpected non-null-value %s",
					testname, oGiven));
		}
		else if (oExpected != null && !oExpected.equals(oGiven)) {
			throw new RuntimeException(String.format("Test (%s) failed: %s != %s",
					testname, oExpected, oGiven));
		}
		executedTests.add(testname);
	}

	// test-object methods

	@SuppressWarnings("static-method")
	public int subtract(final int a, final int b) {
		return a - b;
	}

	public int getIntField() {
		return fIntField;
	}

	// test-classes and test-interfaces

	/**
	 * Interface whichs extends Consumer.
	 */
	public interface TestExtendedConsumer extends Consumer<String> {
		String getName();
	}

	public interface TestExtendedConsumerSupplier<T extends TestExtendedConsumer> {
		T get();
	}

	/**
	 * Test-class for getting an instance of TestExtendedConsumer.
	 */
	public class TestExtendedConsumerImpl implements TestExtendedConsumer {
		private String name;
		/** {@inheritDoc} */
		@Override
		public void accept(String t) {
			name = t;
		}
		/** {@inheritDoc} */
		@Override
		public String getName() {
			return name;
		}
	}

	TestExtendedConsumer getExtendedConsumer() {
		return new TestExtendedConsumerImpl();
	}

	/**
	 * Interface containing tryAdvance with two different signatures: LConsumer; and LIntConsumer.
	 */
	public interface TestIntConsumer {
        boolean tryAdvance(Consumer<? super Integer> action);
        boolean tryAdvance(IntConsumer action);
	}

	/**
	 * Interface containing tryAdvance with two different signatures: LConsumer; and LIntConsumer.
	 */
	public interface TestIntConsumerChild extends TestIntConsumer {
        @Override
		default boolean tryAdvance(Consumer<? super Integer> action) {
        	action.accept(Integer.valueOf(0x44));
        	if (action instanceof IntConsumer) {
        		tryAdvance((IntConsumer) action);
        	}
        	return false;
        }
	}

	/** 
	 * Class both implementing Consumer<Integer> and IntConsumer.
	 */
	public static class ConsumerIntConsumer implements Consumer<Integer>, IntConsumer {
		int acceptedInt;
		Integer acceptedInteger;

		@Override
		public void accept(int value) {
			acceptedInt = value;
		}

		@Override
		public void accept(Integer iValue) {
			acceptedInteger = iValue;
		}
		
	}

	public static class TestConstructorBase {
		static int staticInt;
		private final int sumBase;
		public TestConstructorBase() {
			sumBase = -2;
		}
		public TestConstructorBase(int a) {
			sumBase = a;
		}
		public int getSumBase() {
			return sumBase;
		}
	}
	
	public static class TestConstructor extends TestConstructorBase {
		private final int sum;
		public TestConstructor() {
			super();
			sum = -42;
		}
		public TestConstructor(int a, int b) {
			super(a);
			sum = a + b;
		}
		public int getSum() {
			return sum;
		}
	}

	public static class TestConstructorWithoutDefault extends TestConstructorBase {
		private final int sum;
		public TestConstructorWithoutDefault(int a, int b) {
			super(a);
			sum = a + b;
		}
		public int getSum() {
			return sum;
		}
	}

	/**
	 * Class implementing tryAdvance with two different signatures: LConsumer; and LIntConsumer.
	 * See java.util.Spliterators.IntArraySpliterator.
	 */
	public static class TestMethodChoosing implements TestIntConsumerChild {
		@Override
		public boolean tryAdvance(IntConsumer action) {
			action.accept(0x43);
			return true;
		}
	}

	public void testsSyntheticMethod() {
		final TestParameter<Integer> parameter = new TestSynthetic();
		assertEquals("Test synthetic", Integer.valueOf(37), parameter.get());
	}

	public interface TestParameter<T> {
		T get();
	}

	public class TestSynthetic implements TestParameter<Integer> {
		@Override
		public Integer get() {
			return Integer.valueOf("37");
		}
	}

	/** non-public interface used in proxy-tests */
	interface WorkExampleNonPublic {
		String addA(String s);
		int add5(int i);
	}

	/** public Interface used in proxy-tests */
	public interface WorkExample {
		String addA(String s);
		int add5(int i);
		Method getMethod();
		String executeMethod(String a, String b);
	}

	static interface TestMethodInterface {
		String getValue();
	}

	/**
	 * Tests of method-references.
	 */
	private static class TestMethodReference implements TestMethodInterface {
		final String value;
		public TestMethodReference(final String value) {
			this.value = value;
		}
		@Override
		public String getValue() {
			return value;
		}

	}

	/**
	 * Call doPrivileged of AccessController.
	 * @param action action to be executed
	 * @return return-value
	 * @param <T> return-type
	 */
	public static <T> T doPrivileged(Supplier<T> action) {
		return AccessController.doPrivileged((PrivilegedAction<T>) action::get);
	}
}
