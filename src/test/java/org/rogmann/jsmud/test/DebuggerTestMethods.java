package org.rogmann.jsmud.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DebuggerTestMethods {

	public static void main(String[] args) {
		//localVariables();
		stepIntoSSLContext();
	}

	public static int localVariables() {
		int a = 1;
		{
			char b = ' ';
			a += b;
		}
		{
			short b = (short) 32;
			a += b;
		}
		{
			int b = 32;
			a += b;
		}
		return a;
	}

	public static void stepIntoSSLContext() {
		final Class<?> classDepr;
		try {
			classDepr = Class.forName("com.sun.net.ssl.SSLContext");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't load deprecated class", e);
		}
		try {
			final Method method = classDepr.getDeclaredMethod("getInstance", String.class);
			method.invoke(classDepr, "INVALID_TLS");
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Reflection-error while executing getInstance", e);
		}
	}

}
