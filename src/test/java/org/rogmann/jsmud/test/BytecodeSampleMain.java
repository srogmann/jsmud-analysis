package org.rogmann.jsmud.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.rogmann.jsmud.source.SourceFileWriterDecompile;

public class BytecodeSampleMain {

	/**
	 * Prints the class BytecodeSample.
	 * @param args output-file
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			throw new IllegalArgumentException("Usage: .java-output-file");
		}
		final File fileOutput = new File(args[0]);
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOutput), StandardCharsets.UTF_8))) {
			final Class<?> clazz = BytecodeSample.class;
			SourceFileWriterDecompile.writeSource(clazz, clazz.getClassLoader(), bw);
		}
		catch (IOException e) {
			throw new RuntimeException("IO-exception while writing " + fileOutput, e);
		}
		System.out.println("File written: " + fileOutput);
	}

}
