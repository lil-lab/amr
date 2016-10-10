package edu.uw.cs.lil.amr.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.test.stats.ITestingStatistics;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.counter.Counter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.convert.LogicalExpressionToAmr;

/**
 * SMATCH evaluation statistics.
 *
 * @author Yoav Artzi
 */
public class SmatchStats implements
		ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> {
	public static final ILogger	LOG							= LoggerFactory
			.create(SmatchStats.class);
	private static final String	EMPTY_AMR					= "(a / amr-empty)";
	private static final String	METRIC_NAME					= "smatch";

	private int					counter						= 0;
	private final List<AMRPair>	exactInferenceAmrPairs		= new LinkedList<>();
	/**
	 * Caches the global score, reset to null whenever a new result is recorded.
	 */
	private MetricTriplet		globalScore					= null;
	private MetricTriplet		globalSloppyScore			= null;

	private final int			maxRetries;
	private final String		metricName;
	private final Counter		multipleDerivations			= new Counter(0);
	private final Counter		multipleSloppyDerivations	= new Counter(0);
	private final List<AMRPair>	sloppyInferenceAmrPairs		= new LinkedList<>();
	private final Counter		smatchCalls					= new Counter();
	private final String		smatchPath;
	private final int			smatchRounds;
	private long				smatchTime					= 0;
	private final long			timeout;
	private boolean				underspecified;

	public SmatchStats(String smatchPath, long timeout, int maxRetries,
			int smatchRounds, boolean underspecified) {
		this(smatchPath, timeout, maxRetries, smatchRounds,
				underspecified ? "underspec-" + METRIC_NAME : METRIC_NAME);
		this.underspecified = underspecified;
	}

	public SmatchStats(String smatchPath, long timeout, int maxRetries,
			int smatchRounds, String metricName) {
		this.smatchPath = smatchPath;
		this.metricName = metricName;
		this.timeout = timeout;
		this.maxRetries = maxRetries;
		this.smatchRounds = smatchRounds;
		LOG.info(
				"Init %s: timeout=%d, maxRetries=%d, smatchRounds=%d, smatchPath=%s",
				getClass().getSimpleName(), timeout, maxRetries, smatchRounds,
				smatchPath);
	}

	private static String getAmrReferenceString(LabeledAmrSentence dataItem) {
		if (dataItem.getProperties().containsKey("amr")) {
			return dataItem.getProperties().get("amr");
		} else {
			LOG.warn("Failed to get original AMR, using converted");
			return LogicalExpressionToAmr.of(dataItem.getLabel());
		}
	}

	private static Double getNumber(String line, String expectedPrefix) {
		if (line != null && line.startsWith(expectedPrefix)) {
			try {
				return Double.valueOf(line.substring(expectedPrefix.length()));
			} catch (final NumberFormatException e) {
				LOG.error(
						"Invalid line, failed to convert number: %s (prefix=%s)",
						line, expectedPrefix);
				return null;
			}
		} else {
			LOG.error("Invalid line, unexpected prefix: %s (prefix=%s)", line,
					expectedPrefix);
			return null;
		}
	}

	@Override
	public void recordNoParse(LabeledAmrSentence dataItem) {
		// Reset cache.
		globalScore = null;
		globalSloppyScore = null;

		// Pair the reference AMR with an empty one.
		exactInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
		LOG.info("%s stats -- recording no parse [%d]", metricName,
				exactInferenceAmrPairs.size());
	}

	@Override
	public void recordNoParseWithSkipping(LabeledAmrSentence dataItem) {
		// Reset cache.
		globalScore = null;
		globalSloppyScore = null;

		// Pair the reference ARM with an empty one.
		sloppyInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
		LOG.info("%s stats -- recording no parse with sloppy inference [%d]",
				metricName, sloppyInferenceAmrPairs.size());
	}

	@Override
	public void recordParse(LabeledAmrSentence dataItem,
			LogicalExpression candidate) {
		// Reset cache.
		globalScore = null;
		globalSloppyScore = null;

		final String candidateAmr = LogicalExpressionToAmr.of(underspecified
				? AMRServices.underspecifyAndStrip(candidate) : candidate);
		if (candidateAmr == null) {
			exactInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			sloppyInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			LOG.info(
					"%s stats -- recorded parse failed to convert to AMR, recording no parse instead [%d]",
					metricName, exactInferenceAmrPairs.size());
		} else {
			exactInferenceAmrPairs.add(pair(dataItem, candidateAmr));
			sloppyInferenceAmrPairs.add(pair(dataItem, candidateAmr));
			final MetricTriplet smatchScore = computeSmatch(
					ListUtils.createSingletonList(pair(dataItem, candidateAmr)),
					true);
			if (smatchScore == null) {
				LOG.error(
						"%s stats -- recorded parse, but failed to score it with SMATCH [%d]",
						metricName, exactInferenceAmrPairs.size());
			} else {
				LOG.info("%s stats -- recording parse [%d]: %s", metricName,
						exactInferenceAmrPairs.size(), smatchScore);
			}
		}
	}

	@Override
	public void recordParses(LabeledAmrSentence dataItem,
			List<LogicalExpression> candidates) {
		if (new HashSet<>(underspecified
				? candidates.stream().map(AMRServices::underspecifyAndStrip)
						.collect(Collectors.toList())
				: candidates).size() == 1) {
			LOG.info("%s stats -- multiple identical parses", metricName);
			recordParse(dataItem, candidates.get(0));
		} else {
			// Reset cache.
			globalScore = null;
			globalSloppyScore = null;

			// Multiple parses are treated as no parses. Pair the reference AMR
			// with an empty one. Update both sloppy and exact lists.
			exactInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			sloppyInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			multipleDerivations.inc();
			multipleSloppyDerivations.inc();
			LOG.info("%s stats -- multiple parses, recording as no parse [%d]",
					metricName, exactInferenceAmrPairs.size());
		}
	}

	@Override
	public void recordParsesWithSkipping(LabeledAmrSentence dataItem,
			List<LogicalExpression> candidates) {
		if (new HashSet<>(underspecified
				? candidates.stream().map(AMRServices::underspecifyAndStrip)
						.collect(Collectors.toList())
				: candidates).size() == 1) {
			LOG.info("%s stats -- multiple identical parses with skipping",
					metricName);
			recordParseWithSkipping(dataItem, candidates.get(0));
		} else {
			// Reset cache.
			globalScore = null;
			globalSloppyScore = null;

			// Multiple parses are treated as no parses. Pair the reference AMR
			// with an empty one. Update both sloppy and exact lists.
			sloppyInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			multipleSloppyDerivations.inc();
			LOG.info("%s stats -- multiple parses, recording as no parse [%d]",
					metricName, sloppyInferenceAmrPairs.size());
		}
	}

	@Override
	public void recordParseWithSkipping(LabeledAmrSentence dataItem,
			LogicalExpression candidate) {
		// Reset cache.
		globalScore = null;
		globalSloppyScore = null;

		final String candidateAmr = LogicalExpressionToAmr.of(underspecified
				? AMRServices.underspecifyAndStrip(candidate) : candidate);
		if (candidateAmr == null) {
			sloppyInferenceAmrPairs.add(pair(dataItem, EMPTY_AMR));
			LOG.info(
					"%s stats -- recorded sloppy parse failed to convert to AMR, recording no parse instead [%d]",
					metricName, sloppyInferenceAmrPairs.size());
		} else {
			sloppyInferenceAmrPairs.add(pair(dataItem, candidateAmr));
			final MetricTriplet smatchScore = computeSmatch(
					ListUtils.createSingletonList(pair(dataItem, candidateAmr)),
					true);
			if (smatchScore == null) {
				LOG.error(
						"%s stats -- recorded sloppy parse, but failed to score it with SMATCH [%d]",
						metricName, sloppyInferenceAmrPairs.size());
			} else {
				LOG.info("%s stats -- recording sloppy parse [%d]: %s",
						metricName, sloppyInferenceAmrPairs.size(),
						smatchScore);
			}
		}
	}

	@Override
	public String toString() {
		if (globalScore == null || globalSloppyScore == null) {
			computeGlobalScore();
		}

		final StringBuilder ret = new StringBuilder("=== ").append(metricName)
				.append(" statistics:\n");
		if (globalScore != null) {
			ret.append("Recall: ").append(globalScore.recall).append('\n');
			ret.append("Precision: ").append(globalScore.precision)
					.append('\n');
			ret.append("F1: ").append(globalScore.f1).append('\n');
		} else {
			ret.append("FAILED TO COMPUTE GLOBAL SCORE").append('\n');
		}
		if (globalSloppyScore != null) {
			ret.append("SKIP Recall: ").append(globalSloppyScore.recall)
					.append('\n');
			ret.append("SKIP Precision: ").append(" = ")
					.append(globalSloppyScore.precision).append('\n');
			ret.append("SKIP F1: ").append(globalSloppyScore.f1).append('\n');
		} else {
			ret.append("FAILED TO COMPUTE GLOBAL SLOPPY SCORE").append('\n');
		}
		ret.append(String.format("Smatch calls: %d [mean time: %.3fsec]",
				smatchCalls.value(),
				smatchTime / (double) smatchCalls.value() / 1000.0))
				.append('\n');
		ret.append(String.format(
				"%d samples recorded multiple max-scoring derivations (%d with sloppy inference)",
				multipleDerivations.value(),
				multipleSloppyDerivations.value()));

		return ret.toString();
	}

	@Override
	public String toTabDelimitedString() {
		if (globalScore == null || globalSloppyScore == null) {
			computeGlobalScore();
		}

		final StringBuilder ret = new StringBuilder().append("\tmetric=")
				.append(metricName).append("\t");
		ret.append("recall=")
				.append(globalScore == null ? "NA" : globalScore.recall)
				.append('\t');
		ret.append("precision=")
				.append(globalScore == null ? "NA" : globalScore.precision)
				.append('\t');
		ret.append("f1=").append(globalScore == null ? "NA" : globalScore.f1)
				.append('\t');
		ret.append("skippingRecall=").append(
				globalSloppyScore == null ? "NA" : globalSloppyScore.recall)
				.append('\t');
		ret.append("skippingPrecision=").append(
				globalSloppyScore == null ? "NA" : globalSloppyScore.precision)
				.append('\t');
		ret.append("skippingF1=").append(
				globalSloppyScore == null ? "NA" : globalSloppyScore.f1);
		return ret.toString();
	}

	private void computeGlobalScore() {
		// Keep the files used to compute the global score, so we can access
		// them later.
		globalScore = computeSmatch(exactInferenceAmrPairs, false);
		globalSloppyScore = computeSmatch(sloppyInferenceAmrPairs, false);
	}

	private MetricTriplet computeSmatch(List<AMRPair> amrPairs,
			boolean deleteTempFiles) {
		return computeSmatch(amrPairs, 0, deleteTempFiles);
	}

	private MetricTriplet computeSmatch(List<AMRPair> amrPairs, int retryNumber,
			boolean deleteTempFiles) {
		if (retryNumber > 0) {
			LOG.info("Smatch compute retry #%d", retryNumber);
		}
		try {
			++counter;
			// Write temporary files.
			final File candidateFile = File.createTempFile(
					metricName + counter + "-", "-candidate.txt");
			final File goldFile = File
					.createTempFile(metricName + counter + "-", "-gold.txt");
			try (PrintStream candidateStream = new PrintStream(candidateFile);
					PrintStream goldStream = new PrintStream(goldFile)) {
				for (final AMRPair pair : amrPairs) {
					goldStream.println(pair.gold);
					goldStream.println();
					candidateStream.println(pair.candidate);
					candidateStream.println();
				}
			} catch (final FileNotFoundException e) {
				LOG.error("Failed to write to file: %s",
						e.getLocalizedMessage());
				return null;
			}

			final long startTime = System.currentTimeMillis();

			// Create command line.
			final String cmd = new StringBuilder("python ").append(smatchPath)
					.append(" -r ").append(smatchRounds).append(" --pr ")
					.append("-f ").append(candidateFile.getAbsolutePath())
					.append(" ").append(goldFile.getAbsolutePath()).toString();

			LOG.info("SMATCH command: %s", cmd);

			// Execute SMATCH.
			final Process p = Runtime.getRuntime().exec(cmd);
			try {
				// Scale the wait time with the number of pairs compared.
				p.waitFor(Math.min(timeout * amrPairs.size(), 600000),
						TimeUnit.MILLISECONDS);
			} catch (final InterruptedException e) {
				// Ignore, just log.
				LOG.error("Waiting failed due to interrupt: %s", e);
			}
			if (p.isAlive()) {
				// Case the process is still alive, assume it's frozen. If this
				// is not a retry, try again, otherwise fail.
				p.destroyForcibly();
				if (retryNumber < maxRetries) {
					LOG.warn(
							"Failed to execute smatch command (retry #%d), but retrying: %s",
							retryNumber, cmd);
					return computeSmatch(amrPairs, retryNumber + 1,
							deleteTempFiles);
				} else {
					LOG.error(
							"Failed to execute smatch command on retry (attempt #%d), giving up: %s",
							retryNumber, cmd);
					return null;
				}
			}
			try (final BufferedReader stdInput = new BufferedReader(
					new InputStreamReader(p.getInputStream()));) {
				final Double precision = getNumber(stdInput.readLine(),
						"Precision: ");
				final Double recall = getNumber(stdInput.readLine(),
						"Recall: ");
				final Double f1 = getNumber(stdInput.readLine(),
						"Document F-score: ");

				LOG.info("SMATCH runtime: %.4f",
						(System.currentTimeMillis() - startTime) / 1000.0);

				smatchTime += System.currentTimeMillis() - startTime;
				smatchCalls.inc();

				if (precision != null && recall != null && f1 != null) {
					// Delete only if the command was successful.
					if (deleteTempFiles) {
						candidateFile.delete();
						goldFile.delete();
					}
					final MetricTriplet score = new MetricTriplet(precision,
							recall, f1);
					LOG.info("SMATCH result: %s", score);
					return score;
				} else {
					LOG.error("Invalid output for command: %s", cmd);
					return null;
				}
			}
		} catch (final IOException e) {
			LOG.error("Failed to execute smatch command");
			return null;
		}
	}

	private AMRPair pair(LabeledAmrSentence dataItem, String candidate) {
		if (underspecified) {
			final String goldUnderspec = LogicalExpressionToAmr
					.of(AMRServices.underspecifyAndStrip(dataItem.getLabel()));
			assert goldUnderspec != null : "Failed to convert the underspecified gold label back to AMR: "
					+ dataItem.getLabel();
			return new AMRPair(goldUnderspec, candidate);
		} else {
			return new AMRPair(getAmrReferenceString(dataItem), candidate);
		}
	}

	public static class Creator implements IResourceObjectCreator<SmatchStats> {

		private final String type;

		public Creator() {
			this("test.stats.smatch");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SmatchStats create(Parameters params, IResourceRepository repo) {
			return new SmatchStats(params.getAsFile("smatch").getAbsolutePath(),
					params.getAsLong("timeout", 5000),
					params.getAsInteger("retries", 2),
					params.getAsInteger("rounds", 4),
					params.getAsBoolean("underspec", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, SmatchStats.class)
					.addParam("underspec", Boolean.class,
							"Underspecified SMATCH scoring using the underspecified form of the gold label and the candidate LF (default: false)")
					.addParam("rounds", Integer.class,
							"Nubmer of random restarts when computing smatch (default: 4)")
					.addParam("retries", Integer.class,
							"Max smatch retries (default: 2)")
					.addParam("smatch", File.class,
							"Path to the SMATCH evaluation script")
					.addParam("timeout", Long.class,
							"Timeout for SMATCH process in milliseconds (default: 5000)")
					.setDescription("SMATCH evaluation statistics").build();
		}

	}

	private static class AMRPair {
		private final String	candidate;
		private final String	gold;

		public AMRPair(String gold, String candidate) {
			this.gold = gold;
			this.candidate = candidate;
		}

	}

	private class MetricTriplet {
		private final double	f1;
		private final double	precision;
		private final double	recall;

		public MetricTriplet(double precision, double recall, double f1) {
			this.precision = precision;
			this.recall = recall;
			this.f1 = f1;
		}

		@Override
		public String toString() {
			return String.format("p=%.2f, r=%.2f, f1=%.2f", precision, recall,
					f1);
		}
	}

}
