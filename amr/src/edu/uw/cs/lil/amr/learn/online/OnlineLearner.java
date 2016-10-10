package edu.uw.cs.lil.amr.learn.online;

import java.util.function.IntConsumer;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.IParserOutput;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.JointInferenceFilterUtils;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * Online learner.
 *
 * @author Yoav Artzi
 * @param <SAMPLE>
 *            Inference sample.
 * @param <DI>
 *            Learning data item.
 */
public class OnlineLearner extends AbstractGradLearner {

	public static final ILogger LOG = LoggerFactory.create(OnlineLearner.class);

	protected OnlineLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean sortData,
			IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam, boolean resumeLearning) {
		super(numIterations, trainingData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, sortData, estimator, gradientFunction,
				conditionedInferenceBeam, resumeLearning);
	}

	public static class Builder {

		/**
		 * Required for lexical induction.
		 */
		private ICategoryServices<LogicalExpression>																													categoryServices			= null;

		private Integer																																					conditionedInferenceBeam	= null;

		private final IWeightUpdateProcedure																															estimator;

		private IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>												filterFactory				= new IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>() {

																																																		private static final long serialVersionUID = 6723242782481335332L;

																																																		@Override
																																																		public Predicate<ParsingOp<LogicalExpression>> create(
																																																				LabeledAmrSentence object) {
																																																			return JointInferenceFilterUtils
																																																					.stubTrue();
																																																		}

																																																		@Override
																																																		public IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> createJointFilter(
																																																				LabeledAmrSentence ibj) {
																																																			return JointInferenceFilterUtils
																																																					.stubTrue();
																																																		}
																																																	};

		/**
		 * GENLEX procedure. If 'null' skip lexical induction.
		 */
		private ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>	genlex						= null;

		private final IGradientFunction																																	gradientFunction;

		/**
		 * Max sentence length. Sentence longer than this value will be skipped
		 * during training
		 */
		private int																																						maxSentenceLength			= Integer.MAX_VALUE;

		/** Number of training iterations */
		private int																																						numIterations				= 4;

		private final GraphAmrParser																																	parser;

		private IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>																				parserOutputLogger			= new IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>() {

																																																		private static final long serialVersionUID = 757790589682381947L;

																																																		@Override
																																																		public void log(
																																																				IJointOutput<LogicalExpression, LogicalExpression> output,
																																																				IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
																																																				String tag) {
																																																			// Stub,
																																																			// do
																																																			// nothing.

																																																		}

																																																		@Override
																																																		public void log(
																																																				IParserOutput<LogicalExpression> output,
																																																				IDataItemModel<LogicalExpression> dataItemModel,
																																																				String tag) {
																																																			// Stub,
																																																			// do
																																																			// nothing.
																																																		}
																																																	};

		private IntConsumer																																				postIteration				= (
				i) -> {
																																																		return;
																																																	};

		private boolean																																					resumeLearning				= false;

		private boolean																																					sortData					= false;

		/** Training data */
		private final IDataCollection<LabeledAmrSentence>																												trainingData;

		public Builder(IDataCollection<LabeledAmrSentence> trainingData,
				GraphAmrParser parser, IWeightUpdateProcedure estimator,
				IGradientFunction gradientFunction) {
			this.trainingData = trainingData;
			this.parser = parser;
			this.estimator = estimator;
			this.gradientFunction = gradientFunction;
		}

		public Builder addPostIteration(IntConsumer task) {
			this.postIteration = this.postIteration.andThen(task);
			return this;
		}

		public OnlineLearner build() {
			return new OnlineLearner(numIterations, trainingData,
					maxSentenceLength, parser, parserOutputLogger,
					categoryServices, genlex, filterFactory, postIteration,
					sortData, estimator, gradientFunction,
					conditionedInferenceBeam, resumeLearning);
		}

		public Builder setConditionedInferenceBeam(
				Integer conditionedInferenceBeam) {
			this.conditionedInferenceBeam = conditionedInferenceBeam;
			return this;
		}

		public Builder setFilterFactory(
				IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory) {
			this.filterFactory = filterFactory;
			return this;
		}

		public Builder setGenlex(
				ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
				ICategoryServices<LogicalExpression> categoryServices) {
			this.genlex = genlex;
			this.categoryServices = categoryServices;
			return this;
		}

		public Builder setMaxSentenceLength(int maxSentenceLength) {
			this.maxSentenceLength = maxSentenceLength;
			return this;
		}

		public Builder setNumIterations(int numIterations) {
			this.numIterations = numIterations;
			return this;
		}

		public Builder setParserOutputLogger(
				IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger) {
			this.parserOutputLogger = parserOutputLogger;
			return this;
		}

		public Builder setResumeLearning(boolean resumeLearning) {
			this.resumeLearning = resumeLearning;
			return this;
		}

		public Builder setSortData(boolean sortData) {
			this.sortData = sortData;
			return this;
		}

	}

	public static class Creator
			implements IResourceObjectCreator<OnlineLearner> {

		private final String type;

		public Creator() {
			this("learner.amr.online");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public OnlineLearner create(Parameters params,
				IResourceRepository repo) {

			final IDataCollection<LabeledAmrSentence> trainingData = repo
					.get(params.get("data"));

			final Builder builder = new OnlineLearner.Builder(trainingData,
					repo.get(ParameterizedExperiment.PARSER_RESOURCE),
					repo.get(params.get("estimator")),
					repo.get(params.get("gradient")));

			if (params.contains("parseLogger")) {
				builder.setParserOutputLogger(
						repo.get(params.get("parseLogger")));
			}

			if (params.contains("genlex")) {
				builder.setGenlex(repo.get(params.get("genlex")), repo.get(
						ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE));
			}

			builder.setResumeLearning(params.getAsBoolean("resume", false));

			if (params.contains("maxSentenceLength")) {
				builder.setMaxSentenceLength(
						Integer.valueOf(params.get("maxSentenceLength")));
			}

			if (params.contains("iter")) {
				builder.setNumIterations(Integer.valueOf(params.get("iter")));
			}

			if (params.contains("sortData")) {
				builder.setSortData(params.getAsBoolean("sortData"));
			}

			if (params.contains("filterFactory")) {
				builder.setFilterFactory(repo.get(params.get("filterFactory")));
			}

			for (final String id : params.getSplit("postIteration")) {
				builder.addPostIteration(repo.get(id));
			}

			if (params.contains("conditionedBeam")) {
				builder.setConditionedInferenceBeam(
						params.getAsInteger("conditionedBeam"));
			}

			return builder.build();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(), AbstractGradLearner.class)
					.addParam("resume", Boolean.class,
							"Resume learning and skip initialization actions (default: false)")
					.setDescription(
							"AMR-specific learner with stochastic gradient updates.")
					.addParam("data", "id", "Training data")
					.addParam("genlex", ILexiconGeneratorPrecise.class,
							"GENLEX procedure")
					.addParam("estimator", IWeightUpdateProcedure.class,
							"Parameter estimation update rule")
					.addParam("gradient", IGradientFunction.class,
							"Functin to compute the gradient")
					.addParam("parseLogger", "id",
							"Parse logger for debug detailed logging of parses")
					.addParam("genlexbeam", "int",
							"Beam to use for GENLEX inference (parsing).")
					.addParam("maxSentenceLength", "int",
							"Max sentence length to process")
					.addParam("sortData", Boolean.class,
							"Sort the data according to sentence length in ascending order (default: false)")
					.addParam("iter", "int", "Number of training iterations")
					.addParam("postIteration", Runnable.class,
							"Task to run after each iteration")
					.build();
		}

	}

}
