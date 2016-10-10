package edu.uw.cs.lil.amr.learn.batch;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.batch.voting.StubVoting;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.GradientComputation;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.IsValidAmr;

/**
 * Simple batch learner. Simply sums the gradients.
 *
 * @author Yoav Artzi
 */
public class BatchLearner extends AbstractVanillaBatchLearner {

	private final Double	regularizationCoef;

	private final double	stepSize;

	public BatchLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, double stepSize,
			Double regularizationCoef, boolean pruneLexicon,
			BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> votingProcedure,
			IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam,
			ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> alignmentGenlex,
			boolean resumedLearning,
			ILexiconImmutable<LogicalExpression> entriesNotToPrune) {
		super(numIterations, trainingData, sortData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, pruneLexicon, votingProcedure, estimator,
				gradientFunction, conditionedInferenceBeam, alignmentGenlex,
				resumedLearning, entriesNotToPrune);
		this.stepSize = stepSize;
		this.regularizationCoef = regularizationCoef;
	}

	@Override
	protected Set<LexicalEntry<LogicalExpression>> doParameterEstimation(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int epochNumber) {

		// Iterate over the data to collect the gradients.
		final IHashVector update = HashVectorFactory.create();
		int itemCounter = -1;
		final Set<LexicalEntry<LogicalExpression>> viterbiEntries = new HashSet<>();
		for (final LabeledAmrSentence dataItem : trainingData) {
			// Record sample processing start time.
			final long startTime = System.currentTimeMillis();

			// Log sample header.
			LOG.info("%d : ================== [%d / PARAM]", ++itemCounter,
					epochNumber);
			LOG.info("Sample type: %s", dataItem.getClass().getSimpleName());
			LOG.info("%s", dataItem);

			// Skip sample, if over the length limit.
			if (dataItem.getSample().getSample().getTokens()
					.size() > maxSentenceLength) {
				LOG.warn("Training sample too long, skipping");
				continue;
			}

			// Skip sample of the label is invalid.
			if (!IsValidAmr.of(dataItem.getLabel(), true, true)) {
				LOG.warn("Annotated LF is an invalid AMR -- skipping");
				continue;
			}

			stats.count("Processed for parameter estimation", epochNumber);

			// Compute the gradient.
			final GradientComputation gradientComputation = gradientFunction
					.of(dataItem,
							model.createJointDataItemModel(
									dataItem.getSample()),
							itemCounter, epochNumber, parser, filterFactory,
							parserOutputLogger);

			// Apply updates to the learning statistics.
			gradientComputation.getStatUpdates().accept(stats);

			// Aggregate the viterbi entries.
			viterbiEntries.addAll(gradientComputation.getViterbiEntries());

			// Mark the sample as reachable.
			if (!gradientComputation.getViterbiEntries().isEmpty()) {
				reachableDuringLastEpoch.add(itemCounter);
			}

			if (gradientComputation.getGradient() == null) {
				LOG.info("No gradient for data item");
				continue;
			}

			if (!model.isValidWeightVector(gradientComputation.getGradient())) {
				throw new IllegalStateException("Invalid gradient: "
						+ gradientComputation.getGradient());
			}

			// Add the gradient into the update.
			gradientComputation.getGradient().addTimesInto(1.0, update);

			LOG.info("Data item processing time: %.2fsec",
					(System.currentTimeMillis() - startTime) / 1000.0);
		}

		// Scale the update.
		update.multiplyBy(stepSize);

		// L2 regularization.
		if (regularizationCoef != null) {
			LOG.info("Update (pre regularization): %s", update);
			model.getTheta().addTimesInto(-regularizationCoef, update);
		}

		LOG.info("Update: %s", update);

		// Drop noise.
		update.dropNoise();

		// Apply the update.
		update.addTimesInto(1.0, model.getTheta());

		return viterbiEntries;
	}

	public static class Creator
			implements IResourceObjectCreator<BatchLearner> {

		private final String type;

		public Creator() {
			this("learner.amr.batch");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public BatchLearner create(Parameters params,
				IResourceRepository repo) {

			final IDataCollection<LabeledAmrSentence> trainingData = repo
					.get(params.get("data"));
			final int numIterations = params.getAsInteger("iter");
			final int maxSentenceLength = params
					.getAsInteger("maxSentenceLength", Integer.MAX_VALUE);
			final boolean sortData = params.getAsBoolean("sortData", false);
			final double stepSize = params.getAsDouble("stepSize");
			final Double regularizationCoef = params.contains("reg")
					? params.getAsDouble("reg") : null;

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
					private static final long serialVersionUID = -1901195512154368820L;

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

			return new BatchLearner(numIterations, trainingData, sortData,
					maxSentenceLength,
					(GraphAmrParser) repo
							.get(ParameterizedExperiment.PARSER_RESOURCE),
					parserOutputLogger, categoryServices, genlex, filterFactory,
					postIteration, stepSize, regularizationCoef,
					params.getAsBoolean("prune", false), votingProcedure,
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
			return ResourceUsage.builder(type, BatchLearner.class)
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
					.addParam("estimator", IWeightUpdateProcedure.class,
							"Parameter estimation update rule")
					.setDescription(
							"AMR-specific gradient descent batch learner.")
					.addParam("reg", Double.class,
							"L2 regularization coefficient or no value to disable (default: disabled).")
					.addParam("data", IDataCollection.class, "Training data")
					.addParam("iter", "int", "Number of training iterations")
					.addParam("maxSentenceLength", "int",
							"Max sentence length to process")
					.addParam("gradient", IGradientFunction.class,
							"Functin to compute the gradient")
					.addParam("sortData", Boolean.class,
							"Sort the data according to sentence length in ascending order (default: false)")
					.addParam("stepSize", Double.class, "Update step size")
					.addParam("genlex", ILexiconGeneratorPrecise.class,
							"GENLEX procedure")
					.addParam("parseLogger", "id",
							"Parse logger for debug detailed logging of parses")
					.addParam("filterFactory",
							IJointInferenceFilterFactory.class,
							"Filter for conditioned inference (default: true stub)")
					.addParam("postIteration", Runnable.class,
							"Task to run after each iteration")
					.build();
		}

	}
}
