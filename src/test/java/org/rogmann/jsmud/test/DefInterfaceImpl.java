package org.rogmann.jsmud.test;

public class DefInterfaceImpl implements DefInterface, DefInterfaceSuper2
{
	@Override
	public String addSuffix(String s) {
		return "Impl" + DefInterface.super.getName();
	}

	@Override
	public String getName() {
		return "Impl" + DefInterfaceSuper2.super.getName() + DefInterface.super.addSuffix("B-");
	}

}