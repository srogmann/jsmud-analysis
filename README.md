# jsmud-analysis

JSMUD-analysis (_Java simulator multi-user debugger_) is an embedded interpreter of [Java bytecode](https://en.wikipedia.org/wiki/Java_bytecode) written in Java supplemented by a JDWP-debugger-server. You can use it to debug Java applications running in environments where you don't want to enable a debugger via JVM arguments.

The interpreter uses reflection to work on the original objects so one can build an object-tree using the underlying JVM and then execute code working on that tree using JSMUD. The interpreter uses class-generation to support INVOKEDYNAMIC and inspection of constructors and static initializers.

The purpose is to support the analysis of given Java classes in development stages. The module system in JDK 16 and later restricts reflective access against JDK-classes so their internal methods can't be called by a jsmud outside java.base, see [JEP 396](https://openjdk.java.net/jeps/396) and [JEP 403](https://openjdk.java.net/jeps/403). But for example the following code runs in JDK 11.

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
    final ClassExecutionFilter filter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
    JvmHelper.executeRunnable(runnable, filter, System.out);

A small part of the corresponding instruction-dump:

    Load class: org.rogmann.jsmud.tests.LambdaSimpleMain$1
    Enter private static java.lang.String org.rogmann.jsmud.tests.LambdaSimpleMain$1.lambda$0(java.lang.String)
    , Instruction 02, ALOAD 0: stack: currLen=0, maxLen=3, types=[null, null, null], values=[nul..., nul..., nul...], locals [e2-e4]
    , Instruction 03, ICONST_0: stack: currLen=1, maxLen=3, types=[String, null, null], values=[e2-e4, nul..., nul...], locals [e2-e4]
    , Instruction 04, ICONST_2: stack: currLen=2, maxLen=3, types=[String, Integer, null], values=[e2-e4, 0, nul...], locals [e2-e4]
    , Instruction 05, INVOKEVIRTUAL java/lang/String#substring(II)Ljava/lang/String;: stack: currLen=3, maxLen=3, types=[String, Integer, Integer], values=[e2-e4, 0, 2], locals [e2-e4]
    Load class: java.lang.String
    , Instruction 06, ARETURN: stack: currLen=1, maxLen=3, types=[String, Integer, Integer], values=[e2, 0, 2], locals [e2-e4]


The execution of a JVM is very complex, this interpreter has different limitations (see below).

## Getting started

The class JvmHelper contains helper-methods to start the JVM in simple environments.

The following example starts a Runnable and dumps the execution in System.out. The class-execution-filter createNonJavaButJavaUtilExecutionFilter() discards java.*-classes from being interpreted except java.util.*. The interpretation of java.util.* is necessary for the interpretation of lambda-expressions using the stream-API. 

    final ClassExecutionFilter filter = JvmHelper.createNonJavaButJavaUtilExecutionFilter();
    JvmHelper.executeRunnable(runnable, filter, System.out);

JvmHelper's method connectRunnableToDebugger can be used to connect to a waiting debugger ("Remote Java Application" with "Socket Listen" in an IDE).

### Example of a method-call-trace
The included class InstructionVisitor can generate a method-call-trace:

      + public void org.rogmann.jsmud.test.JvmTests.assertEquals(java.lang.String,java.lang.Object,java.lang.Object) 2 of 6
        + public boolean java.util.ArrayList.add(java.lang.Object) 6 of 7
          + private void java.util.ArrayList.ensureCapacityInternal(int) 7 of 7
            + private static int java.util.ArrayList.calculateCapacity(java.lang.Object[],int) 7 of 7
            + private void java.util.ArrayList.ensureExplicitCapacity(int) 7 of 7
              + private void java.util.ArrayList.grow(int) 1 of 1
                + public static java.lang.Object[] java.util.Arrays.copyOf(java.lang.Object[],int) 1 of 1
                  + public static java.lang.Object[] java.util.Arrays.copyOf(java.lang.Object[],int,java.lang.Class) 1 of 1

## Mode of operation
JSMUD is written in Java and executes bytecode contained in .class-files by means of [ASM](https://asm.ow2.io/). It operates on classes and instances of objects in the JVM. You can build an object-tree in normal Java and then execute methods with JSMUD using the given object-tree. 

A `NEW`-instruction is implemented by placing an instance of UninitializedInstance on the stack.

### INVOKEDYNAMIC via class-generation
As default a `INVOKEDYNAMIC`-instruction is implemented by generating a class-site-class on-the-fly. Depending on the execution-filter the call-site-method will be simulated or executed by the underlying JVM.

### INVOKEDYNAMIC via proxy
Until version 0.2.4 the `INVOKEDYNAMIC`-instruction was implemented by placing a java.lang.reflect.Proxy-instance on the stack. This mode can be enable by setting a JVM-property:

    -Dorg.rogmann.jsmud.vm.MethodFrame.executeAccessControllerNative=true.

Corresponding to the Proxy-instance a CallSiteSimulation-instance is stored in a map containing details of the bootstrap-method. Currently java/lang/invoke/LambdaMetafactory-bootstrap-methods are supported only via simulation. The Proxy-instances are detected in INVOKE-instructions. For example the following code can be executed by using these proxies:

    final String listStreamed = list.stream()
    	.filter(s -> s.startsWith("d"))
    	.map(String::toUpperCase)
    	.sorted()
    	.collect(Collectors.joining("-"));

These proxies can't be used in JDK 16 and later when the stream-API is used. Therefore the class-generation had been added as default.

### JsmudClassLoader: Static initializer and constructors, HCR
JSMUD operates on classes of the JVM using reflection. Static initializers are executed after loading, an empty constructor is needed to instanciate an object. The class-loader JsmusClassLoader tries to the manipulate the bytecode of a class so one can step through static initializers and constructors. 

The JsmudClassLoader is used to support hot-code-replace (redefine classes) while debugging.

### Caveats
JSMUD asks the class-loader to get information about the class to be executed. But for example the [WebAppClassLoader](https://github.com/eclipse/jetty.project/blob/jetty-9.4.42.v20210604/jetty-webapp/src/main/java/org/eclipse/jetty/webapp/WebAppClassLoader.java) of Jetty understandably doesn't want to give a webapp its internal classes. You might try to write your own JvmExecutionVisitor treating such cases.

The module-system in newer JVMs doesn't allow reflection in internal JVM-classes: InaccessibleObjectException.

## Support
I wrote this project in my free time and I like my free time so support is given by studying the following links: 
 * [Java bytecode instruction listings](https://en.wikipedia.org/wiki/Java_bytecode_instruction_listings)
 * [The Java Virtual Machine Specification](https://docs.oracle.com/javase/specs/jvms/se12/html/index.html)
 * [Java Debug Wire Protocol](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html)
 * [ASM, the all purpose Java bytecode manipulation and analysis framework](https://asm.ow2.io/)

## Changelog
 * V 0.4.0, 2021-09-26: Respect several threads, hot-code-replace while debugging.
 * V 0.3.0, 2021-08-30: Switch to using generated class-files as INVOKEDYNAMIC-call-sites. On-the-fly-disassembler for debugging bytecode.
 * V 0.2.4, 2021-08-01: Optional method-call-trace, mock-support of AccessController, classloader-determination in INVOKEDYNAMIC, bug-fixes (see history).
 * V 0.2.3, 2021-07-25: Support of ClassObject/InvokeMethod command in debugger, several bug-fixes (see history).
 * V 0.2.2, 2021-07-18: Use the class-loader of a class or class in context when analyzing new classes.
 * V 0.2.1, 2021-07-11: Added support of java.lang.reflect.Proxy.
 * V 0.2.0, 2021-07-07: Initial release containing bytecode-interpreter and debugger.
 