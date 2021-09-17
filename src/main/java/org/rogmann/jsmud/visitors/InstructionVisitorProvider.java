package org.rogmann.jsmud.visitors;

import java.io.PrintStream;

import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.JvmExecutionVisitorProvider;
import org.rogmann.jsmud.vm.VM;

public class InstructionVisitorProvider implements JvmExecutionVisitorProvider {

	/** output-stream */
	protected final PrintStream psOut;

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

	/** thread-display-flag */
	protected boolean showThreadName = false;

	/**
	 * Constructor
	 * @param psOut output-stream
	 * @param dumpJreInstructions show-instructions flag
	 * @param dumpClassStatistic dump class-usage statistics
	 * @param dumpInstructionStatistic dump class-usage statistics
	 * @param dumpMethodCallTrace dump a method-call-trace
	 */
	public InstructionVisitorProvider(final PrintStream psOut,
			final boolean dumpJreInstructions,
			final boolean dumpClassStatistic, final boolean dumpInstructionStatistic,
			final boolean dumpMethodCallTrace) {
		this.psOut = psOut;
		this.dumpJreInstructions = dumpJreInstructions;
		this.dumpClassStatistic = dumpClassStatistic;
		this.dumpInstructionStatistic = dumpInstructionStatistic;
		this.dumpMethodCallTrace = dumpMethodCallTrace;
	}

	/** {@inheritDoc} */
	@Override
	public JvmExecutionVisitor create(final VM vm, final Thread currentThread) {
		final Long threadId = Long.valueOf(currentThread.getId());
		final String threadName = currentThread.getName();
		final MessagePrinter printer = new MessagePrinter() {
			@Override
			public void println(String msg) {
				if (showThreadName) {
					psOut.println(String.format("%d/%s (%s): %s",
							threadId, threadName, Thread.currentThread(), msg));
				}
				else {
					psOut.println(msg);
				}
			}
			@Override
			public void dump(Throwable e) {
				e.printStackTrace();
			}
		};
		final InstructionVisitor visitor = new InstructionVisitor(printer,
				dumpJreInstructions,
				dumpClassStatistic,
				dumpInstructionStatistic,
				dumpMethodCallTrace);
		visitor.setShowOutput(showOutput);
		visitor.setShowStatisticsAfterExecution(showStatisticsAfterExecution);
		return visitor;
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

	/**
	 * Sets if the thread-name should be printed.
	 * @param flag thread-display-flag
	 */
	public void setShowThreadName(final boolean flag) {
		showThreadName = flag;
	}
}
