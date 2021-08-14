package org.rogmann.jsmud.dumps;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a byte array dump (e.g. "buf = [72, 105, 33]") and writes the raw data into a file.
 */
public class ByteArrayWriter {

	/**
	 * main-method.
	 * @param args output-file
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			throw new IllegalArgumentException("Usage: output-file");
		}
		final File fileOut = new File(args[0]);
		
		final StringBuilder sb = new StringBuilder(200);
		final byte[] buf = new byte[500];
		while (true) {
			int len;
			try {
				len = System.in.read(buf);
			} catch (IOException e) {
				throw new RuntimeException("IO-error while reading STDIN", e);
			}
			if (len <= 0) {
				break;
			}
			// ISO-8859-1: We are interested in ASCII only.
			sb.append(new String(buf, 0, len, StandardCharsets.ISO_8859_1));
		}
		final String sBytes = sb.toString();
		final int idxStart = sBytes.indexOf('[');
		if (idxStart == -1) {
			throw new IllegalArgumentException("'[' is missing: " + sBytes);
		}
		final int idxEnd = sBytes.indexOf(']', idxStart + 1);
		if (idxEnd == -1) {
			throw new IllegalArgumentException("']' is missing: " + sBytes);
		}
		final String[] aBytes = sBytes.substring(idxStart + 1, idxEnd).split(",");
		try (final OutputStream os = new BufferedOutputStream(new FileOutputStream(fileOut))) {
			for (String sByte : aBytes) {
				final int b = Integer.parseInt(sByte.trim());
				if (b < -128 || b > 127) {
					throw new IllegalArgumentException("Unexpected byte: " + sByte);
				}
				os.write(b);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("IO-exception while writing (%s)", fileOut), e);
		}
	}

}
