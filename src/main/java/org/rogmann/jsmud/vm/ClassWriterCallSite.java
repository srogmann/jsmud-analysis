package org.rogmann.jsmud.vm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

/**
 * ClassWriter for generating classes whose super-class is {@link Object}.
 */
class ClassWriterCallSite extends ClassWriter {

	/** default class-loader */
	private ClassLoader classLoader;
	/** internal name of class to be generated */
	private String classNameInt;

	/**
	 * Constructs a new {@link ClassWriter} object.
	 * @param flags option flags that can be used to modify the default behavior of this class. Must
	 *     be zero or more of {@link #COMPUTE_MAXS} and {@link #COMPUTE_FRAMES}.
	 * @param classLoader default class-loader
	 * @param classNameInt internal name of the class to be generated
	 */
	public ClassWriterCallSite(final int flags, final ClassLoader classLoader, final String classNameInt) {
		super(flags);
		this.classLoader = classLoader;
		this.classNameInt = classNameInt;
	}

	/**
	 * This method can check the super-class of classNameInt which
	 * isn't inside the classloader yet because of generation-in-progress.
	 * 
	 * @param type1 internal name of first class
	 * @param type2 internal name of second class
	 * @return internal name of common super-class
	 */
	@Override
	protected String getCommonSuperClass(final String type1, final String type2) {
		if (classNameInt.equals(type1) && classNameInt.equals(type2)) {
			return classNameInt;
		}
		if (classNameInt.equals(type1) || classNameInt.equals(type2)) {
			return Type.getInternalName(Object.class);
		}
		return super.getCommonSuperClass(type1, type2);
	}

	@Override
	protected ClassLoader getClassLoader() {
		return classLoader;
	}
	
}
