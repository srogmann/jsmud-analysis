package org.rogmann.jsmud.visitors;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rogmann.jsmud.vm.FrameDisplay;
import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.MethodFrame;
import org.rogmann.jsmud.vm.OpcodeDisplay;
import org.rogmann.jsmud.vm.OperandStack;
import org.rogmann.jsmud.vm.Utils;

public class InstructionVisitor implements JvmExecutionVisitor {

	/** message-printer */
	private final MessagePrinter printer;

	/** show-instructions flag */
	private final boolean dumpJreInstructions;
	/** dump class-usage statistics */
	private final boolean dumpClassStatistic;
	/** dump class-usage statistics */
	private final boolean dumpInstructionStatistic;
	/** dump method-call-trace */
	private final boolean dumpMethodCallTrace;
	
	/** output-allowed flag */
	private boolean showOutput = true;

	/** statistics-flag */
	private boolean showStatisticsAfterExecution = true;

	/** current frame-stack */
	Stack<VisitorFrame> stackFrames = new Stack<>();
	/** details of frame */
	VisitorFrame vFrame;

	/** Number of instruction-calls in class */
	private final Map<Class<?>, AtomicLong> mapClassInstrCount = new HashMap<>();
	/** Map from opcode to number of calls */
	private final Map<Integer, AtomicLong> mapInstrCount = new HashMap<>();

	/** Map from method to number of calls */
	private final Map<String, Long> mapMethodCount = new HashMap<>();
	/** Map from method to level */
	private final Map<String, Integer> mapMethodTrace = new LinkedHashMap<>();
	/** Map from method-call to level */
	private final Map<String, Integer> mapMethodCallTrace = new LinkedHashMap<>();
	/** Map from method and called method to number of calls */
	private final Map<String, Long> mapMethodCallCount = new HashMap<>();
	/** level of method in call-stack */
	private int level = 0;

	/** opcode of previous instruction (-1 if none) */
	private int prevOpcode;

	/**
	 * Constructor
	 * @param printer message-printer
	 * @param dumpJreInstructions show-instructions flag
	 * @param dumpClassStatistic dump class-usage statistics
	 * @param dumpInstructionStatistic dump class-usage statistics
	 * @param dumpMethodCallTrace dump a method-call-trace
	 */
	public InstructionVisitor(final MessagePrinter printer,
			final boolean dumpJreInstructions,
			final boolean dumpClassStatistic, final boolean dumpInstructionStatistic,
			final boolean dumpMethodCallTrace) {
		this.printer = printer;
		this.dumpJreInstructions = dumpJreInstructions;
		this.dumpClassStatistic = dumpClassStatistic;
		this.dumpInstructionStatistic = dumpInstructionStatistic;
		this.dumpMethodCallTrace = dumpMethodCallTrace;
	}

