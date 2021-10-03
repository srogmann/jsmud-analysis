package org.rogmann.jsmud.debugger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.function.Predicate;

import org.rogmann.jsmud.vm.Utils;

/**
 * Generation of source-files into a local directory.
 */
public class SourceFilesLocalDirectory implements SourceFileRequester {
	
	/** class-filter */
	private final Predicate<Class<?>> classFilter;
	/** destination directory */
	private final File dirDest;
	/** extension of generated files (e.g. "java" or "asm") */
	private final String extension;
	/** encoding */
	private final Charset charset;
	/** line-break in generated files */
	private final String lineBreak;

	/**
	 * Constructor
	 * @param classFilter classes to generate sources
	 * @param dirDest destination source-folder
	 * @param extension extension of generated files
	 * @param charset encoding of generated files
	 * @param lineBreak line-break in generated files
	 */
	public SourceFilesLocalDirectory(final Predicate<Class<?>> classFilter,
			final File dirDest, final String extension, final Charset charset, final String lineBreak) {
		this.classFilter = classFilter;
		this.dirDest = dirDest;
		this.extension = extension;
		this.charset = charset;
		this.lineBreak = lineBreak;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isSourceRequested(Class<?> clazzLoaded) {
		return classFilter.test(clazzLoaded);
	}

	/** {@inheritDoc} */
	@Override
	public BufferedWriter createBufferedWriter(Class<?> clazz) throws IOException {
		final String qualifiedName = clazz.getName();
		final int idxName = qualifiedName.lastIndexOf('.');
		final File dirPackage;
		if (idxName >= 0) {
			final String namePackage = qualifiedName.substring(0, idxName);
			dirPackage = new File(dirDest, namePackage.replace('.', File.separatorChar));
		}
		else {
			dirPackage = dirDest; 
		}
		if (!dirPackage.isDirectory()) {
			Files.createDirectories(dirPackage.toPath());
		}
		final File fileSource = new File(dirPackage, Utils.guessSourceFile(clazz, extension));
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileSource), charset));
	}

	/** {@inheritDoc} */
	@Override
	public String lineBreak() {
		return lineBreak;
	}

	/** {@inheritDoc} */
	@Override
	public String getExtension() {
		return extension;
	}

}
