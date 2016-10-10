package edu.uw.cs.lil.amr.learn.batch.distributed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
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

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.spf.reliabledist.EnvironmentConfig;
import edu.cornell.cs.nlp.spf.reliabledist.JobFuture;
import edu.cornell.cs.nlp.spf.reliabledist.ReliableManager;
import edu.cornell.cs.nlp.spf.test.exec.distributed.ExecTestEnvironment;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.system.MemoryReport;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.batch.AbstractBatchLearner;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.IsValidAmr;

public abstract class AbstractDistributedBatchLearner
		extends AbstractBatchLearner {

	public static final ILogger			LOG						= LoggerFactory
			.create(AbstractDistributedBatchLearner.class);

	private final LearningEnvironment	environment;
	protected final ReliableManager		manager;
	protected boolean					remoteModelIsUpToDate	= false;

	public AbstractDistributedBatchLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean pruneLexicon,
			BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> votingProcedure,
			ReliableManager manager, IWeightUpdateProcedure estimator,
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
		this.manager = manager;
		this.environment = new LearningEnvironment();
	}

	@Override
	public final void train(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		train(model, Collections.emptyList());
	}

	@Override
	protected boolean addAlignmentEntries(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		if (super.addAlignmentEntries(epochNumber, model)) {
			remoteModelIsUpToDate = false;
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void doLexiconInduction(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {

		final long startTime = System.currentTimeMillis();

		// Update GENLEX. We update before every lexicon induction epoch since
		// it might contain new templates. Templates are updated as new entries
		// are added to the model's lexicon via the model listener (assuming the
		// GENLEX procedure registered as a listener).
		manager.updateEnviroment(LearningEnvironment.setGenlex(genlex));

		// Update the environment with the most current model.
		if (!remoteModelIsUpToDate) {
			if (!manager
					.updateEnviroment(LearningEnvironment.setModel(model))) {
				LOG.error("Failed to update environment");
				throw new IllegalStateException();
			}
			remoteModelIsUpToDate = true;
		}

		final List<JobFuture<LexiconInductionResult>> futures = new ArrayList<>(
				trainingData.size());
		int itemCounter = -1;
		for (final LabeledAmrSentence dataItem : trainingData) {
			itemCounter++;
			futures.add(manager.execute(new LexiconInductionJob(dataItem,
					reachableDuringLastEpoch.contains(itemCounter))));
		}

		boolean working = true;
		final long distStartTime = System.currentTimeMillis();
		while (working) {
			working = false;
			int completed = 0;
			JobFuture<LexiconInductionResult> remainingFuture = null;
			for (final JobFuture<LexiconInductionResult> future : futures) {
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

		final Iterator<LabeledAmrSentence> dataIterator = trainingData
				.iterator();
		final Iterator<JobFuture<LexiconInductionResult>> futureIterator = futures
				.iterator();
		itemCounter = -1;
		long computeTime = 0;
		final List<ILexiconImmutable<LogicalExpression>> generatedLexicons = new LinkedList<>();
		while (dataIterator.hasNext()) {
			final LabeledAmrSentence dataItem = dataIterator.next();
			final JobFuture<LexiconInductionResult> future = futureIterator
					.next();

			// Log sample header.
			LOG.info("%d : ================== [%d / LEX]", ++itemCounter,
					epochNumber);
			LOG.info("Sample type: %s", dataItem.getClass().getSimpleName());
			LOG.info("%s", dataItem);

			final LexiconInductionResult result;
			try {
				result = future.get();
			} catch (InterruptedException | ExecutionException e) {
				LOG.error("Job failed: %s", e);
				continue;
			}

			try {
				LOG.info(future.getLog());
			} catch (final InterruptedException e) {
				LOG.error("Failed to get log due to an exception: %s", e);
			}

			// Record stored statistics.
			if (!result.skipped) {
				stats.count("Processed for lexical induction", epochNumber);
				stats.mean("GENLEX", result.genlexTime / 1000.0, "sec");
				if (result.generatedLexicon.size() > 0) {
					stats.count("Reachable under GENLEX", epochNumber);
				}
				if (result.newLexicalEntries > 0) {
					stats.appendSampleStat(itemCounter, epochNumber,
							result.newLexicalEntries);
				}
			}
			stats.mean("Sample lexical induction", result.totalTime / 1000.0,
					"sec");
			computeTime += result.totalTime;

			// Add the generated lexicon to the list of lexicons.
			generatedLexicons.add(result.generatedLexicon);
		}

		// Log speedup.
		final long realTotalTime = System.currentTimeMillis() - startTime;
		LOG.info("Distribution speedup:");
		LOG.info("Real time: %.3f, compute time: %.3f, speedup: %.3f",
				realTotalTime / 1000.0, computeTime / 1000.0,
				computeTime / (double) realTotalTime);

		// Add all the entries to the model.
		if (updateModel(model, generatedLexicons)) {
			remoteModelIsUpToDate = false;
		}
	}

	protected LearningEnvironment getEnvironment() {
		return environment;
	}

	@Override
	protected boolean pruneLexicon(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			Set<LexicalEntry<LogicalExpression>> viterbiEntries) {
		final boolean changed = super.pruneLexicon(epochNumber, model,
				viterbiEntries);
		if (changed) {
			remoteModelIsUpToDate = false;
		}
		return changed;
	}

	protected void train(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			List<EnvironmentConfig<?>> preTrainEnvUpdates) {
		// Update the manager's environment with the configuration of this
		// learner.
		final List<EnvironmentConfig<?>> updates = new ArrayList<>(
				preTrainEnvUpdates);
		updates.add(
				LearningEnvironment.setMaxSentenceLength(maxSentenceLength));
		updates.add(LearningEnvironment.setCategoryServices(categoryServices));
		updates.add(LearningEnvironment.updateParser(parser));
		updates.add(LearningEnvironment.setFilterFactory(filterFactory));
		updates.add(LearningEnvironment
				.updateParserOutputLogger(parserOutputLogger));
		updates.add(LearningEnvironment
				.setConditionedInferenceBeam(conditionedInferenceBeam));
		updates.add(LearningEnvironment.setGradientFunction(gradientFunction));
		manager.setupEnviroment(getEnvironment());
		manager.updateEnviroment(updates);

		super.train(model);
	}

	private static class LexiconInductionJob
			implements Function<LearningEnvironment, LexiconInductionResult>,
			Serializable {

		private static final long			serialVersionUID	= -5926342583825314214L;
		private final LabeledAmrSentence	dataItem;

		/**
		 * Flag to indicate if the data item was reachable in the previous
		 * epoch.
		 */
		private final boolean				reachableInPrev;

		public LexiconInductionJob(LabeledAmrSentence dataItem,
				boolean reachableInPrev) {
			this.dataItem = dataItem;
			this.reachableInPrev = reachableInPrev;
		}

		@Override
		public LexiconInductionResult apply(LearningEnvironment env) {

			final IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model = env.model;
			final ICategoryServices<LogicalExpression> categoryServices = env.categoryServices;
			final ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex = env.genlex;

			// Record sample processing start time.
			final long startTime = System.currentTimeMillis();

			// Skip sample if reachable in last epoch.
			if (reachableInPrev) {
				LOG.info("Reachable during last epoch -- skipping");
				return new LexiconInductionResult(
						System.currentTimeMillis() - startTime);
			}

			// Skip sample, if over the length limit.
			if (dataItem.getSample().getSample().getTokens()
					.size() > env.maxSentenceLength) {
				LOG.info("Training sample too long, skipping");
				return new LexiconInductionResult(
						System.currentTimeMillis() - startTime);
			}

			// Skip sample of the label is invalid.
			if (!IsValidAmr.of(dataItem.getLabel(), true, true)) {
				LOG.warn("Annotated LF is an invalid AMR -- skipping");
				return new LexiconInductionResult(
						System.currentTimeMillis() - startTime);
			}

			// Generate lexical entries.
			final long genlexStart = System.currentTimeMillis();

			final ILexiconImmutable<LogicalExpression> generatedLexicon = genlex
					.generate(dataItem, model, categoryServices);
			final long genlexTime = System.currentTimeMillis() - genlexStart;
			LOG.info("Generated lexicon size = %d (%.4fsec)",
					generatedLexicon.size(), genlexTime / 1000.0);

			// Log the newly created entries.
			int newLexicalEntries = 0;
			for (final LexicalEntry<LogicalExpression> entry : generatedLexicon
					.toCollection()) {
				if (genlex.isGenerated(entry)) {
					if (!model.getLexicon().contains(entry)) {
						++newLexicalEntries;
						LOG.info("Generated new LexicalEntry: [%s] %s [%s]",
								entry.getOrigin(), entry,
								model.getTheta().printValues(
										model.computeFeatures(entry)));

					}
					for (final LexicalEntry<LogicalExpression> linkedEntry : entry
							.getLinkedEntries()) {
						if (!model.getLexicon().contains(linkedEntry)) {
							LOG.info(
									"Generated new (linked) LexicalEntry: [%s] %s [%s]",
									entry.getOrigin(), linkedEntry,
									model.getTheta().printValues(model
											.computeFeatures(linkedEntry)));
							++newLexicalEntries;
						}
					}
				}
			}

			LOG.info("Generated %d entries", generatedLexicon.size());

			final long totalTime = System.currentTimeMillis() - startTime;
			LOG.info("Total sample lexical induction time: %.4fsec",
					totalTime / 1000.0);
			LOG.info("System memory: %s", MemoryReport.generate());
			return new LexiconInductionResult(generatedLexicon, genlexTime,
					newLexicalEntries, totalTime);

		}

	}

	private static class LexiconInductionResult implements Serializable {
		private static final long							serialVersionUID	= -4846358599787720549L;
		private final ILexiconImmutable<LogicalExpression>	generatedLexicon;
		private final long									genlexTime;
		private final int									newLexicalEntries;
		private final boolean								skipped;
		private final long									totalTime;

		public LexiconInductionResult(
				ILexiconImmutable<LogicalExpression> generatedLexicon,
				long genlexTime, int newLexicalEntries, long totalTime) {
			this.genlexTime = genlexTime;
			this.newLexicalEntries = newLexicalEntries;
			this.totalTime = totalTime;
			this.skipped = false;
			this.generatedLexicon = generatedLexicon;
		}

		public LexiconInductionResult(long totalTime) {
			this.totalTime = totalTime;
			this.skipped = true;
			this.generatedLexicon = new Lexicon<>();
			this.genlexTime = 0;
			this.newLexicalEntries = 0;
		}

	}

	protected static class LearningEnvironment extends
			ExecTestEnvironment<SituatedSentence<AMRMeta>, LogicalExpression> {
		private static final String																																		CONFIG_CATEGORY_SERVICES			= "categoryServices";
		private static final String																																		CONFIG_CONDITIONED_INFERENCE_BEAM	= "conditionedInferenceBeam";
		private static final String																																		CONFIG_FILTER_FACTORY				= "filterFactory";
		private static final String																																		CONFIG_GENLEX						= "genlex";
		private static final String																																		CONFIG_GRADIENT_FUNCTION			= "gradientFunction";
		private static final String																																		CONFIG_MAX_SENTENCE_LENGTH			= "maxSentenceLength";
		private static final String																																		CONFIG_MODEL						= "model";
		private static final String																																		CONFIG_MODEL_PARAMS					= "modelSetParams";
		private static final String																																		CONFIG_PARSER						= "parser";
		private static final String																																		CONFIG_PARSER_OUTPUT_LOGGER			= "parserOutputLogger";
		private static final long																																		serialVersionUID					= -4284828823131925469L;

		private ICategoryServices<LogicalExpression>																													categoryServices;
		private Integer																																					conditionedInferenceBeam			= null;
		private IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>												filterFactory						= null;
		private ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>	genlex;
		private IGradientFunction																																		gradientFunction;
		private int																																						maxSentenceLength;
		private JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>																				model								= null;
		private GraphAmrParser																																				parser								= null;
		private IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>																				parserOutputLogger					= null;

		public LearningEnvironment() {
		}

		public static EnvironmentConfig<ICategoryServices<LogicalExpression>> setCategoryServices(
				ICategoryServices<LogicalExpression> categoryServices) {
			return new EnvironmentConfig<ICategoryServices<LogicalExpression>>(
					CONFIG_CATEGORY_SERVICES, categoryServices);
		}

		public static EnvironmentConfig<Integer> setConditionedInferenceBeam(
				int beam) {
			return new EnvironmentConfig<>(CONFIG_CONDITIONED_INFERENCE_BEAM,
					beam);
		}

		public static EnvironmentConfig<IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>> setFilterFactory(
				IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory) {
			return new EnvironmentConfig<>(CONFIG_FILTER_FACTORY,
					filterFactory);
		}

		public static EnvironmentConfig<ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>> setGenlex(
				ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex) {
			return new EnvironmentConfig<>(CONFIG_GENLEX, genlex);
		}

		public static EnvironmentConfig<IGradientFunction> setGradientFunction(
				IGradientFunction gradientFunction) {
			return new EnvironmentConfig<IGradientFunction>(
					CONFIG_GRADIENT_FUNCTION, gradientFunction);
		}

		public static EnvironmentConfig<Integer> setMaxSentenceLength(
				int maxSentenceLength) {
			return new EnvironmentConfig<Integer>(CONFIG_MAX_SENTENCE_LENGTH,
					maxSentenceLength);
		}

		public static EnvironmentConfig<JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> setModel(
				JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
			return new EnvironmentConfig<JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>(
					CONFIG_MODEL, model);
		}

		public static EnvironmentConfig<IHashVectorImmutable> setModelParameters(
				IHashVectorImmutable weights) {
			return new EnvironmentConfig<>(CONFIG_MODEL_PARAMS, weights);
		}

		public static EnvironmentConfig<GraphAmrParser> updateParser(
				GraphAmrParser parser) {
			return new EnvironmentConfig<>(CONFIG_PARSER, parser);
		}

		public static EnvironmentConfig<IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>> updateParserOutputLogger(
				IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger) {
			return new EnvironmentConfig<>(CONFIG_PARSER_OUTPUT_LOGGER,
					parserOutputLogger);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void applyUpdate(EnvironmentConfig<?> update) {
			super.applyUpdate(update);
			switch (update.getKey()) {
				case CONFIG_MODEL:
					model = (JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>) update
							.getValue();
					break;
				case CONFIG_CATEGORY_SERVICES:
					categoryServices = (ICategoryServices<LogicalExpression>) update
							.getValue();
					break;
				case CONFIG_GENLEX:
					genlex = (ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>) update
							.getValue();
					break;
				case CONFIG_MAX_SENTENCE_LENGTH:
					maxSentenceLength = (Integer) update.getValue();
					break;
				case CONFIG_PARSER:
					parser = (GraphAmrParser) update.getValue();
					break;
				case CONFIG_FILTER_FACTORY:
					filterFactory = (IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>) update
							.getValue();
					break;
				case CONFIG_GRADIENT_FUNCTION:
					gradientFunction = (IGradientFunction) update.getValue();
					break;
				case CONFIG_PARSER_OUTPUT_LOGGER:
					parserOutputLogger = (IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>) update
							.getValue();
					break;
				case CONFIG_MODEL_PARAMS:
					// Case re-set model weights.
					model.getTheta().clear();
					((IHashVectorImmutable) update.getValue()).addTimesInto(1.0,
							model.getTheta());
					break;
				case CONFIG_CONDITIONED_INFERENCE_BEAM:
					conditionedInferenceBeam = (Integer) update.getValue();
					break;

			}
		}

		public ICategoryServices<LogicalExpression> getCategoryServices() {
			return categoryServices;
		}

		public Integer getConditionedInferenceBeam() {
			return conditionedInferenceBeam;
		}

		public IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> getFilterFactory() {
			return filterFactory;
		}

		public ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> getGenlex() {
			return genlex;
		}

		public IGradientFunction getGradientFunction() {
			return gradientFunction;
		}

		public int getMaxSentenceLength() {
			return maxSentenceLength;
		}

		public JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> getModel() {
			return model;
		}

		public GraphAmrParser getParser() {
			return parser;
		}

		public IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> getParserOutputLogger() {
			return parserOutputLogger;
		}

	}

}
