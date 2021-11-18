package org.rogmann.jsmud.test;

public interface DefInterfaceSuper {
	default String addSuffix(String s) {
		return s + "Super";
	}

	default String getName() {
		return "DefS";
	}

}