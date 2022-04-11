package org.rogmann.jsmud.vm;

import org.objectweb.asm.ClassWriter;

/**
 * Interface of a class-remapper.
 */
public interface ClassRemapper {

	/**
	 * Puts an entry.
	 * @param internalName internal class name of class to be mapped
	 * @param mappedInternalName internal name of mapped class in mapped package
	 */
	void put(final String internalName, final String mappedInternalName);

	/**
	 * Gets the mapped name of a class, if it is a mapped class.
	 * @param className class-name
	 * @return mapped name in case of a mapped class, otherwise <code>null</code>
	 */
	String remapName(final String className);

	/**
	 * Maps a class to another package.
	 * @param classWriter class-writer of unmapped class
	 * @param className name of unmapped class
	 * @return class-writer of mapped class (or same class-writer) 
	 */
	ClassWriter remapClassWriter(ClassWriter classWriter, String className);

}
