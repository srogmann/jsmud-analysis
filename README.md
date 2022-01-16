# jsmud-analysis

Jsmud-analysis (_Java simulator multi-user debugger_) is an embedded interpreter of [Java bytecode](https://en.wikipedia.org/wiki/Java_bytecode) written in Java supplemented by a JDWP-debugger-server. You can use it to debug Java applications running in environments where you can't or don't want to enable a debugger via JVM arguments.

The interpreter uses reflection to work on the original objects so one can build an object-tree using the underlying JVM and then execute code working on that tree using jsmud-analysis. The interpreter uses class-generation to support INVOKEDYNAMIC and inspection of static initializers.

The purpose is to support the analysis of given Java classes in development stages. The module system in JDK 16 and later restricts reflective access against JDK-classes so their internal methods can't be called by a jsmud-analysis outside java.base, see [JEP 396](https://openjdk.java.net/jeps/396) and [JEP 403](https://openjdk.java.net/jeps/403).

## Getting started

The class JvmHelper contains helper-methods to start the JVM in simple environments. Place the code of interest or a method which calls the code of interest into a Runnable or Supplier and execute it by means of the JvmHelper-class of jsmud-analysis:

    final Runnable runnable = new Runnable() {
        public void run() {
            final String[] chess = { "e2-e4", "e7-e5", "g1-f3", "b8-c6" };
            final String src = Arrays.stream(chess)
                    .map(move -> move.substring(0, 2))
                    .sorted()
                    .collect(Collectors.joining(","));
            System.out.println("Result: " + src);
        }
    };
    final ClassExecutionFilter filter = JvmHelper.createNonJavaExecutionFilter();
    JvmHelper.executeRunnable(runnable, filter, System.out);

A small part of the corresponding instruction-dump (here executing the lambda-expression):

    Enter private static java.lang.String org.rogmann.jsmud.tests17.JsmudExampleMain$1.lambda$0(java.lang.String)
      Label: L463345942
    L 18, Instr 02, ALOAD 0 (move): stack: currLen=0, maxLen=3, types=[], values=[], locals [e2-e4]
    L 18, Instr 03, ICONST_0: stack: currLen=1, maxLen=3, types=[String], values=[e2-e4]
    L 18, Instr 04, ICONST_2: stack: currLen=2, maxLen=3, types=[String, Integer], values=[e2-e4, 0]
    L 18, Instr 05, INVOKEVIRTUAL java/lang/String#substring(II)Ljava/lang/String;: stack: currLen=3, maxLen=3, types=[String, Integer, Integer], values=[e2-e4, 0, 2]
    L 18, Instr 06, ARETURN: stack: currLen=1, maxLen=3, types=[String], values=[e2]

The execution of a JVM is very complex, this interpreter has different limitations (see below).

JvmHelper's methods connectSupplierToDebugger or can be used to connect to a waiting debugger ("Remote Java Application" with "Socket Listen" in an IDE).

    JvmHelper.connectRunnableToDebugger("192.168.1.1", 15000, runnable);

Another example:

    final int a = 34;
    final int b = 55;
    final Supplier<String> supplier = new Supplier<String>() {
        @Override
        public String get() {
            final int sum = a + b;
            return "Sum: " + sum; // Or call some method doing the work of interest.
        }
    };
    final ClassExecutionFilter filter = JvmHelper.createNonJavaExecutionFilter();
    final SourceFileRequester sourceFileRequester = null;
    final DebuggerJvmVisitor visitor = JvmHelper.createDebuggerVisitor(filter,
        supplier.getClass().getClassLoader(), sourceFileRequester);
    final String sum = JvmHelper.connectSupplierToDebugger(visitor, "192.168.1.1", 15000, supplier, String.class);

### Example of a method-call-trace
The included class InstructionVisitor can generate a method-call-trace. The method-call-traces shows the methods called and the number of its executions.

      + public void org.rogmann.jsmud.test.JvmTests.assertEquals(java.lang.String,java.lang.Object,java.lang.Object) 2 of 6
        + public boolean java.util.ArrayList.add(java.lang.Object) 6 of 7
          + private void java.util.ArrayList.ensureCapacityInternal(int) 7 of 7
            + private static int java.util.ArrayList.calculateCapacity(java.lang.Object[],int) 7 of 7
            + private void java.util.ArrayList.ensureExplicitCapacity(int) 7 of 7
              + private void java.util.ArrayList.grow(int) 1 of 1
                + public static java.lang.Object[] java.util.Arrays.copyOf(java.lang.Object[],int) 1 of 1
                  + public static java.lang.Object[] java.util.Arrays.copyOf(java.lang.Object[],int,java.lang.Class) 1 of 1

The method-call-trace together with the detailed (and possibly large!) trace of executed instructions and its stack-traces may give insights a typical debugger-session doesn't give.

## Mode of operation
Jsmud-analysis is written in Java and executes bytecode contained in .class-files by means of [ASM](https://asm.ow2.io/). It operates on classes and instances of objects in the JVM. You can build an object-tree in normal Java and then execute methods with jsmud-analysis using the given object-tree. 

A `NEW`-instruction is implemented by placing an instance of UninitializedInstance on the stack.

### INVOKEDYNAMIC via class-generation
As default a `INVOKEDYNAMIC`-instruction is implemented by generating a class-site class on-the-fly. Depending on the execution-filter the call-site-method will be simulated or executed by the underlying JVM.

### INVOKEDYNAMIC via proxy
Until version 0.2.4 the `INVOKEDYNAMIC`-instruction was implemented by placing a java.lang.reflect.Proxy-instance on the stack. This non-default mode can be enable by setting a JVM-property:

    -Djsmud.CallsiteViaProxy=true.

Corresponding to the Proxy-instance a CallSiteSimulation-instance is stored in a map containing details of the bootstrap-method. Currently java/lang/invoke/LambdaMetafactory-bootstrap-methods are supported only via simulation. The Proxy-instances are detected in INVOKE-instructions. For example the following code can be executed by using these proxies:

    final String listStreamed = list.stream()
    	.filter(s -> s.startsWith("d"))
    	.map(String::toUpperCase)
    	.sorted()
    	.collect(Collectors.joining("-"));

These proxies can't be used in JDK 16 and later when the stream-API is used. Therefore the class-generation had been added as default.

### Reflection
Jsmud-analysis tries to analyze reflection-calls like Method#invoke or Constructor#newInstance.

### JsmudClassLoader: Static initializer and constructors, HCR
Jsmud-analysis operates on classes of the JVM using reflection. Static initializers are executed after loading. The class-loader JsmudClassLoader tries to the manipulate the bytecode of a class so one can step through static initializers.

The class sun.reflect.ReflectionFactory is used to load a class without calling a constructor. This enables jsmud-analysis to step through the constructor. If sun.reflect.ReflectionFactory isn't available the JsmudClassLoader can patch a class providing a default constructor. But sun.reflect.ReflectionFactory is the method more reliable.

The JsmudClassLoader is used to support hot-code-replace (redefine classes) while debugging.

There are restrictions in environments using a lot of different class-loaders (e.g. OSGi) because some class-loaders may not accept classes generated by JsmudClassLoader.

### Caveats
Jsmud-analysis asks the class-loader to get information about the class to be executed. But for example the [WebAppClassLoader](https://github.com/eclipse/jetty.project/blob/jetty-9.4.42.v20210604/jetty-webapp/src/main/java/org/eclipse/jetty/webapp/WebAppClassLoader.java) of Jetty understandably doesn't want to give a webapp its internal classes. You might try to write your own JvmExecutionVisitor treating such cases.

The module-system in newer JVMs doesn't allow reflection in internal JVM-classes: InaccessibleObjectException.

### Classes defined at runtime
When ClassLoader#defineClass is called by an application jsmud-analysis stores the corresponding bytecode in an internal map to support analyzing the execution of classes defined at runtime.

The analysis of classes defined at runtime can be a bit tricky because often reflection and static initializers are involved, too.

#### Example CGLIB
This paragraphs shows an example of analyzing CGLIB.

Sample application:

    public class CglibTest {    
    	public static void main(String[] mainArgs) {
    		final Enhancer enhancer = new Enhancer();
    		enhancer.setSuperclass(ExampleService1.class);
    		enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
    			if ("greeting".equals(method.getName())) {
    				if ("Voldemort".equals(args[0])) {
    					args[0] = "You-Know-Who";
    				}
    			}
    			return proxy.invokeSuper(obj, args);
    		});
    
    		ExampleService1 proxy = (ExampleService1) enhancer.create();
    		System.out.println(proxy.greeting("Harry"));
    		System.out.println(proxy.greeting("Voldemort"));
    		System.out.println(proxy.getLength("Hedwig"));
    	}
    }

Sample class:

    public class ExampleService1 {
        
    	public String greeting(final String name) {
    		return "Hello " + name + '!';
    	}
    
    	public int getLength(final String name) {
    		return name.length();
    	}
    }

The following code analyzes the test-application.

    public class JsmudCglibTest {
    	public static void main(String[] args) {
    		final Runnable runnable = new Runnable() {
    			@Override
    			public void run() {
    				CglibTest.main(null);
    			}
    		};
    		final String packageApplication = Utils.getPackage(CglibTest.class.getName());
    		final ClassExecutionFilter filter = JvmHelper.createNonJavaExecutionFilter();
    		final PrintStream psOut = System.out;
    
    		final boolean dumpJreInstructions = true;
    		final boolean dumpClassStatistic = true;
    		final boolean dumpInstructionStatistic = true;
    		final boolean dumpMethodCallTrace = true;
    		final InstructionVisitorProvider visitorProvider = new InstructionVisitorProvider(psOut, dumpJreInstructions,
    				dumpClassStatistic, dumpInstructionStatistic, dumpMethodCallTrace);
    		visitorProvider.setShowOutput(true);
    		visitorProvider.setShowStatisticsAfterExecution(true);
    
    		final Class<?> classRunnable = runnable.getClass();
    		final ClassLoader classLoader = classRunnable.getClassLoader();
    		final JsmudConfiguration config = new JsmudConfiguration();
    		final JvmInvocationHandler invocationHandler = new JvmInvocationHandlerReflection(filter, config);
    		// Analyze static initializers of CGLIB (net.sf.cglib.*) and the application.
    		Predicate<String> patchFilter = (c -> c.startsWith("net.sf.") || c.startsWith(packageApplication));
    		boolean patchClinit = true;
    		boolean patchInit = false;
    		boolean redefClasses = true;
    		ClassLoader jcl = new JsmudClassLoader(classLoader, config, patchFilter, patchClinit, patchInit, redefClasses);
    		final ClassRegistry registry = new ClassRegistry(filter, config,
    				jcl, visitorProvider, invocationHandler);
    		registry.registerThread(Thread.currentThread());
    		try {
    			final SimpleClassExecutor executor = new SimpleClassExecutor(registry, runnable.getClass(), invocationHandler);
    			final OperandStack stackArgs = new OperandStack(1);
    			stackArgs.push(runnable);
    			try {
    				executor.executeMethod(Opcodes.INVOKEVIRTUAL, JvmHelper.lookup(runnable.getClass(), "run"), "()V", stackArgs);
    			} catch (Throwable e) {
    				throw new RuntimeException("Exception while simulating runnable", e);
    			}
    		}
    		finally {
    			registry.unregisterThread(Thread.currentThread());
    		}
    	}
    }

The statistics dumped by jsmud-analysis show a lot of work in asm and the CodeEmitter:

    Class class org.objectweb.asm.Frame: 519833 instruction-calls
    Class class org.objectweb.asm.ByteVector: 397208 instruction-calls
    Class class org.objectweb.asm.MethodWriter: 321639 instruction-calls
    Class class org.objectweb.asm.SymbolTable: 201775 instruction-calls
    Class class org.objectweb.asm.Type: 122163 instruction-calls
    Class class org.objectweb.asm.Label: 78799 instruction-calls
    Class class org.objectweb.asm.ClassReader: 55166 instruction-calls
    Class class net.sf.cglib.core.CodeEmitter: 46942 instruction-calls
    Class class net.sf.cglib.core.TypeUtils: 19491 instruction-calls
    [...]

There are a lot of integer-operations:

    Instruction 19 ALOAD: 379827 instruction-calls
    Instruction 15 ILOAD: 325424 instruction-calls
    Instruction b4 GETFIELD: 183992 instruction-calls
    Instruction 36 ISTORE: 85323 instruction-calls
    Instruction b6 INVOKEVIRTUAL: 66705 instruction-calls
    Instruction 84 IINC: 50685 instruction-calls
    Instruction a7 GOTO: 47936 instruction-calls
    Instruction 10 BIPUSH: 42959 instruction-calls
    [...]

Example of methods called in a class defined at runtime:

      + public final int org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21.getLength(java.lang.String) 1 of 1
        + public java.lang.Object org.rogmann.jsmud.gen.CallSite1_CglibTest.intercept(java.lang.Object,java.lang.reflect.Method,java.lang.Object[],net.sf.cglib.proxy.MethodProxy) 1 of 3
                + final int org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21.CGLIB$getLength$0(java.lang.String) 1 of 1
                  + public int org.rogmann.jsmud.tests.cglib.ExampleService1.getLength(java.lang.String) 1 of 1

Some instructions at the execution of GetLength:

    L 32, Instr 34, LDC "Hedwig": stack: currLen=2, maxLen=4, types=[PrintStream, ExampleService1$$EnhancerByCGLIB$$3d7dcd21], values=[java.io.PrintStream@5e853265, class org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21(0x742af13a)[java.lang.ClassNotFoundException: java.lang.Object[]]]
    L 32, Instr 35, INVOKEVIRTUAL org/rogmann/jsmud/tests/cglib/ExampleService1#getLength(Ljava/lang/String;)I: stack: currLen=3, maxLen=4, types=[PrintStream, ExampleService1$$EnhancerByCGLIB$$3d7dcd21, String], values=[java.io.PrintStream@5e853265, class org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21(0x742af13a)[java.lang.ClassNotFoundException: java.lang.Object[]], Hedwig]
    Enter public final int org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21.getLength(java.lang.String)
    , Instr 01, GETFIELD org/rogmann/jsmud/tests/cglib/ExampleService1$$EnhancerByCGLIB$$3d7dcd21.CGLIB$CALLBACK_0(Lnet/sf/cglib/proxy/MethodInterceptor;): stack: currLen=1, maxLen=7, types=[ExampleService1$$EnhancerByCGLIB$$3d7dcd21], values=[class org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21(0x742af13a)[java.lang.ClassNotFoundException: java.lang.Object[]]]
    , Instr 02, DUP: stack: currLen=1, maxLen=7, types=[CallSite1_CglibTest], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66]
    , Instr 03, IFNONNULL L1400802531: stack: currLen=2, maxLen=7, types=[CallSite1_CglibTest, CallSite1_CglibTest], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66, org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66]
      Label: L1400802531
    Frame 4: F_SAME1
    , Instr 0b, DUP: stack: currLen=1, maxLen=7, types=[CallSite1_CglibTest], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66]
    , Instr 0c, IFNULL L1404803059: stack: currLen=2, maxLen=7, types=[CallSite1_CglibTest, CallSite1_CglibTest], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66, org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66]
    , Instr 0d, ALOAD 0: stack: currLen=1, maxLen=7, types=[CallSite1_CglibTest], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66]
    , Instr 0e, GETSTATIC org/rogmann/jsmud/tests/cglib/ExampleService1$$EnhancerByCGLIB$$3d7dcd21.CGLIB$getLength$0$Method(Ljava/lang/reflect/Method;): stack: currLen=2, maxLen=7, types=[CallSite1_CglibTest, ExampleService1$$EnhancerByCGLIB$$3d7dcd21], values=[org.rogmann.jsmud.gen.CallSite1_CglibTest@7b8d6c66, class org.rogmann.jsmud.tests.cglib.ExampleService1$$EnhancerByCGLIB$$3d7dcd21(0x742af13a)[java.lang.ClassNotFoundException: java.lang.Object[]]]

