package edu.uw.cs.lil.amr.learn.tasks;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntConsumer;

import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExec;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.test.exec.ExecTester;
import edu.cornell.cs.nlp.spf.test.exec.IExecTester;
import edu.cornell.cs.nlp.spf.test.stats.CompositeTestingStatistics;
import edu.cornell.cs.nlp.spf.test.stats.ExactMatchTestingStatistics;
import edu.cornell.cs.nlp.spf.test.stats.ITestingStatistics;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.online.AbstractOnlineLearner;
import edu.uw.cs.lil.amr.test.SmatchStats;

/**
 * A runnable to do testing after each learning iteration.
 *
 * @author Yoav Artzi
 * @see AbstractOnlineLearner
 */
public class TestLearningTask implements IntConsumer {
	public static final ILogger																	LOG	= LoggerFactory
			.create(TestLearningTask.class);
	private final IDataCollection<LabeledAmrSentence>											data;
	private final IExec<SituatedSentence<AMRMeta>, LogicalExpression>							exec;
	private final int																			smatchMaxRetries;
	private final String																		smatchPath;
	private final int																			smatchRounds;
	private final long																			smatchTimeout;
	private final IExecTester<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence>	tester;

	public TestLearningTask(IDataCollection<LabeledAmrSentence> data,
			IExecTester<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> tester,
			IExec<SituatedSentence<AMRMeta>, LogicalExpression> exec,
			File smatchPath, long smatchTimeout, int smatchMaxRetries,
			int smatchRounds) {
		this.data = data;
		this.tester = tester;
		this.exec = exec;
		this.smatchTimeout = smatchTimeout;
		this.smatchMaxRetries = smatchMaxRetries;
		this.smatchRounds = smatchRounds;
		this.smatchPath = smatchPath.getAbsolutePath();
		LOG.info("Init %s: size(data)=%d", getClass().getSimpleName(),
				data.size());
		LOG.info(
				"Init %s: timeout=%d, maxRetries=%d, smatchRounds=%d, smatchPath=%s",
				getClass().getSimpleName(), smatchTimeout, smatchMaxRetries,
				smatchRounds, smatchPath);
	}

	@Override
	public void accept(int epochNumber) {
		LOG.info("=========================");
		LOG.info("Training epoch %d: Testing Run", epochNumber);
		LOG.info("=========================");

		// Create test statistics.
		final List<ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence>> testingMetrics = new LinkedList<>();
		testingMetrics.add(new ExactMatchTestingStatistics<>("exact"));
		final SmatchStats smatch = new SmatchStats(smatchPath, smatchTimeout,
				smatchMaxRetries, smatchRounds, false);
		testingMetrics.add(smatch);
		final ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> testStatistics = new CompositeTestingStatistics<>(
				testingMetrics);

		// Record start time.
		final long startTime = System.currentTimeMillis();

		// Test the final model.
		tester.test(exec, data, testStatistics);
		LOG.info("%s\n", testStatistics);

		// Output machine readable statistics, so we have it one line (easier to
		// grep).
		LOG.info(testStatistics.toTabDelimitedString());

		// Output total run time..
		LOG.info("Total run time %.4f seconds",
				(System.currentTimeMillis() - startTime) / 1000.0);

		// Job completed
		LOG.info("============ (Testing run completed)");
	}

	public static class Creator
			implements IResourceObjectCreator<TestLearningTask> {

		private final String type;

		public Creator() {
			this("learn.task.test");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public TestLearningTask create(Parameters params,
				IResourceRepository repo) {
			return new TestLearningTask(repo.get(params.get("data")),
					repo.get(params.get("tester")),
					repo.get(params.get("exec")), params.getAsFile("smatch"),
					params.getAsLong("smatchTimeout", 5000),
					params.getAsInteger("smatchRetries", 2),
					params.getAsInteger("smatchRounds", 4));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, TestLearningTask.class)
					.addParam("smatchRounds", Integer.class,
							"Nubmer of random restarts when computing smatch (default: 4)")
					.setDescription(
							"Runnable to do testing after every learning iteration")
					.addParam("smatchRetries", Integer.class,
							"Max smatch retries (default: 2)")
					.addParam("smatch", File.class, "SMATCH script path")
					.addParam("smatchTimeout", Long.class,
							"Timeout for SMATCH process in milliseconds (default: 5000)")
					.addParam("data", IDataCollection.class,
							"Labeled testing data")
					.addParam("tester", ExecTester.class, "Tester object")
					.addParam("exec", IExec.class, "Execution wrapper").build();
		}

	}

}
