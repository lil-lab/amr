package edu.uw.cs.lil.amr.test;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExec;
import edu.cornell.cs.nlp.spf.exec.IExecOutput;
import edu.cornell.cs.nlp.spf.exec.IExecution;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.reliabledist.ReliableManager;
import edu.cornell.cs.nlp.spf.test.exec.distributed.AbstractExecTestEnvironment;
import edu.cornell.cs.nlp.spf.test.exec.distributed.DistributedExecTester;
import edu.cornell.cs.nlp.spf.test.stats.ITestingStatistics;
import edu.cornell.cs.nlp.utils.filter.FilterUtils;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.exec.Exec;

/**
 * An extension of {@link DistributedExecTester} that generates further logging
 * using conditioned inference to get the correct logical form, if available.
 *
 * @author Yoav Artzi
 */
public class AmrDistributedExecTester extends
		DistributedExecTester<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> {

	protected AmrDistributedExecTester(
			IFilter<SituatedSentence<AMRMeta>> skipParsingFilter,
			ReliableManager manager) {
		super(skipParsingFilter, manager);
	}

	@Override
	public void test(IExec<SituatedSentence<AMRMeta>, LogicalExpression> exec,
			IDataCollection<LabeledAmrSentence> data,
			ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> stats) {
		super.test(exec, data, stats);
	}

	@Override
	protected Function<AbstractExecTestEnvironment<SituatedSentence<AMRMeta>, LogicalExpression>, TestJobResult<LogicalExpression>> createTestJob(
			LabeledAmrSentence dataItem) {
		return new AMRTestJob(dataItem);
	}

	public static class Creator
			implements IResourceObjectCreator<AmrDistributedExecTester> {
		private final String resourceName;

		public Creator() {
			this("tester.exec.dist.amr");
		}

		public Creator(String resourceName) {
			this.resourceName = resourceName;
		}

		@Override
		public AmrDistributedExecTester create(Parameters params,
				IResourceRepository repo) {
			final IFilter<SituatedSentence<AMRMeta>> filter;
			if (params.contains("sloppyFilter")) {
				filter = repo.get(params.get("sloppyFilter"));
			} else {
				filter = FilterUtils.stubTrue();
			}
			return new AmrDistributedExecTester(filter,
					(ReliableManager) repo.get(params.get("manager")));
		}

		@Override
		public String type() {
			return resourceName;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(), AmrDistributedExecTester.class)
					.addParam("sloppyFilter", "id",
							"IFilter used to decide what data items to skip when doing sloppy inference (e.g., skipping words)")
					.addParam("manager", ReliableManager.class,
							"TintDist reliable manager")
					.build();
		}

	}

	private static class AMRTestJob extends
			TestJob<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> {

		private static final long serialVersionUID = -5130948200445660217L;

		public AMRTestJob(LabeledAmrSentence dataItem) {
			super(dataItem);
		}

		@Override
		public TestJobResult<LogicalExpression> apply(
				AbstractExecTestEnvironment<SituatedSentence<AMRMeta>, LogicalExpression> env) {
			final TestJobResult<LogicalExpression> jobResult;
			final List<IExecution<LogicalExpression>> maxScoringExecutions;

			// Try a simple model parse. This block of code is copied as is from
			// the parent class as we require access to the full output for
			// complete logging. The jobResult can be generated similarly by
			// simply calling the super method.
			final IExecOutput<LogicalExpression> execOutput = env.getExec()
					.execute(dataItem.getSample());
			LOG.info("Test execution time %.2f",
					execOutput.getExecTime() / 1000.0);

			final List<IExecution<LogicalExpression>> bestExecs = execOutput
					.getMaxExecutions();

			if (!bestExecs.isEmpty()) {
				LOG.info("%d max scoring execution results", bestExecs.size());

				if (bestExecs.size() == 1) {
					LOG.info(bestExecs.get(0).toString(true));
				}

				maxScoringExecutions = bestExecs;
				jobResult = new TestJobResult<LogicalExpression>(
						bestExecs.stream()
								.map(o -> new ResultWrapper<>(o.getResult(),
										o.getFeatures()))
						.collect(Collectors.toList()), false,
						execOutput.getExecTime(), bestExecs.get(0).score());
			} else if (env.getSkipExecutionFilter()
					.test(dataItem.getSample())) {
				final IExecOutput<LogicalExpression> sloppyExecOutput = env
						.getExec().execute(dataItem.getSample(), true);
				LOG.info("SLOPPY execution time %f",
						sloppyExecOutput.getExecTime() / 1000.0);
				final List<IExecution<LogicalExpression>> bestSloppyExecutions = sloppyExecOutput
						.getMaxExecutions();
				LOG.info("%d sloppy max scoring execution results",
						bestSloppyExecutions.size());
				if (bestSloppyExecutions.size() == 1) {
					LOG.info("Single best sloppy execution:");
					LOG.info(bestSloppyExecutions.get(0).toString(true));
				} else if (!bestSloppyExecutions.isEmpty()) {
					LOG.info("Logging first one only");
					LOG.info(bestSloppyExecutions.get(0).toString(true));
				}
				maxScoringExecutions = bestSloppyExecutions;
				jobResult = new TestJobResult<LogicalExpression>(
						bestSloppyExecutions.stream()
								.map(o -> new ResultWrapper<>(o.getResult(),
										o.getFeatures()))
								.collect(Collectors.toList()),
						true,
						execOutput.getExecTime()
								+ sloppyExecOutput.getExecTime(),
						bestSloppyExecutions.isEmpty() ? null
								: bestSloppyExecutions.get(0).score());
			} else {
				LOG.info("Skipping sloppy execution due to filter");
				maxScoringExecutions = Collections.emptyList();
				jobResult = new TestJobResult<LogicalExpression>(
						Collections.emptyList(), false,
						execOutput.getExecTime(), null);
			}

			// Extra logging.

			if (env.getExec() instanceof Exec
					&& ((Exec) env.getExec()).supportsConditionedInference()) {

				// If there's a single max-scoring derivation and it's the
				// correct one, skip this step.
				if (jobResult.getMaxScoringResults().size() == 1
						&& jobResult.getMaxScoringResults().get(0).getResult()
								.equals(dataItem.getLabel())) {
					LOG.info("CORRECT -- skipping conditioned inference");
				} else {
					final IExecOutput<LogicalExpression> output = ((Exec) env
							.getExec()).conditionedExec(dataItem);
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
						if (jobResult.getMaxScoringResults().size() > 0
								&& jobResult.getMaxScore() < maxScore) {
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
							LOG.info("Delta with correct: %s",
									((Exec) env.getExec()).getModel().getTheta()
											.printValues(delta));
							LOG.info(
									"----------------------------------------");
						}
					}
				}
			} else {
				LOG.info(
						"Skipping conditioned inference -- not supported by executor");
			}

			return jobResult;
		}
	}

}