Depending of the application examined it may be necessary to filter some classes to prevent them from being analyzed (e.g. logging-calls or classes which can't be analyzed).

## Build and Test
Jsmud-analysis is compiled with Java 8 and ASM, see pom.xml. The execution of Java 11 or Java 17 classes is possible. There are a lot of tests in test-class JvmTests. JUnit-tests can be started with test-class JvmTestsJUnit which executes test-class JvmTests. The junit-tests are generated with test-class GenerateJUnitTests.

## Download
GAV in Maven Central Repository:

    <dependency>
        <groupId>org.rogmann.jsmud</groupId>
        <artifactId>jsmud-analysis</artifactId>
        <version>0.5.1</version>
    </dependency>

A compiled and signed Jar of jsmud-analysis is also available at <a href="http://www.rogmann.org/releases/">http://www.rogmann.org/releases/</a>.

## Support
I wrote this project in my free time and I like my free time so support is given by studying the following links: 
 * [Java bytecode instruction listings](https://en.wikipedia.org/wiki/Java_bytecode_instruction_listings)
 * [The Java Virtual Machine Specification, Java SE 17 Edition](https://docs.oracle.com/javase/specs/jvms/se17/html/index.html)
 * [Java Debug Wire Protocol](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html)
 * [ASM, the all purpose Java bytecode manipulation and analysis framework](https://asm.ow2.io/)

## Changelog
 * V 0.5.1, 2022-01-16: Use sun.reflect.ReflectionFactory to load a class without constructor-execution, support FutureTask, bugfixes (GETFIELD, LDC, INVOKEDYNAMIC, ...), analyze classes defined at runtime.
 * V 0.5.0, 2021-12-25: Added a (pseudo-code)-decompiler to support source-line-aligned debugging of classes without source-code (the decompiler isn't full-fledged, it still displays to GOTO-instructions). Handling of exceptions. Support of super-static.
 * V 0.4.1, 2021-10-28: Simulation of SwitchBootstraps (used in pattern-matching for switch), bugfixes (class-loading, AIOOBE-handling, I2S-instruction, ...), implementation of jdwp-command ObjectReference/SetValues.
 * V 0.4.0, 2021-09-26: Respect several threads, hot-code-replace while debugging.
 * V 0.3.0, 2021-08-30: Switch to using generated class-files as INVOKEDYNAMIC-call-sites. On-the-fly-disassembler for debugging bytecode.
 * V 0.2.4, 2021-08-01: Optional method-call-trace, mock-support of AccessController, classloader-determination in INVOKEDYNAMIC, bug-fixes (see history).
 * V 0.2.3, 2021-07-25: Support of ClassObject/InvokeMethod command in debugger, several bug-fixes (see history).
 * V 0.2.2, 2021-07-18: Use the class-loader of a class or class in context when analyzing new classes.
 * V 0.2.1, 2021-07-11: Added support of java.lang.reflect.Proxy.
 * V 0.2.0, 2021-07-07: Initial public release containing bytecode-interpreter and debugger.
