package org.rogmann.jsmud.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Some simple JVM-tests.
 */
public class JvmTests {
	/** static test-value */
	private static final Integer CLINIT_TEST;
	
	/** static test-set */
	private static final Set<String> CLINIT_SET;

	/** static test-singleton */
	private static final JvmTests CLINIT_SINGLETON;

	/** list of executed tests */
	private final List<String> executedTests = new ArrayList<>();
	
	/** internal short: 20000 */
	private static final short SHORT_20000 = Short.parseShort("20000");

	/** test-map */
	private final Map<String, Integer> testMap = new HashMap<>();
	
	static {
		CLINIT_TEST = Integer.valueOf(691);
		CLINIT_SET = new HashSet<>(1);
		CLINIT_SET.add("ClinitTestEntry");
		CLINIT_SINGLETON = new JvmTests();
	}

	/** int-instance */
	private final int fIntField;
	
	public static void main(String[] args) {
		final JvmTests jvmTests = new JvmTests();
		jvmTests.tests();
	}

	/** Executes a simple test-suite */
	public void tests() {
		testsBoolean();
		testsByte();
		testsChar();
		testsShort();
		testsLong();
		testsFloat();
		testsDouble();
		testsLambda();
		testsLambdaFunction();
		testsLambdaNonStatic();
		testsLambdaInterface();
		testsLambdaStreamCollectOnly();
		testsLambdaStreams();
		testsLambdaStreamsThis();
		testsLambdaBiFunctionAndThen();
		testsLambdaCollectingAndThen();
		testsLambdaMultipleFunctions();
//		testsMethodChoosing();
//		testsMethodRef();
//		testsMethodArrayArgs();
//		testsStaticInitializer();
//		testsSyntheticMethod();
//		testsConstructor();
//		testsConstructorRef();
//		testsCatchException();
//		testsJavaTime();
//		testsProxy();
//		testsProxySuper();
//		testsReflection();
//		testReflectionDeclaredConstructors();
//		testsClassForName();
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
	public void testsBoolean() {
		final boolean[] aBoolean = new boolean[2];
		aBoolean[0] = true;
		aBoolean[1] = false;
		assertTrue("z0", aBoolean[0]);
		assertTrue("z1", !aBoolean[1]);
	}

	/** byte-tests */
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
	}

