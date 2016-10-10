package edu.uw.cs.lil.amr.learn.batch.distributed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.JointInferenceFilterUtils;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.spf.reliabledist.JobFuture;
import edu.cornell.cs.nlp.spf.reliabledist.ReliableManager;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.batch.voting.StubVoting;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.GradientComputation;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.IsValidAmr;

/**
 * Distributed learner. Each epoch includes two main passes: lexical generation
 * and parameter estimation. Lexical generation is done with a batch pass that
 * is fully distributed. Parameter estimation is done with mini batches. Each
 * mini batch is distributed.
 *
 * @author Yoav Artzi
 */
public class DistributeMiniBatchLearner
		extends AbstractDistributedBatchLearner {

	private final int batchSize;

	public DistributeMiniBatchLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean pruneLexicon,
			BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> votingProcedure,
			ReliableManager manager, int batchSize,
			IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam,
			ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> alignmentGenlex,
			boolean resumedLearning,
			ILexiconImmutable<LogicalExpression> entriesNotToPrune) {
		super(numIterations, trainingData, sortData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, pruneLexicon, votingProcedure, manager,
				estimator, gradientFunction, conditionedInferenceBeam,
				alignmentGenlex, resumedLearning, entriesNotToPrune);
		this.batchSize = batchSize;
		LOG.info("Init %s: batchSize=%d", getClass().getSimpleName(),
				batchSize);
	}

	@Override
	protected Set<LexicalEntry<LogicalExpression>> doParameterEstimation(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int epochNumber) {
		final long startTime = System.currentTimeMillis();

		if (!remoteModelIsUpToDate) {
			// Update the environment.
			// Update the entire model. This is the only time we do it. Further
			// updates will be incremental. We never update the structure of the
			// model, just the parameters. This is why we can do incremental
			// updates easily. And it will save transfer costs.
			if (!manager
					.updateEnviroment(LearningEnvironment.setModel(model))) {
				LOG.error("Failed to update environment");
				throw new IllegalStateException();
			}
			remoteModelIsUpToDate = true;
		}

		final Iterator<LabeledAmrSentence> dataIterator = trainingData
				.iterator();
		int itemCounter = -1;
		long computeTime = 0;
		int miniBatchCounter = -1;
		final Set<LexicalEntry<LogicalExpression>> viterbiEntries = new HashSet<>();
		while (dataIterator.hasNext()) {

			LOG.info("======================= [%d.%d / PARAM]", epochNumber,
					++miniBatchCounter);

			// Create the mini batch and submit the jobs.
			int batchBaseNumber = itemCounter;
			final List<LabeledAmrSentence> batch = new ArrayList<>(batchSize);
			final List<JobFuture<GradientResult>> futures = new ArrayList<>(
					batchSize);
			while (batch.size() < batchSize && dataIterator.hasNext()) {
				final LabeledAmrSentence dataItem = dataIterator.next();
				batch.add(dataItem);
				futures.add(manager.execute(new GradientJob(dataItem,
						epochNumber, ++batchBaseNumber)));
			}

			// Wait for all jobs to complete.
			boolean working = true;
			final long distStartTime = System.currentTimeMillis();
			while (working) {
				working = false;
				int completed = 0;
				JobFuture<GradientResult> remainingFuture = null;
				for (final JobFuture<GradientResult> future : futures) {
					if (!future.isDone()) {
						remainingFuture = future;
						working = true;
					} else {
						++completed;
					}
				}
				LOG.info("Completed %d/%d (%.3fsec)", completed, futures.size(),
						(System.currentTimeMillis() - distStartTime) / 1000.0);
				if (remainingFuture != null) {
					try {
						remainingFuture.get(10, TimeUnit.SECONDS);
					} catch (InterruptedException | ExecutionException
							| TimeoutException e) {
						// Ignore.
					}
				}
			}
			LOG.info("TinyDist complete (%f.3sec)",
					(System.currentTimeMillis() - distStartTime) / 1000.0);

			final Iterator<LabeledAmrSentence> batchIterator = batch.iterator();
			final Iterator<JobFuture<GradientResult>> futureIterator = futures
					.iterator();
			final IHashVector aggregateGradient = HashVectorFactory.create();
			while (batchIterator.hasNext()) {
				final LabeledAmrSentence dataItem = batchIterator.next();
				final JobFuture<GradientResult> future = futureIterator.next();

				// Log sample header.
				LOG.info("%d : ================== [%d / PARAM]", ++itemCounter,
						epochNumber);
				LOG.info("Sample type: %s",
						dataItem.getClass().getSimpleName());
				LOG.info("%s", dataItem);

				try {
					LOG.info(future.getLog());
				} catch (final InterruptedException e) {
					LOG.error("Failed to get log due to an exception: %s", e);
				}

				final GradientResult result;
				try {
					result = future.get();
				} catch (InterruptedException | ExecutionException e) {
					LOG.error("Job failed: %s", e);
					continue;
				}

				computeTime += result.processingTime;

				if (result.processed) {
					stats.count("Processed for parameter estimation",
							epochNumber);

					// Apply updates to the learning statistics.
					result.computedGradient.getStatUpdates().accept(stats);

					// Aggregate the viterbi entries.
					viterbiEntries.addAll(
							result.computedGradient.getViterbiEntries());

					// Mark the sample as reachable.
					if (!result.computedGradient.getViterbiEntries()
							.isEmpty()) {
						reachableDuringLastEpoch.add(itemCounter);
					}

					if (result.computedGradient.getGradient() != null) {
						// Add the gradient into the aggregate vector.
						result.computedGradient.getGradient().addTimesInto(1.0,
								aggregateGradient);
					}

					LOG.info("Data item processing time: %.2fsec",
							result.processingTime / 1000.0);
				}
			}

			// Apply the mini batch update.
			if (updateProcedure.applyUpdate(aggregateGradient,
					model.getTheta())) {
				// Apply the update.
				remoteModelIsUpToDate = false;
				stats.count("Mini batch update", epochNumber);

				// Refresh the parameters of the remote model. Clone the
				// parameters to get a snapshot of the current parameters.
				if (!manager.updateEnviroment(
						LearningEnvironment.setModelParameters(
								HashVectorFactory.create(model.getTheta())))) {
					LOG.error("Failed to update environment");
					throw new IllegalStateException();
				}
				remoteModelIsUpToDate = true;
			}
		}

		// Log speedup.
		final long realTotalTime = System.currentTimeMillis() - startTime;
		LOG.info("Distribution speedup:");
		LOG.info("Real time: %.3f, compute time: %.3f, speedup: %.3f",
				realTotalTime / 1000.0, computeTime / 1000.0,
				computeTime / (double) realTotalTime);

		return viterbiEntries;

	}

	public static class Creator
			implements IResourceObjectCreator<DistributeMiniBatchLearner> {

		private final String type;

		public Creator() {
			this("learner.amr.minibatch.dist");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public DistributeMiniBatchLearner create(Parameters params,
				IResourceRepository repo) {

			final IDataCollection<LabeledAmrSentence> trainingData = repo
					.get(params.get("data"));
			final int numIterations = params.getAsInteger("iter");
			final int maxSentenceLength = params
					.getAsInteger("maxSentenceLength", Integer.MAX_VALUE);
			final boolean sortData = params.getAsBoolean("sortData", false);

			final ICategoryServices<LogicalExpression> categoryServices;
			final ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex;
			if (params.contains("genlex")) {
				genlex = repo.get(params.get("genlex"));
				categoryServices = repo.get(
						ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE);
			} else {
				genlex = null;
				categoryServices = null;
			}

			final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger;
			if (params.contains("parseLogger")) {
				parserOutputLogger = repo.get(params.get("parseLogger"));
			} else {
				parserOutputLogger = null;
			}

			final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory;
			if (params.contains("filterFactory")) {
				filterFactory = repo.get(params.get("filterFactory"));
			} else {
				filterFactory = new IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>() {
					private static final long serialVersionUID = -8410588783722286647L;

					@Override
					public Predicate<ParsingOp<LogicalExpression>> create(
							LabeledAmrSentence object) {
						return JointInferenceFilterUtils.stubTrue();
					}

					@Override
					public IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> createJointFilter(
							LabeledAmrSentence ibj) {
						return JointInferenceFilterUtils.stubTrue();
					}
				};

			}

			IntConsumer postIteration = (i) -> {
				return;
			};
			for (final String id : params.getSplit("postIteration")) {
				postIteration = postIteration.andThen(repo.get(id));
			}

			final BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> votingProcedure;
			if (params.contains("voter")) {
				votingProcedure = repo.get(params.get("voter"));
			} else {
				votingProcedure = new StubVoting();
			}

			Integer conditionedInferenceBeam;
			if (params.contains("conditionedBeam")) {
				conditionedInferenceBeam = params
						.getAsInteger("conditionedBeam");
			} else {
				conditionedInferenceBeam = null;
			}

			final ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> alignmentGenlex;
			if (params.contains("alignGenlex")) {
				alignmentGenlex = repo.get(params.get("alignGenlex"));
			} else {
				alignmentGenlex = null;
			}

			final ILexiconImmutable<LogicalExpression> keepEntries;
			if (params.contains("keepEntries")) {
				keepEntries = repo.get(params.get("keepEntries"));
			} else {
				keepEntries = new Lexicon<>();
			}

			return new DistributeMiniBatchLearner(numIterations, trainingData,
					sortData, maxSentenceLength,
					repo.get(ParameterizedExperiment.PARSER_RESOURCE),
					parserOutputLogger, categoryServices, genlex, filterFactory,
					postIteration, params.getAsBoolean("prune", false),
					votingProcedure, repo.get(params.get("manager")),
					params.getAsInteger("batch"),
					repo.get(params.get("estimator")),
					repo.get(params.get("gradient")), conditionedInferenceBeam,
					alignmentGenlex, params.getAsBoolean("resume", false),
					keepEntries);
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, DistributeMiniBatchLearner.class)
					.addParam("keepEntries", ILexiconImmutable.class,
							"Entries to keep during lexicon prunning despite if they are used or not (default: none)")
					.addParam("resume", Boolean.class,
							"Resume learning and skip initialization actions (default: false)")
					.addParam("alignGenlex", ILexiconGenerator.class,
							"High-precision heuristic alignment GENLEX to augment the model (default: null)")
					.addParam("prune", Boolean.class,
							"Prune the lexicon after each parameter update (default: false)")
					.addParam("voter", BiFunction.class,
							"Voting procedure (default: stub voter)")
					.setDescription(
							"AMR-specific gradient descent batch learner.")
					.addParam("batch", Integer.class, "Batch size")
					.addParam("data", IDataCollection.class, "Training data")
					.addParam("iter", "int", "Number of training iterations")
					.addParam("maxSentenceLength", "int",
							"Max sentence length to process")
					.addParam("gradient", IGradientFunction.class,
							"Functin to compute the gradient")
					.addParam("estimator", IWeightUpdateProcedure.class,
							"Parameter estimation update rule")
					.addParam("sortData", Boolean.class,
							"Sort the data according to sentence length in ascending order (default: false)")
					.addParam("genlex", ILexiconGeneratorPrecise.class,
							"GENLEX procedure")
					.addParam("parseLogger", "id",
							"Parse logger for debug detailed logging of parses")
					.addParam("manager", ReliableManager.class,
							"Reliable tinydist manager")
					.addParam("filterFactory",
							IJointInferenceFilterFactory.class,
							"Filter for conditioned inference (default: true stub)")
					.addParam("postIteration", Runnable.class,
							"Task to run after each iteration")
					.build();
		}

	}

	private static class GradientJob implements
			Function<LearningEnvironment, GradientResult>, Serializable {

		private static final long			serialVersionUID	= -531673379742439999L;
		private final LabeledAmrSentence	dataItem;
		private final int					epochNumber;
		private final int					itemNumber;

		public GradientJob(LabeledAmrSentence dataItem, int epochNumber,
				int itemNumber) {
			this.dataItem = dataItem;
			this.epochNumber = epochNumber;
			this.itemNumber = itemNumber;
		}

		@Override
		public GradientResult apply(LearningEnvironment env) {

			// Record sample processing start time.
			final long startTime = System.currentTimeMillis();

			final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = env
					.getModel().createJointDataItemModel(dataItem.getSample());

			// Skip sample, if over the length limit.
			if (dataItem.getSample().getSample().getTokens().size() > env
					.getMaxSentenceLength()) {
				LOG.warn("Training sample too long, skipping");
				return new GradientResult(false, null,
						System.currentTimeMillis() - startTime);
			}

			// Skip sample if the label is invalid.
			if (!IsValidAmr.of(dataItem.getLabel(), true, true)) {
				LOG.warn("Annotated LF is an invalid AMR -- skipping");
				return new GradientResult(false, null,
						System.currentTimeMillis() - startTime);
			}

			final GradientComputation gradientComputation = env
					.getGradientFunction().of(dataItem, dataItemModel,
							itemNumber, epochNumber, env.getParser(),
							env.getFilterFactory(),
							env.getParserOutputLogger());

			// Validate the gradient.
			if (gradientComputation.getGradient() != null) {
				if (!env.getModel().isValidWeightVector(
						gradientComputation.getGradient())) {
					throw new IllegalStateException("Invalid gradient");
				}
			} else {
				LOG.info("No gradient");
			}

			return new GradientResult(true, gradientComputation,
					System.currentTimeMillis() - startTime);
		}

	}

	private static class GradientResult implements Serializable {

		private static final long			serialVersionUID	= 4225274940539731344L;
		private final GradientComputation	computedGradient;
		private final boolean				processed;
		private final long					processingTime;

		public GradientResult(boolean processed,
				GradientComputation computedGradient, long processingTime) {
			this.processed = processed;
			this.computedGradient = computedGradient;
			this.processingTime = processingTime;
		}

	}

}
