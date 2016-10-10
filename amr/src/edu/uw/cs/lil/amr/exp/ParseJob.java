package edu.uw.cs.lil.amr.exp;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;

import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExecOutput;
import edu.cornell.cs.nlp.spf.exec.IExecution;
import edu.cornell.cs.nlp.spf.explat.IJobListener;
import edu.cornell.cs.nlp.spf.explat.Job;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.AmrSentenceCollection;
import edu.uw.cs.lil.amr.exec.Exec;
import edu.uw.cs.lil.amr.lambda.convert.LogicalExpressionToAmr;

/**
 * Parsing job. Parses a single sentence with logging and writing the result to
 * the output stream.
 *
 * @author Yoav Artzi
 */
public class ParseJob extends Job {

	public static final ILogger			LOG			= LoggerFactory
			.create(ParseJob.class);
	private static final String			EMPTY_AMR	= "(a / amr-empty)";
	private final boolean				allowSloppy;
	private final Exec					exec;

	private final AmrSentenceCollection	sentences;

	public ParseJob(String id, Set<String> dependencyIds,
			IJobListener jobListener, File outputFile, File logFile,
			AmrSentenceCollection sentences, Exec exec, boolean allowSloppy)
					throws FileNotFoundException {
		super(id, dependencyIds, jobListener, outputFile, logFile);
		this.sentences = sentences;
		this.exec = exec;
		this.allowSloppy = allowSloppy;
	}

	private LogicalExpression doInference(SituatedSentence<AMRMeta> dataItem,
			boolean sloppy) {
		final IExecOutput<LogicalExpression> output = exec.execute(dataItem,
				sloppy);

		LOG.info("%s time: %.3fsec", sloppy ? "Sloppy parsing" : "Parsing",
				output.getExecTime() / 1000.0);

		final List<IExecution<LogicalExpression>> maxes = output
				.getMaxExecutions();
		final List<IExecution<LogicalExpression>> all = output
				.getAllExecutions();
		LOG.info("Generated %d %sderivations and %d max-scoring derivations",
				all.size(), sloppy ? "sloppy " : "", maxes.size());

		LOG.info(() -> {
			if (!maxes.isEmpty()) {
				LOG.info("Max-scoring %sderivations [%d]:",
						sloppy ? "sloppy " : "", maxes.size());
				for (final IExecution<LogicalExpression> derivation : maxes) {
					LOG.info("[%.4f] %s", derivation.score(),
							derivation.toString(true));
					LOG.info("AMR: %s", LogicalExpressionToAmr
							.of(derivation.getResult(), true));
				}
			}
		});

		if (maxes.size() != 1) {
			LOG.info(
					"Multiple or no max-scoring %sderivations [%d], writing an empty AMR to output",
					sloppy ? "sloppy " : "", maxes.size());
			return null;
		} else {
			return maxes.get(0).getResult();
		}
	}

	@Override
	protected void doJob() {
		int itemCounter = 0;
		final long startTime = System.currentTimeMillis();
		for (final SituatedSentence<AMRMeta> dataItem : sentences) {
			LOG.info("%d : ==================", ++itemCounter);
			LOG.info("%s", dataItem);

			// First-pass of inference.
			LogicalExpression result = doInference(dataItem, false);

			// Case first-pass failed (no derivations) and we allow for sloppy
			// inference.
			if (result == null && allowSloppy) {
				result = doInference(dataItem, true);
			}

			// Write output.
			getOutputStream().println(dataItem.getString());
			if (result == null) {
				getOutputStream().println(EMPTY_AMR);
			} else {
				getOutputStream()
						.println(LogicalExpressionToAmr.of(result, true));
			}
			getOutputStream().println();

		}
		LOG.info("=======================");
		LOG.info("=======================");
		LOG.info("Parsing job completed: %.2fsec",
				(System.currentTimeMillis() - startTime) / 1000.0);
	}

}