	/** char-tests */
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
	}
	
	public void testsShort() {
		final short a = (short) 1;
		final short b = Short.parseShort("-1");
		final int c = doubleShort(SHORT_20000);
		assertEquals("Test a + b", Short.valueOf((short) 0), Short.valueOf((short) (a + b)));
		assertEquals("Test b", Short.valueOf((short) -1), Short.valueOf(b));
		assertEquals("Test 2 * b", Short.valueOf((short) -2), Short.valueOf(doubleShort(b)));
		assertEquals("Test 40000 - 65536", Integer.valueOf(-25536), Integer.valueOf(c));
	}
	
	static short doubleShort(short a) {
		return (short) (2 * a);
	}

	public void testsLong() {
		final long l = Long.parseLong("16777216");
		final long m = 10L;
		final long n = 13L;
		assertEquals("l << 16", Long.valueOf(1099511627776L), Long.valueOf(l << 16));
		assertEquals("l >> 4", Long.valueOf(1048576L), Long.valueOf(l >> 4));
		assertEquals("l >>> 4", Long.valueOf(1048576L), Long.valueOf(l >>> 4));
		assertEquals("l | m", Long.valueOf(16777226L), Long.valueOf(l | m));
		assertEquals("m ^ n", Long.valueOf(7L), Long.valueOf(m ^ n));
		assertEquals("n % m", Long.valueOf(3L), Long.valueOf(n % m));
		
		assertEquals("long-test 4096", Long.valueOf(4096L), Long.valueOf(testsLong2(6L, 9L, 0L, 4L)));
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
	
	public void testsFloat() {
		final float a = Float.parseFloat("1357.5");
		final float b = 10.0f;
		assertEquals("a % b", Float.valueOf(7.5f), Float.valueOf(a % b));
		assertTrue("a > b", a > b);
		assertTrue("b < a", b < a);
	}

	public void testsDouble() {
		final double a = Double.parseDouble("12345.1122334455");
		final double b = 10.0d;
		assertEquals("a % b", Double.valueOf(5.1122334455d), Double.valueOf(Math.round((a % b) * 10000000000L) / 10000000000d));
		assertTrue("a > b", a > b);
		assertTrue("b < a", b < a);
	}

	public int mult(int a, long b) {
		return a * (int) b;
	}
	
	/** simple lambda-tests */
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
		final IntFunction<Integer> iFkt = (i -> Integer.valueOf(i * i));
		assertEquals("iFkt 3", Integer.valueOf(9), iFkt.apply(3));
		assertEquals("iFkt 25", Integer.valueOf(25), iFkt.apply(5));
	}
	
	/**
	 * Example of a lambda-function via ::-operator.
	 */
	public void testsLambdaFunction() {
		Function<String, String> sFkt = String::toUpperCase;
		assertEquals("lambdaFunction toUpperCase", "UPPER", sFkt.apply("uPPeR"));
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
	public void testsLambdaNonStatic() {
		this.testMap.put("A", Integer.valueOf(11));
		final String key = "A";
		Predicate<Set<String>> predicate = set -> this.testMap.containsKey(key);
		assertTrue("Key present", predicate.test(Collections.emptySet()));
	}

	public void testsLambdaInterface() {
		final Consumer<Map<String, Integer>> consumer = Map::clear;
		testMap.put("a1", Integer.valueOf(1));
		consumer.accept(testMap);
		assertEquals("Test lambda-interface", Integer.valueOf(0), Integer.valueOf(testMap.size()));
	}

	/** lambda-stream-tests with collect only*/
	public void testsLambdaStreamCollectOnly() {
		final List<String> list = Arrays.asList("d1", "d2", "d3");
		final String listStreamed = list.stream().collect(Collectors.joining("-"));
		assertEquals("Test listStreamCollectOnly", "d1-d2-d3", listStreamed);
	}

	/** lambda-tests with streams */
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

	/** lambda-tests with streams: filter with instance */
	public void testsLambdaStreamsThis() {
		this.testMap.put("a2", Integer.valueOf(11));

		final List<String> list = Arrays.asList("a1", "a2", "b1");
		final String listStreamed = list.stream().filter(s -> !this.testMap.containsKey(s)).collect(Collectors.joining("-"));
		assertEquals("Test lambdaStreamsThis", "a1-b1", listStreamed);
	}

	/** lambda-tests with function and Collectors.andThan(...) */
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
	public void testsLambdaCollectingAndThen() {
		final Function<Integer, String> toHex = (i -> Integer.toHexString(i.intValue()));
		final Function<String, String> addPrefix = (s -> "0x" + s);
		final Function<Integer, String> toCHex = toHex.andThen(addPrefix);
		assertEquals("Test Function#andThen", "0x2a", toCHex.apply(Integer.valueOf(42)));
	}

	public void testsMethodArrayArgs() {
		final int[][] arrInt = new int[0][0];
		final Class<?> aArrIntExpected = int[][].class;
		assertEquals("test arrayArg", aArrIntExpected, checkArray(arrInt, aArrIntExpected));
	}
	
	static Class<?> checkArray(int[][] arr, Class<?> aArr) {
		return aArr;
	}

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
	
	public void testsStaticInitializer() {
		assertEquals("Test static int", Integer.valueOf(691), CLINIT_TEST);
		assertTrue("Test static set 1", CLINIT_SET != null);
		assertTrue("Test static set 2", CLINIT_SET.contains("ClinitTestEntry"));
		assertTrue("Test singleton 1", CLINIT_SINGLETON != null);
		assertTrue("Test singleton 2", CLINIT_SINGLETON.executedTests != null);
		assertEquals("Test singleton 3", Integer.valueOf(0), Integer.valueOf(CLINIT_SINGLETON.executedTests.size()));
	}

	public void testsConstructor() {
		final TestConstructor testConstr = new TestConstructor(21, 34);
		assertEquals("Constructor", Integer.valueOf(55), Integer.valueOf(testConstr.getSum()));
		assertEquals("Constructor-Base", Integer.valueOf(21), Integer.valueOf(testConstr.getSumBase()));

		final TestConstructor testConstrDefault = new TestConstructor();
		assertEquals("ConstructorDefault", Integer.valueOf(-42), Integer.valueOf(testConstrDefault.getSum()));
		assertEquals("ConstructorDefault-Base", Integer.valueOf(-2), Integer.valueOf(testConstrDefault.getSumBase()));

		final TestConstructorBase testConstrBase = new TestConstructorBase();
		assertEquals("ConstructorBase", Integer.valueOf(-2), Integer.valueOf(testConstrBase.getSumBase()));
		final TestConstructorBase testConstrBase7 = new TestConstructorBase(7);
		assertEquals("ConstructorBase7", Integer.valueOf(7), Integer.valueOf(testConstrBase7.getSumBase()));
	}

	public void testsConstructorRef() {
		final String[] sValues = { "Gamma", "Alpha", "Beta", "Delta" };
		final Supplier<Set<String>> setSupplier = HashSet::new;
		final Set<String> set = setSupplier.get();
		Arrays.stream(sValues).forEach(s -> set.add(s));
		assertEquals("set.size", Integer.valueOf(sValues.length), Integer.valueOf(set.size()));
		assertTrue("Test Gamma", set.contains("Gamma"));
	}
	
	public void testsMethodChoosing() {
		final ConsumerIntConsumer consumer = new ConsumerIntConsumer();
		final TestMethodChoosing test = new TestMethodChoosing();
		final TestIntConsumer testAsInterfaceParent = test;
		final boolean rc = testAsInterfaceParent.tryAdvance((Consumer<Integer>) consumer);
		assertTrue("RC", rc == false);
		assertEquals("int", Integer.valueOf(0x43), Integer.valueOf(consumer.acceptedInt));
		assertEquals("Integer", Integer.valueOf(0x44), consumer.acceptedInteger);
	}
	
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

	public void testsProxy() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandler();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1 = worker.addA("Beta");
		final int result2 = worker.add5(37);
		assertEquals("Proxy: A", "BetaA", result1);
		assertEquals("Proxy: B", Integer.valueOf(42), Integer.valueOf(result2));
	}

	/**
	 * Creates a proxy with invoke-method in a super-class.
	 */
	public void testsProxySuper() {
		final ClassLoader cl = WorkExample.class.getClassLoader();
		final Class<?>[] aInterfaces = { WorkExample.class };
		final InvocationHandler ih = new ProxyInvocationHandlerChild();
		final WorkExample worker = (WorkExample) Proxy.newProxyInstance(cl, aInterfaces, ih);

		final String result1 = worker.addA("Super");
		assertEquals("ProxySuper: A", "SuperA", result1);
	}

	public static class ProxyInvocationHandler implements InvocationHandler {
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
			else if ("toString".equals(name)) {
				
			}
			throw new IllegalArgumentException("Unexpected method " + name);
		}
	}

	public static class ProxyInvocationHandlerChild extends ProxyInvocationHandler {
		// nothing here
	}

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
	}

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

	/** Interface used in proxy-tests */
	interface WorkExample {
		String addA(String s);
		int add5(int i);
	}

}
