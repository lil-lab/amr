/*******************************************************************************
 * UW SPF - The University of Washington Semantic Parsing Framework
 * <p>
 * Copyright (C) 2013 Yoav Artzi
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 ******************************************************************************/
package edu.uw.cs.lil.amr.exp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.situated.ISituatedDataItem;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExec;
import edu.cornell.cs.nlp.spf.explat.DistributedExperiment;
import edu.cornell.cs.nlp.spf.explat.Job;
import edu.cornell.cs.nlp.spf.explat.resources.ResourceCreatorRepository;
import edu.cornell.cs.nlp.spf.learn.ILearner;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.ccg.LogicalExpressionCategoryServices;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelImmutable;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelInit;
import edu.cornell.cs.nlp.spf.parser.ccg.model.Model;
import edu.cornell.cs.nlp.spf.parser.ccg.model.ModelLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelInit;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelProcessor;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.spf.reliabledist.EnslavedLocalManager;
import edu.cornell.cs.nlp.spf.reliabledist.ReliableManager;
import edu.cornell.cs.nlp.spf.test.exec.IExecTester;
import edu.cornell.cs.nlp.spf.test.stats.CompositeTestingStatistics;
import edu.cornell.cs.nlp.spf.test.stats.ITestingStatistics;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.gradient.GradientChecker;

public class AmrExp extends DistributedExperiment {
	public static final ILogger						LOG	= LoggerFactory
			.create(AmrExp.class);

	private final LogicalExpressionCategoryServices	categoryServices;

	public AmrExp(File initFile) throws IOException {
		this(initFile, Collections.<String, String> emptyMap(),
				new AmrResourceRepo());
	}

	public AmrExp(File initFile, Map<String, String> envParams,
			ResourceCreatorRepository creatorRepo) throws IOException {
		super(initFile, envParams, creatorRepo);

		// //////////////////////////////////////////
		// Get parameters
		// //////////////////////////////////////////
		final File typesFile = globalParams.getAsFile("types");
		final File specmapFile = globalParams.getAsFile("specmap");

		// //////////////////////////////////////////////////
		// Init AMR.
		// //////////////////////////////////////////////////

		Init.init(typesFile, specmapFile,
				globalParams.getAsFile("stanfordModel"), false,
				globalParams.getAsFile("nerConfig"),
				globalParams.getAsFile("nerTranslation"),
				globalParams.getAsFile("propBank"),
				globalParams.getAsBoolean("underspecifyPropBank", false));

		// //////////////////////////////////////////////////
		// Category services for logical expressions.
		// //////////////////////////////////////////////////

		this.categoryServices = new LogicalExpressionCategoryServices(true);
		storeResource(CATEGORY_SERVICES_RESOURCE, categoryServices);

		// //////////////////////////////////////////////////
		// Read resources.
		// //////////////////////////////////////////////////

		readResrouces();

		// //////////////////////////////////////////////////
		// Create jobs
		// //////////////////////////////////////////////////

		for (final Parameters params : jobParams) {
			addJob(createJob(params));
		}
	}

	public AmrExp(File initFile, String[] args) throws IOException {
		this(initFile, argsToMap(args), new AmrResourceRepo());
	}

	private static Map<String, String> argsToMap(String[] args) {
		final Map<String, String> map = new HashMap<>();
		for (final String arg : args) {
			final String[] split = arg.split("=", 2);
			if (split.length == 2) {
				map.put(split[0], split[1]);
			} else {
				throw new IllegalArgumentException("Invalid argument: " + arg);
			}
		}
		return map;
	}

