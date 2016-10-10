package edu.uw.cs.lil.amr.test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExec;
import edu.cornell.cs.nlp.spf.exec.IExecOutput;
import edu.cornell.cs.nlp.spf.exec.IExecution;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.test.exec.ExecTester;
import edu.cornell.cs.nlp.spf.test.stats.ITestingStatistics;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.filter.FilterUtils;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.exec.Exec;

public class AmrExecTester extends
		ExecTester<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> {

	public AmrExecTester(IFilter<SituatedSentence<AMRMeta>> skipParsingFilter) {
		super(skipParsingFilter);
	}

	@Override
	protected void test(int itemCounter, LabeledAmrSentence dataItem,
			IExec<SituatedSentence<AMRMeta>, LogicalExpression> exec,
			ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> stats) {
		final List<IExecution<LogicalExpression>> maxScoringExecutions;

		LOG.info("%d : ==================", itemCounter);
		LOG.info("%s", dataItem);

		// Try a simple model parse
		final IExecOutput<LogicalExpression> execOutput = exec
				.execute(dataItem.getSample());
		LOG.info("Test execution time %.2f", execOutput.getExecTime() / 1000.0);

		final List<IExecution<LogicalExpression>> bestExecs = execOutput
				.getMaxExecutions();
		if (bestExecs.size() == 1) {
			// Case we have a single execution
			maxScoringExecutions = bestExecs;
			processSingleBestParse(dataItem, execOutput, bestExecs.get(0),
					false, stats);
		} else if (bestExecs.size() > 1) {
			// Multiple top executions
			maxScoringExecutions = bestExecs;

			// Update statistics
			stats.recordParses(dataItem,
					ListUtils.map(bestExecs, obj -> obj.getResult()));

			// There are more than one equally high scoring
			// logical forms. If this is the case, we abstain
			// from returning a result.
			LOG.info("too many results");
			LOG.info("%d results:", bestExecs.size());
			for (final IExecution<LogicalExpression> execution : bestExecs) {
				LOG.info(execution.toString(true));
			}
			// Check if we had the correct parse and it just wasn't the best
			final List<IExecution<LogicalExpression>> correctExecs = execOutput
					.getExecutions(dataItem.getLabel());
			LOG.info("Had correct result: %s", !correctExecs.isEmpty());
			for (final IExecution<LogicalExpression> correctExec : correctExecs) {
				LOG.info(correctExec.toString(true));
			}
		} else {
			// No parses
			LOG.info("no results");

			// Update stats
			stats.recordNoParse(dataItem);

			// Potentially re-execute -- sloppy execution
			LOG.info("no parses");
			if (skipExecutionFilter.test(dataItem.getSample())) {
				final IExecOutput<LogicalExpression> sloppyExecOutput = exec
						.execute(dataItem.getSample(), true);
				LOG.info("SLOPPY execution time %f",
						sloppyExecOutput.getExecTime() / 1000.0);
				final List<IExecution<LogicalExpression>> bestSloppyExecutions = sloppyExecOutput
						.getMaxExecutions();

				if (bestSloppyExecutions.size() == 1) {
					maxScoringExecutions = bestSloppyExecutions;
					processSingleBestParse(dataItem, sloppyExecOutput,
							bestSloppyExecutions.get(0), true, stats);
				} else if (bestSloppyExecutions.isEmpty()) {
					// No results
					LOG.info("no results");
					maxScoringExecutions = Collections.emptyList();
					stats.recordNoParseWithSkipping(dataItem);
				} else {
					// too many results
					stats.recordParsesWithSkipping(dataItem, ListUtils
							.map(bestSloppyExecutions, obj -> obj.getResult()));
					maxScoringExecutions = bestSloppyExecutions;
					LOG.info("WRONG: %d results", bestSloppyExecutions.size());
					for (final IExecution<LogicalExpression> execution : bestSloppyExecutions) {
						LOG.info(execution.toString(true));
					}
					// Check if we had the correct execution and it just wasn't
					// the best
					final List<IExecution<LogicalExpression>> correctExecs = sloppyExecOutput
							.getExecutions(dataItem.getLabel());
					LOG.info("Had correct result: %s", !correctExecs.isEmpty());
					for (final IExecution<LogicalExpression> correctExec : correctExecs) {
						LOG.info(correctExec.toString(true));
					}
				}
			} else {
				maxScoringExecutions = Collections.emptyList();
				LOG.info("Skipping sloppy execution due to filter");
				stats.recordNoParseWithSkipping(dataItem);
			}

			// Extra logging.

			if (exec instanceof Exec
					&& ((Exec) exec).supportsConditionedInference()) {

				// If there's a single max-scoring derivation and it's the
				// correct one, skip this step.
				if (maxScoringExecutions.size() == 1 && maxScoringExecutions
						.get(0).getResult().equals(dataItem.getLabel())) {
					LOG.info("CORRECT -- skipping conditioned inference");
				} else {
					final IExecOutput<LogicalExpression> output = ((Exec) exec)
							.conditionedExec(dataItem);
					final List<IExecution<LogicalExpression>> correctExecutions = output
							.getExecutions(dataItem.getLabel());
					if (correctExecutions.isEmpty()) {
						LOG.info("No correct executions");
					} else {

						// Get a set of correct LFs, to check if the correct LF
						// is max-scoring (but not the only one).
						correctExecutions.stream().map(e -> e.getResult())
								.collect(Collectors.toSet());

						// Log correct executions.
						LOG.info("Generated %d correct executions:",
								correctExecutions.size());
						double maxScore = -Double.MAX_VALUE;
						for (final IExecution<LogicalExpression> execution : correctExecutions) {
							LOG.info(execution.toString(true));
							if (execution.score() > maxScore) {
								maxScore = execution.score();
							}
						}

						// Compare scores to identify search errors.
						if (!maxScoringExecutions.isEmpty()
								&& maxScoringExecutions.get(0)
										.score() < maxScore) {
							LOG.info(
									"Search error: the correct max-scoring derivation has a higher score that the max output");
						}

						// Compare the features of the first correct derivation
						// against all max-scoring derivations.
						LOG.info("Comparing to max-scoring derivations:");
						final IExecution<LogicalExpression> correct = correctExecutions
								.get(0);
						final IHashVectorImmutable correctFeatures = correct
								.getFeatures();
						for (final IExecution<LogicalExpression> execution : maxScoringExecutions) {
							LOG.info(execution.toString(true));

							final IHashVector delta = correctFeatures
									.addTimes(-1.0, execution.getFeatures());
							delta.dropNoise();
							delta.dropZeros();
							LOG.info("Delta with correct: %s", ((Exec) exec)
									.getModel().getTheta().printValues(delta));
							LOG.info(
									"----------------------------------------");
						}
					}
				}
			} else {
				LOG.info(
						"Skipping conditioned inference -- not supported by executor");
			}

		}
	}

	public static class Creator
			implements IResourceObjectCreator<AmrExecTester> {

		private final String type;

		public Creator() {
			this("tester.exec.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AmrExecTester create(Parameters params,
				IResourceRepository repo) {
			IFilter<SituatedSentence<AMRMeta>> filter;
			if (params.contains("sloppyFilter")) {
				filter = repo.get(params.get("sloppyFilter"));
			} else {
				filter = FilterUtils.stubTrue();
			}

			return new AmrExecTester(filter);
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(), AmrExecTester.class)
					.addParam("sloppyFilter", "id",
							"IFilter used to decide what data items to skip when doing sloppy inference (e.g., skipping words)")
					.build();
		}

	}

}
