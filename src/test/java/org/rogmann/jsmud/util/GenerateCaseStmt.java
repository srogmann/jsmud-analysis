package org.rogmann.jsmud.util;

import java.io.PrintStream;

import org.rogmann.jsmud.OpcodeDisplay;

/**
 * Generates a switch-statement.
 */
public class GenerateCaseStmt {

	/**
	 * Entry-method.
	 * @param args none
	 */
	public static void main(String[] args) {
		PrintStream psOut = System.out;
		psOut.println("switch (opcode) {");
		final int lastOpcode = 199;
		for (int op = 0; op < lastOpcode; op++) {
			psOut.println(String.format("case Opcodes.%s: // 0x%02x",
					OpcodeDisplay.lookup(op), Integer.valueOf(op)));
			psOut.println(String.format("\tthrow new UnsupportedOperationException(\"Opcode 0x%02x (%s) not yet supported.",
					Integer.valueOf(op), OpcodeDisplay.lookup(op)));
			psOut.println("\tbreak;");
		}
		psOut.println("default:");
		psOut.println("	break;");
		psOut.println("}");
	}

}
