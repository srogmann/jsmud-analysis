package org.rogmann.jsmud.test;

import java.io.PrintStream;
import java.util.concurrent.Callable;

import org.rogmann.jsmud.vm.ClassExecutionFilter;
import org.rogmann.jsmud.vm.JvmHelper;

public class MemoryTestMain {

	public static void main(String[] args) {
		final PrintStream psOut = System.out;
		final Callable<Long> callable = new Callable<Long>() {
			@Override
			public Long call() {
				long sum = 0;
				for (int i = 0; i < 10000; i++) {
					final byte[] buf = new byte[1048576];
					sum += buf.length;
				}
				return Long.valueOf(sum);
			}
		};
		final ClassExecutionFilter filter = JvmHelper.createNonJavaExecutionFilter();
		final Long result = JvmHelper.executeCallable(callable, filter, psOut);
		psOut.println("Result: " + result);
	}

}
