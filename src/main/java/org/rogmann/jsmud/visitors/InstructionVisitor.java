package org.rogmann.jsmud.visitors;

import java.io.PrintStream;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.FrameDisplay;
import org.rogmann.jsmud.JvmExecutionVisitor;
import org.rogmann.jsmud.MethodFrame;
import org.rogmann.jsmud.OpcodeDisplay;
import org.rogmann.jsmud.OperandStack;

public class InstructionVisitor implements JvmExecutionVisitor {

	/** output-stream */
	private final PrintStream psOut;

	/** show-instructions flag */
	private final boolean dumpJreInstructions;
	/** dump class-usage statistics */
	private final boolean dumpClassStatistic;
	/** dump class-usage statistics */
	private final boolean dumpInstructionStatistic;
	
	private boolean showOutput = true;

	/** current frame-stack */
	Stack<VisitorFrame> stackFrames = new Stack<>();
	/** details of frame */
	VisitorFrame vFrame;

	/** Number of instruction-calls in class */
	private final Map<Class<?>, AtomicLong> mapClassInstrCount = new HashMap<>();
	/** Map from opcode to number of calls */
	private final Map<Integer, AtomicLong> mapInstrCount = new HashMap<>();

	/**
	 * Constructor
	 * @param psOut outout-stream
	 * @param dumpJreInstructions show-instructions flag
	 * @param dumpClassStatistic dump class-usage statistics
	 * @param dumpInstructionStatistic dump class-usage statistics
	 */
	public InstructionVisitor(final PrintStream psOut,
			final boolean dumpJreInstructions,
			final boolean dumpClassStatistic, final boolean dumpInstructionStatistic) {
		this.psOut = psOut;
		this.dumpJreInstructions = dumpJreInstructions;
		this.dumpClassStatistic = dumpClassStatistic;
		this.dumpInstructionStatistic = dumpInstructionStatistic;
	}