	private Job createGradientCheckJob(Parameters params)
			throws FileNotFoundException {
		// The model to use.
		final JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model = get(
				params.get("model"));

		// The gradient checker.
		final GradientChecker checker = get(params.get("checker"));

		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				final long startTime = System.currentTimeMillis();

				// Start job
				LOG.info("============ (Job %s started)", getId());

				// Do the gradient checking.
				checker.check(model);

				// Output total run time
				LOG.info("Total run time %.4f seconds",
						(System.currentTimeMillis() - startTime) / 1000.0);

				// Job completed
				LOG.info("============ (Job %s completed)", getId());
			}
		};
	}

	private Job createJob(Parameters params) throws FileNotFoundException {
		final String type = params.get("type");
		if (type.equals("train")) {
			return createTrainJob(params);
		} else if (type.equals("test")) {
			return createTestJob(params);
		} else if (type.equals("save")) {
			return createSaveJob(params);
		} else if ("process".equals(type)) {
			return createProcessingJob(params);
		} else if ("listen".equals(type)) {
			return createListenerRegistrationJob(params);
		} else if (type.equals("log")) {
			return createModelLoggingJob(params);
		} else if ("init".equals(type)) {
			return createModelInitJob(params);
		} else if ("tinydist.master".equals(type)) {
			return createReliableManagerJob(params);
		} else if ("tinydist.worker".equals(type)) {
			return createWorkerJob(params);
		} else if ("parse".equals(type)) {
			return createParseJob(params);
		} else if ("gradcheck".equals(type)) {
			return createGradientCheckJob(params);
		} else {
			throw new RuntimeException("Unsupported job type: " + type);
		}
	}

	private Job createListenerRegistrationJob(Parameters params)
			throws FileNotFoundException {
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				LOG.info("Registering listeners to model, id=%s",
						params.get("model"));
				final Model<?, ?> model = get(params.get("model"));
				for (final String listenerId : params.getSplit("listeners")) {
					model.registerListener(get(listenerId));
				}

			}
		};
	}

	@SuppressWarnings("unchecked")
	private Job createModelInitJob(Parameters params)
			throws FileNotFoundException {

		final JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model = get(
				params.get("model"));
		final List<Runnable> runnables = new LinkedList<>();
		for (final String id : params.getSplit("init")) {
			final Object init = get(id);
			if (init instanceof IModelInit) {
				runnables
						.add(() -> ((IModelInit<SituatedSentence<AMRMeta>, LogicalExpression>) init)
								.init(model));
			} else if (init instanceof IJointModelInit) {
				runnables
						.add(() -> ((IJointModelInit<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>) init)
								.init(model));
			} else {
				throw new RuntimeException("invalid init type");
			}
		}

		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				for (final Runnable runnable : runnables) {
					runnable.run();
				}
			}
		};
	}

	private Job createModelLoggingJob(Parameters params)
			throws FileNotFoundException {
		final IModelImmutable<?, ?> model = get(params.get("model"));
		final ModelLogger modelLogger = get(params.get("logger"));
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				modelLogger.log(model, getOutputStream());
			}
		};
	}

	private Job createParseJob(Parameters params) throws FileNotFoundException {
		return new ParseJob(params.get("id"),
				new HashSet<>(params.getSplit("dep")), this,
				createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id")),
				get(params.get("sentences")), get(params.get("exec")),
				params.getAsBoolean("allowSloppy", true));
	}

	private <DI extends ISituatedDataItem<?, ?>, MR, ESTEP> Job createProcessingJob(
			Parameters params) throws FileNotFoundException {
		final JointModel<DI, MR, ESTEP> model = get(params.get("model"));
		final IJointModelProcessor<DI, MR, ESTEP> processor = get(
				params.get("processor"));
		assert model != null;
		assert processor != null;
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				// Process the model.
				LOG.info("Processing model...");
				final long startTime = System.currentTimeMillis();
				processor.process(model);
				LOG.info("Processing completed (%.3fsec).",
						(System.currentTimeMillis() - startTime) / 1000.0);
			}
		};
	}

	private Job createReliableManagerJob(Parameters params)
			throws FileNotFoundException {
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				LOG.info("Starting tinydist reliable manager");
				final ReliableManager manager = get(params.get("manager"));
				manager.start();
			}
		};
	}

	private Job createSaveJob(final Parameters params)
			throws FileNotFoundException {
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@SuppressWarnings("unchecked")
			@Override
			protected void doJob() {
				// Save the model to file.
				try {
					LOG.info("Saving model (id=%s) to: %s", params.get("model"),
							params.getAsFile("file").getAbsolutePath());
					Model.write(
							(Model<Sentence, LogicalExpression>) get(
									params.get("model")),
							params.getAsFile("file"));
					LOG.info("Model saved");
				} catch (final IOException e) {
					LOG.error("Failed to save model to: %s",
							params.get("file"));
					throw new RuntimeException(e);
				}

			}
		};
	}

	private Job createTestJob(Parameters params) throws FileNotFoundException {
		// Create test statistics.
		final List<ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence>> testingMetrics = new LinkedList<>();
		for (final String statsId : params.getSplit("stats")) {
			testingMetrics.add(get(statsId));
		}
		final ITestingStatistics<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> testStatistics = new CompositeTestingStatistics<>(
				testingMetrics);

		// Get the executor.
		final IExec<SituatedSentence<AMRMeta>, LogicalExpression> exec = get(
				params.get("exec"));

		// Get the tester.
		final IExecTester<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence> tester = get(
				params.get("tester"));

		// Get the data.
		final IDataCollection<LabeledAmrSentence> data = get(
				params.get("data"));

		// Create and return the job.
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {

				// Record start time.
				final long startTime = System.currentTimeMillis();

				// Job started.
				LOG.info("============ (Job %s started)", getId());

				// Test the final model.
				tester.test(exec, data, testStatistics);
				LOG.info("%s\n", testStatistics);
				getOutputStream()
						.println(testStatistics.toTabDelimitedString());

				// Output total run time..
				LOG.info("Total run time %.4f seconds",
						(System.currentTimeMillis() - startTime) / 1000.0);

				// Job completed
				LOG.info("============ (Job %s completed)", getId());
			}
		};
	}

	@SuppressWarnings("unchecked")
	private Job createTrainJob(Parameters params) throws FileNotFoundException {
		// The model to use
		final Model<Sentence, LogicalExpression> model = (Model<Sentence, LogicalExpression>) get(
				params.get("model"));

		// The learning
		final ILearner<Sentence, SingleSentence, Model<Sentence, LogicalExpression>> learner = (ILearner<Sentence, SingleSentence, Model<Sentence, LogicalExpression>>) get(
				params.get("learner"));

		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				final long startTime = System.currentTimeMillis();

				// Start job
				LOG.info("============ (Job %s started)", getId());

				// Do the learning
				learner.train(model);

				// Output total run time
				LOG.info("Total run time %.4f seconds",
						(System.currentTimeMillis() - startTime) / 1000.0);

				// Job completed
				LOG.info("============ (Job %s completed)", getId());

			}
		};
	}

	private Job createWorkerJob(Parameters params)
			throws FileNotFoundException {
		return new Job(params.get("id"), new HashSet<>(params.getSplit("dep")),
				this, createJobOutputFile(params.get("id")),
				createJobLogFile(params.get("id"))) {

			@Override
			protected void doJob() {
				LOG.info("Starting tinydist worker");
				final EnslavedLocalManager worker = get(params.get("worker"));
				worker.run();
			}
		};
	}

}