	/** {@inheritDoc} */
	@Override
	public void visitThreadStarted(Thread startedThread) {
		if (showOutput) {
			printer.println(String.format("Started thread: %s", startedThread.getName()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitLoadClass(Class<?> loadedClass) {
		if (showOutput) {
			printer.println(String.format("Load class: %s", loadedClass.getName()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodEnter(Class<?> currClass, Executable method, MethodFrame pFrame) {
		final VisitorFrame parentFrame = (vFrame != null) ? vFrame : null;
		if (vFrame != null) {
			stackFrames.add(vFrame);
		}
		vFrame = new VisitorFrame();
		vFrame.clazz = currClass;
		vFrame.isJreClass = currClass.getName().startsWith("java.") || currClass.getName().startsWith("sun.com.");
		if (showOutput) {
			printer.println(String.format("Enter %s", method));
		}
		vFrame.frame = pFrame;
		vFrame.currLine = -1;
		level++;
		prevOpcode = -1;
		if (dumpMethodCallTrace) {
			final String nameMethod = method.toString();
			mapMethodCount.compute(nameMethod, (key, cnt) -> (cnt == null) ? Long.valueOf(1) : Long.valueOf(cnt.longValue() + 1));
			mapMethodTrace.putIfAbsent(nameMethod, Integer.valueOf(level));
			final String keyCallerAndCalled;
			if (parentFrame != null) {
				keyCallerAndCalled = parentFrame.frame.getMethod().toString() + "_#_" + nameMethod;
			}
			else {
				keyCallerAndCalled = nameMethod;
			}
			mapMethodCallTrace.putIfAbsent(keyCallerAndCalled, Integer.valueOf(level));
			mapMethodCallCount.compute(keyCallerAndCalled, (key, cnt) -> (cnt == null) ? Long.valueOf(1) : Long.valueOf(cnt.longValue() + 1));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMethodExit(Class<?> currClass, Executable method, MethodFrame pFrame, Object objReturn) {
		if (showOutput) {
			printer.println(String.format("Leave %s", method));
		}
		if (stackFrames.size() > 0) {
			vFrame = stackFrames.pop();
		}
		else {
			vFrame = null;
		}
		level--;
		prevOpcode = -1;
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
			printer.println(String.format("Back in %s with %s", method, sObjReturn));
		}
		prevOpcode = -1;
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
			else if (instr instanceof LabelNode) {
				final LabelNode label = (LabelNode) instr;
				if (showOutput) {
					printer.println("  Label: " + label.getLabel());
				}
			}
			else if (instr instanceof FrameNode) {
				final FrameNode fn = (FrameNode) instr;
				if (showOutput) {
					printer.println(String.format("Frame %d: %s", Integer.valueOf(fn.type), FrameDisplay.lookup(fn.type)));
				}
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
		if (opcode != -1) {
			final String line = (vFrame.currLine > 0) ? "L " + vFrame.currLine : "";
			final String sInstruction = displayInstruction(instr, vFrame.frame.getMethodNode());
			final String sLocals;
			if (prevOpcode == -1 || prevOpcode == Opcodes.ASTORE
					|| prevOpcode == Opcodes.DSTORE || prevOpcode == Opcodes.FSTORE
					|| prevOpcode == Opcodes.IINC
					|| prevOpcode == Opcodes.ISTORE || prevOpcode == Opcodes.LSTORE) {
				sLocals = ", locals " + OperandStack.toString(aLocals, aLocals.length - 1);
			}
			else {
				sLocals = "";
			}
			printer.println(String.format("%s, Instr %02x, %s: %s%s",
					line, (vFrame.frame != null) ? Integer.valueOf(vFrame.frame.instrNum) : null,
					sInstruction, stack, sLocals));
			prevOpcode = opcode;
		}
	}

	/** {@inheritDoc} */
	@Override
	public Object visitFieldAccess(final int opcode, final Object owner, final Field field, final Object value) {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public void invokeException(Throwable e) {
		if (showOutput) {
			printer.println(String.format("Exception while executing method: %s", e));
			printer.dump(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEnter(Object objMonitor) {
		if (showOutput) {
			printer.println(String.format("monitor-enter: obj.id=0x%x, obj.class=%s, obj=%s",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorEntered(Object objMonitor, Integer counter) {
		if (showOutput) {
			printer.println(String.format("monitor-entered: obj.id=0x%x, obj.class=%s, obj=%s, counter=%d",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor,
					counter));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visitMonitorExit(Object objMonitor, Integer counter) {
		if (showOutput) {
			printer.println(String.format("monitor-exit: obj.hash=0x%x, obj.class=%s, obj=%s, counter=%d",
					Integer.valueOf(System.identityHashCode(objMonitor)),
					(objMonitor != null) ? objMonitor.getClass().getName() : null, objMonitor,
					counter));
		}
	}

	/**
	 * Displays an instruction.
	 * @param instr instruction
	 * @param methodNode optional method-node (used to display local variables in variable instructions)
	 * @return display-text, e.g. "ASTORE 1"
	 */
	public static String displayInstruction(final AbstractInsnNode instr, final MethodNode methodNode) {
		final String opcodeDisplay = OpcodeDisplay.lookup(instr.getOpcode());
		String addition = "";
		switch (instr.getType()) {
			case AbstractInsnNode.INSN:
			{
				// instruction-node
				break;
			}
			case AbstractInsnNode.INT_INSN:
			{
				final IntInsnNode iin = (IntInsnNode) instr;
				addition = String.format(" %d", Integer.valueOf(iin.operand));
				break;
			}
			case AbstractInsnNode.VAR_INSN:
			{
				final VarInsnNode vi = (VarInsnNode) instr;
				addition = String.format(" %d", Integer.valueOf(vi.var));
				if (methodNode != null && methodNode.localVariables != null) {
						// && vi.var < methodNode.localVariables.size()) {
					LocalVariableNode varNode = null;
					for (LocalVariableNode varNodeLoop : methodNode.localVariables) {
						if (varNodeLoop.index == vi.var) {
							varNode = varNodeLoop;
							break;
						}
					}
					if (varNode != null) {
						addition += String.format(" (%s)", varNode.name);
					}
				}
				break;
			}
			case AbstractInsnNode.FIELD_INSN:
			{
				final FieldInsnNode fi = (FieldInsnNode) instr;
				addition = String.format(" %s.%s(%s)", fi.owner, fi.name, fi.desc);
				break;
			}
			case AbstractInsnNode.LDC_INSN:
			{
				final LdcInsnNode li = (LdcInsnNode) instr;
				if (li.cst instanceof String) {
					final String sRaw = (String) li.cst;
					final int sLen = sRaw.length();
					final StringBuilder sb = new StringBuilder(sLen + 5);
					sb.append(' ');
					Utils.appendStringValue(sb, sRaw);
					addition = sb.toString();
				}
				else {
					addition = " " + li.cst;
				}
				break;
			}
			case AbstractInsnNode.IINC_INSN:
			{
				final IincInsnNode ii = (IincInsnNode) instr;
				addition = String.format(" %d, local[%d]", Integer.valueOf(ii.incr), Integer.valueOf(ii.var));
				break;
			}
			case AbstractInsnNode.JUMP_INSN:
			{
				final JumpInsnNode ji = (JumpInsnNode) instr;
				addition = String.format(" %s", ji.label.getLabel());
				break;
			}
			case AbstractInsnNode.METHOD_INSN:
			{
				final MethodInsnNode mi = (MethodInsnNode) instr;
				addition = String.format(" %s#%s%s", mi.owner, mi.name, mi.desc);
				break;
			}
			case AbstractInsnNode.MULTIANEWARRAY_INSN:
			{
				final MultiANewArrayInsnNode manai = (MultiANewArrayInsnNode) instr;
				addition = String.format(" %d, %s", Integer.valueOf(manai.dims), manai.desc);
				break;
			}
			case AbstractInsnNode.TYPE_INSN:
			{
				final TypeInsnNode ti = (TypeInsnNode) instr;
				addition = String.format(" %s", ti.desc);
				break;
			}
			case AbstractInsnNode.LABEL:
			{
				final LabelNode label = (LabelNode) instr;
				addition = String.format(" Label %s", label.getLabel());
				break;
			}
			case AbstractInsnNode.FRAME:
			{
				final FrameNode frame = (FrameNode) instr;
				addition = String.format(" Frame (locals %s)", frame.local);
				break;
			}
			case AbstractInsnNode.LINE:
			{
				final LineNumberNode line = (LineNumberNode) instr;
				addition = String.format(" LineNumber %s", Integer.valueOf(line.line));
				break;
			}
			default:
			{
				addition = String.format(" Type_%02d", Integer.valueOf(instr.getType()));
				break;
			}
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
				printer.println(String.format("Class %s: %s instruction-calls", entry.getKey(), entry.getValue()));
			}
		}
		if (dumpInstructionStatistic) {
			for (final Entry<Integer, AtomicLong> entry : sortMap(mapInstrCount)) {
				final Integer opcode = entry.getKey();
				final String opcodeName = OpcodeDisplay.lookup(opcode.intValue());
				printer.println(String.format("Instruction %02x %s: %s instruction-calls",
						opcode, opcodeName, entry.getValue()));
			}
		}
		if (dumpMethodCallTrace) {
			printer.println("Method-call-trace:");
			for (Entry<String, Integer> entry : mapMethodCallTrace.entrySet()) {
				final String nameCallerAndCalled = entry.getKey();
				final String nameCalled = nameCallerAndCalled.replaceFirst(".*_#_", "");
				final int level = entry.getValue().intValue();
				final StringBuilder sb = new StringBuilder(50);
				for (int i = 0; i < level; i++) {
					sb.append(' ').append(' ');
				}
				sb.append('+').append(' ');
				sb.append(nameCalled);
				sb.append(' ');
				final Long cntCall = mapMethodCallCount.get(nameCallerAndCalled);
				sb.append(cntCall);
				sb.append(" of ");
				sb.append(mapMethodCount.get(nameCalled));
				printer.println(sb.toString());
			}
		}
	}
	
	/**
	 * Gets the entries of a map to AtomicLong sorted by long-value.
	 * @param map map to be sorted
	 * @param <K> type of keys
	 * @return sorted list of entries
	 */
	static <K> List<Entry<K, AtomicLong>> sortMap(final Map<K, AtomicLong> map) {
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

	/**
	 * Sets if statistics after execution show be displayed.
	 * @param flag statistics-flag
	 */
	public void setShowStatisticsAfterExecution(final boolean flag) {
		showStatisticsAfterExecution = flag;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (showStatisticsAfterExecution) {
			showStatistics();
		}
	}
}