	/** {@inheritDoc} */
	@Override
	public void visitThreadStarted(Thread startedThread) {
		if (showOutput) {
			psOut.println(String.format("Started thread: %s", startedThread.getName()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitLoadClass(Class<?> loadedClass) {
		if (showOutput) {
			psOut.println(String.format("Load class: %s", loadedClass.getName()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodEnter(Class<?> currClass, Executable method, MethodFrame pFrame) {
		if (vFrame != null) {
			stackFrames.add(vFrame);
		}
		vFrame = new VisitorFrame();
		vFrame.clazz = currClass;
		vFrame.isJreClass = currClass.getName().startsWith("java.") || currClass.getName().startsWith("sun.com.");
		if (showOutput) {
			psOut.println(String.format("Enter %s", method));
		}
		vFrame.frame = pFrame;
		vFrame.currLine = -1;
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExit(Class<?> currClass, Executable method, MethodFrame pFrame, Object objReturn) {
		if (showOutput) {
			psOut.println(String.format("Leave %s", method));
		}
		if (stackFrames.size() > 0) {
			vFrame = stackFrames.pop();
		}
		else {
			vFrame = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExitBack(Class<?> currClass, Executable method, MethodFrame frame, Object objReturn) {
		if (showOutput) {
			String sObjReturn = null;
			if (objReturn != null) {
				try {
					sObjReturn = objReturn.toString();
				} catch (Exception e) {
					sObjReturn = String.format("%s(ex/%s)", objReturn.getClass(), e.getMessage());
				}
			}
			psOut.println(String.format("Back in %s with %s", method, sObjReturn));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitInstruction(AbstractInsnNode instr, OperandStack stack, Object[] aLocals) {
		if (instr == null) {
			throw new NullPointerException(String.format("instr == null, stack=%s", stack));
		}
		final int opcode = instr.getOpcode();
		if (opcode == -1) {
			if (instr instanceof LineNumberNode) {
				final LineNumberNode ln = (LineNumberNode) instr;
				vFrame.currLine = ln.line;
			}
		}
		else {
			if (dumpClassStatistic) {
				final AtomicLong counter = mapClassInstrCount.computeIfAbsent(vFrame.clazz, k -> new AtomicLong());
				counter.incrementAndGet();
			}
			if (dumpInstructionStatistic) {
				final AtomicLong counter = mapInstrCount.computeIfAbsent(Integer.valueOf(opcode), k -> new AtomicLong());
				counter.incrementAndGet();
			}
		}
		if (!showOutput) {
			return;
		}
		if (vFrame.isJreClass && !dumpJreInstructions) {
			return;
		}
		final String line = (vFrame.currLine > 0) ? "Line " + vFrame.currLine : "";
		final String sInstruction = displayInstruction(instr);
		psOut.println(String.format("%s, Instruction %02x, %s: %s, locals %s",
				line, (vFrame.frame != null) ? Integer.valueOf(vFrame.frame.instrNum) : null,
				sInstruction, stack, OperandStack.toString(aLocals, aLocals.length)));
		if (instr instanceof LabelNode) {
			final LabelNode label = (LabelNode) instr;
			psOut.println("  Label: " + label.getLabel());
		}
		else if (instr instanceof LineNumberNode) {
			final LineNumberNode lnn = (LineNumberNode) instr;
			psOut.println("  Line: " + lnn.line);
		}
		else if (instr instanceof FrameNode) {
			final FrameNode fn = (FrameNode) instr;
			psOut.println(String.format("Frame %d: %s", Integer.valueOf(fn.type), FrameDisplay.lookup(fn.type)));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void invokeException(Throwable e) {
		if (showOutput) {
			psOut.println(String.format("Exception while executing method: %s", e));
			e.printStackTrace(psOut);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEnter(Object objMonitor) {
		if (showOutput) {
			psOut.println(String.format("monitor-enter: obj.id=0x%x, obj.class=%s, obj=%s",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEntered(Object objMonitor, Integer counter) {
		if (showOutput) {
			psOut.println(String.format("monitor-entered: obj.id=0x%x, obj.class=%s, obj=%s, counter=%d",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor,
					counter));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorExit(Object objMonitor, Integer counter) {
		if (showOutput) {
			psOut.println(String.format("monitor-exit: obj.hash=0x%x, obj.class=%s, obj=%s, counter=%d",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor,
					counter));
		}
	}

	/**
	 * Displays an instruction.
	 * @param instr instruction
	 * @return display-text, e.g. "ASTORE 1"
	 */
	public static String displayInstruction(AbstractInsnNode instr) {
		final String opcodeDisplay = OpcodeDisplay.lookup(instr.getOpcode());
		String addition = "";
		if (instr instanceof VarInsnNode) {
			final VarInsnNode vi = (VarInsnNode) instr;
			addition = String.format(" %d", Integer.valueOf(vi.var));
		}
		else if (instr instanceof FieldInsnNode) {
			final FieldInsnNode fi = (FieldInsnNode) instr;
			addition = String.format(" %s.%s(%s)", fi.owner, fi.name, fi.desc);
		}
		else if (instr instanceof LdcInsnNode) {
			final LdcInsnNode li = (LdcInsnNode) instr;
			addition = String.format(" %s", li.cst);
		}
		else if (instr instanceof MethodInsnNode) {
			final MethodInsnNode mi = (MethodInsnNode) instr;
			addition = String.format(" %s#%s%s", mi.owner, mi.name, mi.desc);
		}
		final String sInstruction = opcodeDisplay + addition;
		return sInstruction;
	}

	/**
	 * Dumps a class-statistic and an instruction-statistic.
	 */
	public void showStatistics() {
		if (dumpClassStatistic) {
			for (final Entry<Class<?>, AtomicLong> entry : sortMap(mapClassInstrCount)) {
				psOut.println(String.format("Class %s: %s instruction-calls", entry.getKey(), entry.getValue()));
			}
		}
		if (dumpInstructionStatistic) {
			for (final Entry<Integer, AtomicLong> entry : sortMap(mapInstrCount)) {
				final Integer opcode = entry.getKey();
				final String opcodeName = OpcodeDisplay.lookup(opcode.intValue());
				psOut.println(String.format("Instruction %02x %s: %s instruction-calls",
						opcode, opcodeName, entry.getValue()));
			}
		}
	}
	
	static <K, V> List<Entry<K, AtomicLong>> sortMap(final Map<K, AtomicLong> map) {
		final List<Entry<K, AtomicLong>> list = new ArrayList<>(map.entrySet());
		list.sort((e1, e2) -> (int) (e2.getValue().get() - e1.getValue().get()));
		return list;
	}
	
	/**
	 * Sets if output via psOut is allowed.
	 * @param flag show-output flag
	 */
	public void setShowOutput(final boolean flag) {
		showOutput = flag;
	}
}
