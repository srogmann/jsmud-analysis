package org.rogmann.jsmud.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Generated JUnit-test-methods.
 */
public class GenerateJUnitTests {

	/**
	 * Main entry
	 * @param args no arguments
	 */
	public static void main(String[] args) {
		final PrintStream psOut = System.out;
		final Class<?> classJvmTests = JvmTests.class;
		final String resName = classJvmTests.getSimpleName() + ".class";
		try (InputStream is = JvmTests.class.getResourceAsStream(resName)) {
			final ClassReader cr = new ClassReader(is);
			final ClassNode node = new ClassNode();
			cr.accept(node, 0);
			final String descJsmudTest = Type.getDescriptor(JsmudTest.class);
			int orderNo = 0;
			for (final MethodNode methodNode : node.methods) {
				boolean isJsmudTest = false;
				List<AnnotationNode> visibleAnnotations = methodNode.visibleAnnotations;
				if (visibleAnnotations == null) {
					continue;
				}
				for (AnnotationNode ann : visibleAnnotations) {
					if (descJsmudTest.equals(ann.desc)) {
						isJsmudTest = true;
					}
				}
				if (!isJsmudTest) {
					continue;
				}
				psOut.println(String.format("\t/** JUnit-Test of method {@link %s#%s()} */",
						classJvmTests.getSimpleName(), methodNode.name));
				psOut.println("\t@Test");
				psOut.println(String.format("\t@Order(%d)", Integer.valueOf(++orderNo)));
				psOut.println(String.format("\tpublic void test%s() {", toUpperFirst(methodNode.name)));
				psOut.println("\t\tfinal JvmTests jvmTests = new JvmTests();");
				psOut.println("\t\tfinal Runnable runnable = new Runnable() {");
				psOut.println("\t\t\t@Override");
				psOut.println("\t\t\tpublic void run() {");
				psOut.println(String.format("\t\t\t\tjvmTests.%s();", methodNode.name));
				psOut.println("\t\t\t}");
				psOut.println("\t\t};");
				psOut.println("\t\texecute(runnable);");
				psOut.println("\t}");
				psOut.println();
			}
		}
		catch (IOException e) {
			throw new RuntimeException("IO-error while reading " + resName, e);
		}
	}

	/**
	 * Changes the first letter of a name into upper-case.
	 * @param s name
	 * @return name, first letter upper-case
	 */
	static String toUpperFirst(final String s) {
		return new StringBuilder(s.length())
			.append(Character.toUpperCase(s.charAt(0)))
			.append(s.substring(1, s.length()))
			.toString();
	}

}
