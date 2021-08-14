package org.rogmann.jsmud.visitors;

import org.rogmann.jsmud.vm.MethodFrame;

/**
 * Method-frame in a visitor.
 */
class VisitorFrame {

	Class<?> clazz;
	MethodFrame frame;
	boolean isJreClass;
	int currLine = -1;
	int printedLine = -2;

}
