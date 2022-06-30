package org.rogmann.jsmud.source;

import java.util.Collection;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.objectweb.asm.Type;
import org.rogmann.jsmud.vm.Utils;

/**
 * This class supports in rendering class-names and collecting import-statements.
 */
public class SourceNameRenderer {

	/** map from imported class-names to simple class-name */
	private final SortedMap<String, String> mapClassNames = new TreeMap<>();

	/** map from simple class-name to imported class-names */
	private final SortedMap<String, String> mapSimpleClassNames = new TreeMap<>();

	/** Package of the class having import-statements */
	private final String currentPackage;
	
	/** filter which gives <code>true</code> if a class-name may be imported */
	private final Predicate<String> filter;

	/**
	 * Constructor
	 * @param classPackage package of the class
	 * @param filter give <code>true</code> if a class-name may be imported
	 */
	public SourceNameRenderer(final String classPackage, final Predicate<String> filter) {
		this.currentPackage = classPackage;
		this.filter = filter;
	}

	/**
	 * Creates a list of imported class-names.
	 * @return list of fully qualified names
	 */
	public Collection<String> getImportedClassNames() {
		final SortedSet<String> collImport = new TreeSet<String>();
		for (String className : mapClassNames.keySet()) {
			if (!currentPackage.equals(Utils.getPackage(className))) {
				collImport.add(className);
			}
		}
		return collImport;
	}

	/**
	 * Renders a type-name.
	 * @param type class-type
	 * @return fully qualified name or simple-name (with import-statement)
	 */
	public String renderType(final Type type) {
		final String className = SourceFileWriter.simplifyClassName(type);
		return renderClassName(className);
	}
	
	/**
	 * Renders a type-name.
	 * @param className class-name, e.g. "java.util.List"
	 * @return fully qualified name or simple-name (with import-statement)
	 */
	public String renderClassName(final String className) {
		String nameRendered;
		if (className.indexOf('.') < 0 || !filter.test(className)) {
			nameRendered = className;
		}
		else {
			nameRendered = mapClassNames.get(className);
			if (nameRendered == null) {
				final int idx = className.lastIndexOf('.');
				final String simpleName = className.substring(idx + 1);
				if (mapSimpleClassNames.containsKey(simpleName)) {
					// The simple name is used in another package.
					nameRendered = className;
				}
				else {
					nameRendered = simpleName;
					mapClassNames.put(className, simpleName);
					mapSimpleClassNames.put(simpleName, className);
				}
			}
		}
		return nameRendered;
	}
}
