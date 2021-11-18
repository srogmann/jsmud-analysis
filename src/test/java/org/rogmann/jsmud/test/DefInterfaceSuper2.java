package org.rogmann.jsmud.test;

public interface DefInterfaceSuper2 {
	default String addSuffix(String s) {
		return s + "Super2";
	}

	default String getName() {
		return "DefS2";
	}

}