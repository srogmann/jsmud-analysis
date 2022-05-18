package org.rogmann.jsmud.test.source;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.rogmann.jsmud.source.SourceBlockList;
import org.rogmann.jsmud.source.SourceFileWriterDecompile;
import org.rogmann.jsmud.source.SourceLine;

/**
 * Generate a source-file (e.g. pseudo-code) of a given .class-file.
 */
public class SourceFileOfClassMain {

	/**
	 * Entry method.
	 * @param args class-file-in java-file-out
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			throw new IllegalArgumentException("Usage: class-file-in java-file-out");
		}
		final File fileIn = new File(args[0]);
		final File fileOut = new File(args[1]);
		final ClassNode classNode;
		try (InputStream is = new BufferedInputStream(new FileInputStream(fileIn))) {
			final ClassReader classReader = new ClassReader(is);
			classNode = new ClassNode();
			classReader.accept(classNode, 0);
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-error while reading %s", fileIn), e);
		}
		final ClassLoader cl = SourceFileOfClassMain.class.getClassLoader();
		final SourceFileWriterDecompile sfw;
		final List<SourceLine> sourceLines;
		try {
			sfw = new SourceFileWriterDecompile("java", classNode, cl);
			
			final SourceBlockList blockList = sfw.getSourceBlockList();
			sourceLines = new ArrayList<>(100);
			blockList.collectLines(sourceLines, 0);
			final StringBuilder sb = new StringBuilder(500);
			dumpStructure(blockList, sb, "### Lines without corrections:");
			blockList.lowerExpectedLines(0);
			dumpStructure(blockList, sb, "### Lines after sinking expected lines:");
			blockList.lowerHeaderLines();
			dumpStructure(blockList, sb, "### Lines after sinking header-lines:");
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-error while generating source of %s", fileIn), e);
		}
		final boolean respectSourceLineNumbers = true;
		final boolean dumpLineNumbers = true;
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOut), StandardCharsets.UTF_8))) {
			sfw.writeLines(bw, sourceLines, "    ", System.lineSeparator(),
					respectSourceLineNumbers, dumpLineNumbers);
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-error while writing %s", fileOut), e);
		}
		final PrintStream psOut = System.out;
		psOut.println("Wrote " + fileOut);

	}

	/**
	 * Dumps the structure of source-blocks and its line-numbers.
	 * @param blockList list of source-blocks
	 * @param sb text-output
	 * @param msg title of dump
	 */
	private static void dumpStructure(final SourceBlockList blockList, final StringBuilder sb, String msg) {
		final PrintStream psOut = System.out;
		psOut.println(msg);
		sb.setLength(0);
		blockList.dumpStructure(sb, 0);
		psOut.println(sb);
	}

}
