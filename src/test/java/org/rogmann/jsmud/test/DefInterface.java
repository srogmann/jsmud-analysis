package org.rogmann.jsmud.test;

public interface DefInterface extends DefInterfaceSuper {
	@Override
	default String addSuffix(String s) {
		return s + "Child";
	}

}