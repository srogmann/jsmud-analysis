package org.rogmann.jsmud.events;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.rogmann.jsmud.debugger.DebuggerException;

/**
 * Case ClassMatch or ClassExclude.
 */
public class JdwpModifierClassMatch extends JdwpEventModifier {

	/** class-pattern */
	private final Pattern pClassPattern;

	/**
	 * Constructor
	 * @param modKind CLASS_MATCH or CLASS_EXCLUDE
	 * @param classPattern pattern of classes to be matched
	 */
	public JdwpModifierClassMatch(final ModKind modKind, final String classPattern) {
		super(modKind);
		if (modKind != ModKind.CLASS_MATCH && modKind != ModKind.CLASS_EXCLUDE) {
			throw new DebuggerException("Invalid modkind in classPattern-modifier: " + modKind);
		}
		final String regExp = classPattern.replace("$", "\\$").replace("*", ".*");
		try {
			pClassPattern = Pattern.compile(regExp);
		} catch (PatternSyntaxException e) {
			throw new DebuggerException(String.format("Unexpected class-pattern (%s) in %s-modifier",
					classPattern, modKind), e);
		}
	}

	/**
	 * Gets the class-pattern.
	 * @return pattern
	 */
	public Pattern getClassPattern() {
		return pClassPattern;
	}
}
