package org.rogmann.jsmud.source;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Generates a source-zip containing Java-pseudo-code (e.g. "goto" ;-)).
 */
public class SourceZipGenerator {

	/** Suffix ".class" */
	private static final String SUFFIX_CLASS = ".class";
	/** Suffix ".java" */
	private static final String SUFFIX_JAVA = ".java";

	/**
	 * Entry point
	 * @param args src-jar-file dest-zip-file
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			throw new IllegalArgumentException("Usage: jar-file zip-file");
		}
		final File fileJar = new File(args[0]);
		final File fileZip = new File(args[1]);

		// First pass: read inner classes.
		final Map<String, byte[]> mapInnerClasses = readInnerClasses(fileJar);
		final Function<String, ClassNode> innerClassesProvider = createInnerClassesProvider(mapInnerClasses);

		// Second pass: read classes and write output-zip.
		try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(fileJar)))) {
			try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(fileZip)))) {
				while (true) {
					try {
						final JarEntry jarEntry = jis.getNextJarEntry();
						if (jarEntry == null) {
							break;
						}
						if (jarEntry.isDirectory()) {
							continue;
						}
						String resName = jarEntry.getName();
						if (resName.endsWith(SUFFIX_CLASS) && resName.indexOf('$') <= 0) {
							final ClassReader classReader = new ClassReader(jis);
							final ClassNode classNode = new ClassNode();
							classReader.accept(classNode, 0);

							final String destName = resName.substring(0, resName.length() - SUFFIX_CLASS.length()) + SUFFIX_JAVA;
							try {
								SourceFileWriterDecompile sfw = new SourceFileWriterDecompile(SUFFIX_JAVA, classNode, innerClassesProvider);
								final ZipEntry zipEntry = new ZipEntry(destName);
								zos.putNextEntry(zipEntry);

								final SourceBlockList blockList = sfw.getSourceBlockList();
								final List<SourceLine> sourceLines = new ArrayList<>(100);
								blockList.collectLines(sourceLines, 0);
								final boolean respectSourceLineNumbers = true;
								final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
								final boolean dumpLineNumbers = false;
								sfw.writeLines(bw, sourceLines, "    ", System.lineSeparator(),
										respectSourceLineNumbers, dumpLineNumbers);
								bw.flush();
							} catch (IOException e) {
								throw new RuntimeException(String.format("IO-error while writing entry (%s) in (%s)",
										destName, fileZip), e);
							}
						}
					} catch (IOException e) {
						throw new RuntimeException(String.format("IO-error while reading entries in (%s)", fileJar), e);
					}
				}
			}
			catch (IOException e) {
				throw new RuntimeException(String.format("IO-error while writing (%s)", fileZip), e);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-error while reading (%s)", fileJar), e);
		}
	}

	/**
	 * Reads inner classes of a jar-file.
	 * @param fileJar jar-file
	 * @return map from resource-name to class-file
	 */
	static Map<String, byte[]> readInnerClasses(final File fileJar) {
		final Map<String, byte[]> mapInnerClasses = new HashMap<>(100);
		final byte[] buf = new byte[4096];
		try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(fileJar)))) {
			while (true) {
				final JarEntry jarEntry = jis.getNextJarEntry();
				if (jarEntry == null) {
					break;
				}
				if (jarEntry.isDirectory()) {
					continue;
				}
				String name = jarEntry.getName();
				if (name.endsWith(SUFFIX_CLASS) && name.indexOf('$') > 0) {
					final ByteArrayOutputStream baos = new ByteArrayOutputStream(500);
					while (true) {
						final int len = jis.read(buf);
						if (len <= 0) {
							break;
						}
						baos.write(buf, 0, len);
					}
					final String innerClassName = name.substring(0, name.length() - SUFFIX_CLASS.length());
					mapInnerClasses.put(innerClassName, baos.toByteArray());
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-error while reading (%s)", fileJar), e);
		}
		return mapInnerClasses;
	}

	/**
	 * Create an inner-classes-provider.
	 * @param mapInnerClasses map from class-resource-name to class-file
	 * @return inner-classes-provider
	 */
	static Function<String, ClassNode> createInnerClassesProvider(final Map<String, byte[]> mapInnerClasses) {
		final Function<String, ClassNode> innerClassesProvider = (resName -> {
			final byte[] buf = mapInnerClasses.get(resName);
			if (buf == null) {
				throw new IllegalArgumentException("Unknown inner class-resource " + resName);
			}
			final ClassReader classReader;
			try {
				classReader = new ClassReader(new ByteArrayInputStream(buf));
			} catch (IOException e) {
				throw new RuntimeException(String.format("IO-Exception while in-memory-reading %s", resName), e);
			}
			final ClassNode classNode = new ClassNode();
			classReader.accept(classNode, 0);
			return classNode;
		});
		return innerClassesProvider;
	}

}
