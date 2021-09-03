package org.rogmann.jsmud.visitors;

import java.io.IOException;
import java.io.PrintStream;

import org.rogmann.jsmud.vm.JvmExecutionVisitor;
import org.rogmann.jsmud.vm.JvmExecutionVisitorProvider;

public class InstructionVisitorProvider implements JvmExecutionVisitorProvider {

	/** output-stream */
	private final PrintStream psOut;

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
	public JvmExecutionVisitor create(Thread currentThread) {
		final InstructionVisitor visitor = new InstructionVisitor(psOut,
				dumpJreInstructions,
				dumpClassStatistic,
				dumpInstructionStatistic,
				dumpMethodCallTrace);
		visitor.setShowOutput(showOutput);
		visitor.setShowStatisticsAfterExecution(showStatisticsAfterExecution);
		return visitor;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		// Nothing to do here.
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

}
