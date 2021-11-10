package org.rogmann.jsmud.vm;

/**
 * Utility-methods.
 */
public class Utils {

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
	 * Guesses the name of a source-file.
	 * @param classRef class
	 * @param extension (e.g. "java" or "asm")
	 * @return guessed source-file (e.g. .java-name)
	 */
	public static String guessSourceFile(final Class<?> classRef, final String extension) {
		final String className = classRef.getName().replaceFirst(".*[.]", "");
		final String classNameOuter = className.replaceFirst("[$].*", "");
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
