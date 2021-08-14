package org.rogmann.jsmud.test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.rogmann.jsmud.vm.FrameDisplay;
import org.rogmann.jsmud.vm.OpcodeDisplay;

/**
 * Programm zur Analyse.
 */
public class ClassScanMain {

	/**
	 * Einstiegspunkt
	 * @param args .class-Datei
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			throw new IllegalArgumentException("Usage: .class-file");
		}
		
		final File fileClass = new File(args[0]);
		Files.list(fileClass.getParentFile().toPath()).forEach(path -> System.out.println("Path: " + path));
		byte[] buf;
		try {
			buf = Files.readAllBytes(fileClass.toPath());
		} catch (IOException e) {
			throw new RuntimeException("IO-error while reading " + fileClass, e);
		}
		final ClassReader reader = new ClassReader(buf);
		final ClassNode node = new ClassNode();
		reader.accept(node, ClassReader.SKIP_DEBUG);
		final PrintStream psOut = System.out;
		for (MethodNode method : node.methods) {
			psOut.println("Method: " + method.name);
			psOut.println("  Descriptor: " + method.desc);
			psOut.println("  Signature: " + method.signature);
			psOut.println("  #instructions: " + method.instructions.size());

			final InsnList instrs = method.instructions;
			for (int i = 0; i < instrs.size(); i++) {
				final AbstractInsnNode instr = instrs.get(i);
				final int opcode = instr.getOpcode();
				if (instr instanceof VarInsnNode) {
					VarInsnNode viNode = (VarInsnNode) instr;
					psOut.println(String.format("    Instruction 0x%02x: %s %d (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode),
							Integer.valueOf(viNode.var),
							instr.getClass().getSimpleName()));
				}
				else if (instr instanceof TypeInsnNode) {
					TypeInsnNode tiNode = (TypeInsnNode) instr;
					psOut.println(String.format("    Instruction 0x%02x: %s %s (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode),
							tiNode.desc,
							instr.getClass().getSimpleName()));
				}
				else if (instr instanceof MethodInsnNode) {
					MethodInsnNode miNode = (MethodInsnNode) instr;
					psOut.println(String.format("    Instruction 0x%02x: %s %s#%s#%s (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode),
							miNode.owner, miNode.name, miNode.desc,
							instr.getClass().getSimpleName()));
				}
				else if (instr instanceof InvokeDynamicInsnNode) {
					InvokeDynamicInsnNode idiNode = (InvokeDynamicInsnNode) instr;
					psOut.println(String.format("    Instruction 0x%02x: %s %s / %s / %s / %s (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode),
							idiNode.name, idiNode.desc,
							idiNode.bsm.getOwner(), idiNode.bsm.getName(),
							instr.getClass().getSimpleName()));
					final Object[] bsmArgs = idiNode.bsmArgs;
					if (bsmArgs != null) {
						for (Object oArg : bsmArgs) {
							psOut.println(String.format("      Arg %s: %s", oArg.getClass().getSimpleName(), oArg));
						}
					}
				}
				else if (instr instanceof FieldInsnNode) {
					FieldInsnNode fiNode = (FieldInsnNode) instr;
					psOut.println(String.format("    Instruction 0x%02x: %s %s (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode),
							fiNode.desc,
							instr.getClass().getSimpleName()));
				}
				else if (instr instanceof FrameNode) {
					final FrameNode fn = (FrameNode) instr;
					psOut.println(String.format("    Frame %d: %s", Integer.valueOf(fn.type), FrameDisplay.lookup(fn.type)));
				}
				else {
					psOut.println(String.format("    Instruction 0x%02x: %s (%s)",
							Integer.valueOf(opcode), OpcodeDisplay.lookup(opcode), instr.getClass().getSimpleName()));
				}
			}
			final Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
			final Frame<BasicValue>[] frames;
			try {
				frames = analyzer.analyze(node.name, method);
			} catch (AnalyzerException e) {
				throw new RuntimeException("Analyze-error in " + method.name, e);
			}
			for (final Frame<BasicValue> frame : frames) {
				psOut.println(String.format("Frame: %s (numLocals: %d, stackSize: %d, maxSize: %d)",
						frame, Integer.valueOf(frame.getLocals()),
						Integer.valueOf(frame.getStackSize()), Integer.valueOf(frame.getMaxStackSize())));
			}
		}
	}

}
