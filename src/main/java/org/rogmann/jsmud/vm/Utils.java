package org.rogmann.jsmud.vm;

import org.objectweb.asm.Type;
import org.rogmann.jsmud.source.SourceFileWriter;

/**
 * Utility-methods.
 */
public class Utils {

	/**
	 * Appends a constant value.
	 * @param sb string-builder to be appended
	 * @param cst constant value
	 */
	public static void appendConstant(StringBuilder sb, final Object cst) {
		if (cst instanceof Integer) {
			sb.append(((Integer) cst).intValue());
		}
		else if (cst instanceof String) {
			final String s = (String) cst;
			Utils.appendStringValue(sb, s);
		}
		else if (cst instanceof Long) {
			sb.append(((Long) cst).longValue());
		}
		else if (cst instanceof Float) {
			sb.append(((Float) cst).floatValue());
		}
		else if (cst instanceof Double) {
			sb.append(((Double) cst).doubleValue());
		}
		else if (cst instanceof Type) {
			final Type type = (Type) cst;
			sb.append(SourceFileWriter.simplifyClassName(type));
		}
		else if (cst instanceof Byte) {
			// non cst-type.
			final byte bVal = ((Byte) cst).byteValue();
			sb.append(bVal);
		}
		else if (cst instanceof Boolean) {
			// non cst-type.
			sb.append(((Boolean) cst).booleanValue());
		}
		else if (cst instanceof Character) {
			// non cst-type.
			final char cVal = ((Character) cst).charValue();
			if ((cVal >= ' ' && cVal < 0x80) || (cVal > 0xa0 && cVal < 0x100)) {
				sb.append('\'');
				if (cVal == '\'') {
					sb.append('\\');
				}
				sb.append(cVal).append('\'');
			}
			else {
				sb.append("(char) ");
				sb.append((int) cVal);
			}
		}
		else if (cst instanceof Short) {
			// non cst-type.
			sb.append(((Short) cst).shortValue());
		}
		else {
			throw new JvmException(String.format("Unexpected type (%s): %s",
					cst.getClass(), cst));
		}
	}

	/**
	 * Appends the escaped display-value of a string.
	 * E.g. a linefeed will be appended as "\n".
	 * @param sb string-builder to be appended
	 * @param sRaw raw-value
	 */
	public static void appendStringValue(final StringBuilder sb, final String sRaw) {
		if (sRaw == null) {
			sb.append("null");
		}
		else {
			final int sLen = sRaw.length();
			sb.append('"');
			for (int i = 0; i < sLen; i++) {
				final char c = sRaw.charAt(i);
				if (c == '"') {
					sb.append("\\\"");
				}
				else if (c >= ' ' && c < 0x100) {
					sb.append(c);
				}
				else if (c == '\n') {
					sb.append("\\n");
				}
				else if (c == '\r') {
					sb.append("\\r");
				}
				else if (c == '\t') {
					sb.append("\\t");
				}
				else {
					sb.append(String.format("\\u%04x", Integer.valueOf(c)));
				}
			}
			sb.append('"');
		}
	}

	/**
	 * Gets a class-name by signature
	 * @param signature signature, e.g. "Ljava/lang/Thread;"
	 * @return class-name or <code>null</code>
	 */
	static String getNameFromSignature(String signature) {
		String className = null;
		final int signLen = signature.length();
		if (signLen > 2 && signature.charAt(0) == 'L' && signature.charAt(signLen - 1) == ';') {
			final StringBuilder sb = new StringBuilder(signLen - 2);
			for (int i = 1; i < signLen - 1; i++) {
				final char c = signature.charAt(i);
				if (c == '/') {
					sb.append('.');
				}
				else {
					sb.append(c);
				}
			}
			className = sb.toString();
		}
		return className;
	}

	/**
	 * Gets the outer-name of a class or inner-class.
	 * <ul>
	 * <li>"Enhancer$Key" -&gt; "Enhancer"</li>
	 * <li>"Enhancer$Key$$Outer1$Inner2" -&gt; "Enhancer$Key$$Outer1"</li>
	 * </ul>
	 * @param className class-name
	 * @return outer-name
	 */
	public static String getOuterClassName(final String className) {
		final String classNameOuter = className.replaceFirst("(?<![$])[$][^$](?!.*[$][$]).*", "");
		return classNameOuter;
	}

	/**
	 * Gets the package of a fully qualified class-name.
	 * @param className class-name, e.g. "java.util.List"
	 * @return package, e.g. "java.util"
	 */
	public static String getPackage(final String className) {
		final int idx = className.lastIndexOf('.');
		return (idx < 0) ? "" : className.substring(0, idx); 
	}
	
	/**
	 * Guesses the name of a source-file.
	 * @param classRef class
	 * @param extension (e.g. "java" or "asm")
	 * @return guessed source-file (e.g. .java-name)
	 */
	public static String guessSourceFile(final Class<?> classRef, final String extension) {
		final String className = classRef.getName().replaceFirst(".*[.]", "");
		final String classNameOuter = getOuterClassName(className);
		final String sourceFileGuessed = String.format("%s.%s", classNameOuter, extension);
		return sourceFileGuessed;
	}

	/**
	 * Gets the class-loader of a class. Gets the default class-loader if the class is a system-class. 
	 * @param clazz class
	 * @param clDefault default class-loader
	 * @return class-loader
	 */
	public static ClassLoader getClassLoader(final Class<?> clazz, final ClassLoader clDefault) {
		final ClassLoader cl = clazz.getClassLoader();
		return (cl != null) ? cl : clDefault;
	}
	

}
