package org.rogmann.jsmud.vm;

/**
 * Optional interface to provide classes to a class-loader.
 * One usage might be to provide classes in a dex-file.
 */
public interface ClassProvider {

	/**
	 * Checks for a class.
	 * @param name class-name, e.g. "example.android.Activity"
	 * @return provided class or <code>null</code>
	 */
	Class<?> checkForClass(String name);

}
