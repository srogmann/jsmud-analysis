package org.rogmann.jsmud.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rogmann.jsmud.test.JvmTests.TestConstructorBase;

/**
 * Some simple JVM-tests.
 */
public class JvmInitTests {
	/** static test-value */
	private static final Integer CLINIT_TEST;
	
	/** static test-set */
	private static final Set<String> CLINIT_SET;

	/** static test-singleton */
	private static final JvmInitTests CLINIT_SINGLETON;

	/** list of executed tests */
	private final List<String> executedTests = new ArrayList<>();

	/** int-instance */
	protected final int fIntField;

	static {
		CLINIT_TEST = Integer.valueOf(691);
		CLINIT_SET = new HashSet<>(1);
		CLINIT_SET.add("ClinitTestEntry");
		CLINIT_SINGLETON = new JvmInitTests();
	}

	public static void main(String[] args) {
		final JvmInitTests jvmTests = new JvmInitTests();
		jvmTests.tests();
	}

	/** Executes a simple test-suite */
	public void tests() {
		testsStaticInitializer();
		testsConstructor();
		System.out.println("Executed tests: " + executedTests);
	}

	/**
	 * Constructor
	 */
	public JvmInitTests() {
		this(-1);
	}

	/**
	 * Constructor
	 * @param iValue int-field-value
	 */
	public JvmInitTests(final int iValue) {
		this.fIntField = iValue;
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

	// test-classes and test-interfaces

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

}
