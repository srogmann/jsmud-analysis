package org.rogmann.jsmud.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rogmann.jsmud.log.LoggerFactory;
import org.rogmann.jsmud.log.LoggerFactorySystemOut;
import org.rogmann.jsmud.vm.JvmHelper;

@TestMethodOrder(value = OrderAnnotation.class)
public class JvmTestsJUnit {
	private final PrintStream psOut = System.out;
	
	@BeforeAll
	static void beforeClass() {
		LoggerFactory.setLoggerSpi(new LoggerFactorySystemOut(System.out, false, false));
	}

	void execute(final Runnable runnable) {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(baos);
		boolean isOk = false;
		try {
			JvmHelper.executeRunnable(runnable, ps);
			isOk = true;
		}
		finally {
			if (!isOk) {
				ps.flush();
				psOut.println("Error: " + new String(baos.toByteArray()));
			}
		}
	}

	// test-methods
	//
	
	/** JUnit-Test of method {@link JvmTests#testsBoolean()} */
	@Test
	@Order(1)
	public void testTestsBoolean() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsBoolean();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsByte()} */
	@Test
	@Order(2)
	public void testTestsByte() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsByte();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsChar()} */
	@Test
	@Order(3)
	public void testTestsChar() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsChar();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsShort()} */
	@Test
	@Order(4)
	public void testTestsShort() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsShort();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLong()} */
	@Test
	@Order(5)
	public void testTestsLong() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLong();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsFloat()} */
	@Test
	@Order(6)
	public void testTestsFloat() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsFloat();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsDouble()} */
	@Test
	@Order(7)
	public void testTestsDouble() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsDouble();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsArray()} */
	@Test
	@Order(8)
	public void testTestsArray() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsArray();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsArrayIndex()} */
	@Test
	@Order(9)
	public void testTestsArrayIndex() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsArrayIndex();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsExceptionHandling()} */
	@Test
	@Order(10)
	public void testTestsExceptionHandling() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsExceptionHandling();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsRegexp()} */
	@Test
	@Order(11)
	public void testTestsRegexp() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsRegexp();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsSwitch()} */
	@Test
	@Order(12)
	public void testTestsSwitch() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsSwitch();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambda()} */
	@Test
	@Order(13)
	public void testTestsLambda() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambda();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaBiConsumer()} */
	@Test
	@Order(14)
	public void testTestsLambdaBiConsumer() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaBiConsumer();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaClassMethodReferences()} */
	@Test
	@Order(15)
	public void testTestsLambdaClassMethodReferences() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaClassMethodReferences();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaObjectMethodReferences()} */
	@Test
	@Order(16)
	public void testTestsLambdaObjectMethodReferences() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaObjectMethodReferences();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaMultipleFunctions()} */
	@Test
	@Order(17)
	public void testTestsLambdaMultipleFunctions() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaMultipleFunctions();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaNonStatic()} */
	@Test
	@Order(18)
	public void testTestsLambdaNonStatic() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaNonStatic();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaInterface()} */
	@Test
	@Order(19)
	public void testTestsLambdaInterface() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaInterface();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaSpecialAndThen()} */
	@Test
	@Order(20)
	public void testTestsLambdaSpecialAndThen() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaSpecialAndThen();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaFunctionAndThen()} */
	@Test
	@Order(21)
	public void testTestsLambdaFunctionAndThen() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaFunctionAndThen();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaPrimitiveTypes()} */
	@Test
	@Order(22)
	public void testTestsLambdaPrimitiveTypes() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaPrimitiveTypes();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaStreamCollectOnly()} */
	@Test
	@Order(23)
	public void testTestsLambdaStreamCollectOnly() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaStreamCollectOnly();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaStreams()} */
	@Test
	@Order(24)
	public void testTestsLambdaStreams() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaStreams();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaStreams2()} */
	@Test
	@Order(25)
	public void testTestsLambdaStreams2() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaStreams2();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaStreamsThis()} */
	@Test
	@Order(26)
	public void testTestsLambdaStreamsThis() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaStreamsThis();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaBiFunctionAndThen()} */
	@Test
	@Order(27)
	public void testTestsLambdaBiFunctionAndThen() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaBiFunctionAndThen();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaCollectingAndThen()} */
	@Test
	@Order(28)
	public void testTestsLambdaCollectingAndThen() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaCollectingAndThen();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaReuse()} */
	@Test
	@Order(29)
	public void testTestsLambdaReuse() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaReuse();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsLambdaAndSecurity()} */
	@Test
	@Order(30)
	public void testTestsLambdaAndSecurity() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsLambdaAndSecurity();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsMethodArrayArgs()} */
	@Test
	@Order(31)
	public void testTestsMethodArrayArgs() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsMethodArrayArgs();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsMethodRef()} */
	@Test
	@Order(32)
	public void testTestsMethodRef() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsMethodRef();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsFields()} */
	@Test
	@Order(33)
	public void testTestsFields() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsFields();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsConstructorRef()} */
	@Test
	@Order(34)
	public void testTestsConstructorRef() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsConstructorRef();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsMethodChoosing()} */
	@Test
	@Order(35)
	public void testTestsMethodChoosing() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsMethodChoosing();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsCatchException()} */
	@Test
	@Order(36)
	public void testTestsCatchException() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsCatchException();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxy()} */
	@Test
	@Order(37)
	public void testTestsProxy() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxy();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxySuper()} */
	@Test
	@Order(38)
	public void testTestsProxySuper() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxySuper();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyViaReflection()} */
	@Test
	@Order(39)
	public void testTestsProxyViaReflection() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyViaReflection();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyViaReflectionMethod()} */
	@Test
	@Order(40)
	public void testTestsProxyViaReflectionMethod() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyViaReflectionMethod();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyPublicInterface()} */
	@Test
	@Order(41)
	public void testTestsProxyPublicInterface() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyPublicInterface();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyPublicInterfaceViaReflection()} */
	@Test
	@Order(42)
	public void testTestsProxyPublicInterfaceViaReflection() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyPublicInterfaceViaReflection();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyPublicInterfaceViaReflectionImpl()} */
	@Test
	@Order(43)
	public void testTestsProxyPublicInterfaceViaReflectionImpl() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyPublicInterfaceViaReflectionImpl();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsProxyExecuteInternal()} */
	@Test
	@Order(44)
	public void testTestsProxyExecuteInternal() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsProxyExecuteInternal();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsReflection()} */
	@Test
	@Order(45)
	public void testTestsReflection() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsReflection();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsReflectionOnInterface()} */
	@Test
	@Order(46)
	public void testTestsReflectionOnInterface() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsReflectionOnInterface();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testReflectionDeclaredConstructors()} */
	@Test
	@Order(47)
	public void testTestReflectionDeclaredConstructors() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testReflectionDeclaredConstructors();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsClassForName()} */
	@Test
	@Order(48)
	public void testTestsClassForName() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsClassForName();
			}
		};
		execute(runnable);
	}

	/** JUnit-Test of method {@link JvmTests#testsAccessController()} */
	@Test
	@Order(49)
	public void testTestsAccessController() {
		final JvmTests jvmTests = new JvmTests();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				jvmTests.testsAccessController();
			}
		};
		execute(runnable);
	}

}
