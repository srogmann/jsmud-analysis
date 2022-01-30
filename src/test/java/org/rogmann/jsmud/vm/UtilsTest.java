/**
 * 
 */
package org.rogmann.jsmud.vm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

/**
 * JUnit-Tests of class {@link Utils}.
 */
@SuppressWarnings("static-method")
class UtilsTest {

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#appendConstant(java.lang.StringBuilder, java.lang.Object)}.
	 */
	@Test
	void testAppendConstant() {
		final StringBuilder sb = new StringBuilder(30).append('#');
		Utils.appendConstant(sb, Integer.valueOf(5));
		Assertions.assertEquals("#5", sb.toString());
		sb.append(", ");
		Utils.appendConstant(sb, Character.valueOf('a'));
		Assertions.assertEquals("#5, 'a'", sb.toString());
		sb.append(", ");
		Utils.appendConstant(sb, Type.getType(String.class));
		Assertions.assertEquals("#5, 'a', String", sb.toString());
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#appendStringValue(java.lang.StringBuilder, java.lang.String)}.
	 */
	@Test
	void testAppendStringValue() {
		final StringBuilder sb = new StringBuilder(40).append('#');
		Utils.appendStringValue(sb, "text");
		Assertions.assertEquals("#\"text\"", sb.toString());
		sb.append(',');
		Utils.appendStringValue(sb, "\r\nline2");
		Assertions.assertEquals("#\"text\",\"\\r\\nline2\"", sb.toString());
		sb.append(',');
		Utils.appendStringValue(sb, "\t\".€.\"");
		Assertions.assertEquals("#\"text\",\"\\r\\nline2\",\"\\t\\\".\\u20ac.\\\"\"", sb.toString());
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#getNameFromSignature(java.lang.String)}.
	 */
	@Test
	void testGetNameFromSignature() {
		Assertions.assertEquals("java.lang.Thread", Utils.getNameFromSignature("Ljava/lang/Thread;"));
		Assertions.assertEquals(null, Utils.getNameFromSignature("I"));
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#getOuterClassName(java.lang.String)}.
	 */
	@Test
	void testGetOuterClassName() {
		Assertions.assertEquals("java.lang.String", Utils.getOuterClassName("java.lang.String"));
		Assertions.assertEquals("example.Outer", Utils.getOuterClassName("example.Outer$Inner1"));
		Assertions.assertEquals("Enhancer$Key$$Outer1", Utils.getOuterClassName("Enhancer$Key$$Outer1$Inner2"));
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#getPackage(java.lang.String)}.
	 */
	@Test
	void testGetPackage() {
		Assertions.assertEquals("java.lang", Utils.getPackage("java.lang.String"));
		Assertions.assertEquals("example", Utils.getPackage("example.Outer$Inner1"));
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#guessSourceFile(java.lang.Class, java.lang.String)}.
	 */
	@Test
	void testGuessSourceFile() {
		Assertions.assertEquals("String.java", Utils.guessSourceFile(String.class, "java"));
	}

	/**
	 * Test method for {@link org.rogmann.jsmud.vm.Utils#getClassLoader(java.lang.Class, java.lang.ClassLoader)}.
	 */
	@Test
	void testGetClassLoader() {
		Assertions.assertEquals(Utils.class.getClassLoader(), Utils.getClassLoader(Utils.class, null));
	}

}
