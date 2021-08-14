package org.rogmann.jsmud.vm;

/**
 * Utility-methods.
 */
class Utils {

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

}
