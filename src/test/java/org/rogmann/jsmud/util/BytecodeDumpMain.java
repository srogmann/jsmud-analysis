package org.rogmann.jsmud.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.debugger.SourceFilesLocalDirectory;
import org.rogmann.jsmud.source.SourceBlockList;
import org.rogmann.jsmud.source.SourceFileWriter;
import org.rogmann.jsmud.source.SourceLine;
import org.rogmann.jsmud.vm.JvmException;

/**
 * Dumps its own bytecode.
 */
public class BytecodeDumpMain {

	/**
	 * Entry method.
	 * @param args output-folder
	 */
	public static void main(String[] args) {
		Predicate<Class<?>> sfFilter = (c -> true);
		File dirDest = new File(args.length > 0 ? args[0] : "/tmp/test-out");
		final SourceFileRequester sfr = new SourceFilesLocalDirectory(sfFilter, dirDest, "java", StandardCharsets.UTF_8, "\n");
		writeBytecode(BytecodeDumpMain.class, sfr);
	}

	static void writeBytecode(final Class<?> clazz, SourceFileRequester sfr) {
		final ClassNode node;
		try (final InputStream is = clazz.getResourceAsStream("/" + clazz.getName().replace('.', '/') + ".class")) {
			final ClassReader reader = new ClassReader(is);
			node = new ClassNode();
			reader.accept(node, 0);
		}
		catch (IOException e) {
			throw new RuntimeException("IO-error while reading " + clazz, e);
		}
		
		final Function<String, ClassNode> innerClassProvider = (internalName -> {
			final Class<?> classInner;
			try {
				classInner = Class.forName(internalName.replace('/', '.'));
			} catch (ClassNotFoundException e) {
				throw new JvmException(String.format("Can't load inner-class (%s)", internalName), e);
			}
			final ClassNode innerClassNode;
			try (final InputStream is = clazz.getResourceAsStream("/" + classInner.getName().replace('.', '/') + ".class")) {
				final ClassReader innerReader = new ClassReader(is);
				innerClassNode = new ClassNode();
				innerReader.accept(innerClassNode, 0);
			}
			catch (IOException e) {
				throw new RuntimeException("IO-error while reading " + classInner, e);
			}
			return innerClassNode;
		});

		try (final BufferedWriter bw = sfr.createBufferedWriter(clazz)) {
			final String extension = sfr.getExtension();
			final String lineBreak = sfr.lineBreak();
			final String indentation = null;
			
			SourceFileWriter sfw = new SourceFileWriter(extension, node, innerClassProvider);
			final List<SourceLine> sourceLines = new ArrayList<>(100);
			final SourceBlockList sourceBlockList = sfw.getSourceBlockList();
			sourceBlockList.collectLines(sourceLines, 0);
			sfw.writeLines(bw, sourceLines, indentation, lineBreak);
		}
		catch (IOException e) {
			throw new RuntimeException("IO-error while writing " + clazz, e);
		}

	}

}
