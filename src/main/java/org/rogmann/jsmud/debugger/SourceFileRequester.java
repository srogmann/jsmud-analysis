package org.rogmann.jsmud.debugger;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Interface to request the on-the-fly-generation of pseudo-byte-code of java-classes.
 */
public interface SourceFileRequester {

	/**
	 * Checks if a source-file should be generated
	 * @param clazzLoaded loaded class
	 * @return <code>true</code> if a source-file is requested
	 */
	boolean isSourceRequested(Class<?> clazzLoaded);

	/**
	 * Creates a buffered writer for writing pseudo-code
	 * @param clazz class
	 * @return writer
	 * @throws IOException in case of an IO-error
	 */
	BufferedWriter createBufferedWriter(Class<?> clazz) throws IOException;

	/**
	 * line-break (CR of CRLF) of generated source-file.
	 * @return line-break
	 */
	String lineBreak();
}
