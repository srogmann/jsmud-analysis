package org.rogmann.jsmud.dumps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rogmann.jsmud.debugger.JdwpCommand;
import org.rogmann.jsmud.debugger.JdwpCommandSet;

/**
 * Decorates a jdwp-stream recorded by wireshark.
 * 
 * <pre>
 * 00000000  4a 44 57 50 2d 48 61 6e  64 73 68 61 6b 65        JDWP-Han dshake
 *         00000000  4a 44 57 50 2d 48 61 6e  64 73 68 61 6b 65        JDWP-Han dshake
 * 
 * 0000000E  00 00 00 0b 00 00 00 02  00 01 07                 ........ ...
 *         0000002B  00 00 00 1f 00 00 00 02  80 00 00 00 00 00 08 00  ........ ........
 * <pre>
 */
public class WiresharkStreamDecorator {
	/** hex-line, group(1) = indent, group(2) = offset, group(3)...group(18) = hexbytes */
	private static final Pattern P_HEXLINE = Pattern.compile("([ \t]*)([0-9A-F]{8})  ((?:[0-9a-f]{2}  ?){1,16}) .*");

	/**
	 * Entry method
	 * @param args stream-file
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			throw new IllegalArgumentException("Usage: stream-file");
		}
		final File file = new File(args[0]);
		final PrintStream psOut = System.out;
		final Map<String, JdwpParser> map = new HashMap<>(2);
		try (final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			final Map<Integer, JdwpCommandSet> mapReqCmdSet = new HashMap<>();
			final Map<Integer, JdwpCommand> mapReqCmd = new HashMap<>();
			while (true) {
				final String line = br.readLine();
				if (line == null) {
					break;
				}
				final Matcher m = P_HEXLINE.matcher(line);
				if (!m.matches()) {
					psOut.println(line);
					continue;
				}
				final String indent = m.group(1);

				final JdwpParser parser = map.computeIfAbsent(indent, key -> new JdwpParser(indent, psOut,
						mapReqCmdSet, mapReqCmd));
				// final int offset = Integer.parseInt(m.group(2).toLowerCase(), 16);
				final byte[] buf = parseHex(m.group(3).replace(" ", ""));
				parser.addPart(buf, 0, buf.length);
				
				psOut.println(line);
			}
		}
		catch (IOException e) {
			throw new RuntimeException("IO-error while reading " + file, e);
		}
	}

	/**
	 * Parses hexbytes.
	 * @param hexBytes, e.g."416921"
	 * @return byte-array
	 */
	private static byte[] parseHex(String hexBytes) {
		final int len = hexBytes.length() / 2;
		final byte[] buf = new byte[len];
		for (int i = 0; i < len; i++) {
			buf[i] = (byte) Integer.parseInt(hexBytes.substring(2 * i, 2 * i + 2), 16);
		}
		return buf;
	}

}
