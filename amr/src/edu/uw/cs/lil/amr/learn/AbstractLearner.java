package edu.uw.cs.lil.amr.learn;

import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.learn.ILearner;
import edu.cornell.cs.nlp.spf.learn.LearningStats;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * Generic abstract class for learner code.
 *
 * @author Yoav Artzi
 */
public abstract class AbstractLearner implements
		ILearner<SituatedSentence<AMRMeta>, LabeledAmrSentence, JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> {

	public static final ILogger																																				LOG	= LoggerFactory
			.create(AbstractLearner.class);

	protected final ICategoryServices<LogicalExpression>																													categoryServices;

	/**
	 * The beam size to use when doing conditioned inference. If null, use the
	 * default beam of the parser.
	 */
	protected final Integer																																					conditionedInferenceBeam;

	/**
	 * Number of training epochs.
	 */
	protected final int																																						epochs;

	protected final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>												filterFactory;

	/**
	 * GENLEX procedure. If 'null' skip lexical induction.
	 */
	protected final ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>	genlex;

	protected final IGradientFunction																																		gradientFunction;

	/**
	 * Max sentence length to process. If longer, skip.
	 */
	protected final int																																						maxSentenceLength;

	/**
	 * Joint parser for inference.
	 */
	protected final GraphAmrParser																																			parser;

	/**
	 * Parser output logger.
	 */
	protected final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>																				parserOutputLogger;

	protected final IntConsumer																																				postIteration;

	/**
	 * Indicates that this learner resumes a previous learning cycle. This flag
	 * is used to skip various steps, such as initializing the update
	 * procedure.
	 */
	protected final boolean																																					resumedLearning;

	/**
	 * Learning statistics.
	 */
	protected final LearningStats																																			stats;

	/**
	 * Training data.
	 */
	protected final IDataCollection<LabeledAmrSentence>																														trainingData;

	protected final IWeightUpdateProcedure																																	updateProcedure;

	public AbstractLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam, boolean resumedLearning) {
		this.epochs = numIterations;
		this.updateProcedure = estimator;
		this.gradientFunction = gradientFunction;
		this.conditionedInferenceBeam = conditionedInferenceBeam;
		this.resumedLearning = resumedLearning;
		this.trainingData = sortData
				? new IDataCollection<LabeledAmrSentence>() {
					// formatter:off
					private static final long serialVersionUID	= -4318866295332509604L;
					// formatter:on
					final List<LabeledAmrSentence>data				= StreamSupport
							.stream(trainingData.spliterator(), false)
							.sorted((o1, o2) -> Integer.compare(
									o1.getSample().getSample().getTokens()
											.size(),
									o2.getSample().getSample().getTokens()
											.size()))
							.collect(Collectors.toList());

					@Override
					public Iterator<LabeledAmrSentence> iterator() {
						return data.iterator();
					}

					@Override
					public int size() {
						return data.size();
					}
				} : trainingData;

		this.stats = createLearningStats(this.trainingData.size());

		this.maxSentenceLength = maxSentenceLength;
		this.parser = parser;
		this.parserOutputLogger = parserOutputLogger;
		this.categoryServices = categoryServices;
		this.genlex = genlex;
		this.filterFactory = filterFactory;
		this.postIteration = postIteration;
		LOG.info(
				"Init %s: numIterations=%d,trainingData.size()=%d, maxSentenceLength=%d ...",
				this.getClass().getSimpleName(), numIterations,
				this.trainingData.size(), maxSentenceLength);
		LOG.info("Init %s: ... estimator=%s ...",
				this.getClass().getSimpleName(), estimator);
		LOG.info("Init %s: ... genlex=%s ...", this.getClass().getSimpleName(),
				genlex);
		LOG.info("Init %s: ... gradientFunction=%s",
				this.getClass().getSimpleName(), gradientFunction);
		LOG.info("Init %s: ... resumeLearning=%s",
				this.getClass().getSimpleName(), resumedLearning);
	}

	protected static LearningStats createLearningStats(int trainingDataSize) {
		return new LearningStats.Builder(trainingDataSize)
				.addStat(LearningServices.BASE_PARSE_PRUNED,
						"Base parse pruned")
				.addStat(LearningServices.CORRECT_REACHABLE,
						"Correct parse is reachable under the grammar")
				.addStat(LearningServices.GOLD_OPTIMAL,
						"The best-scoring LF is the annotated one")
				.addStat(LearningServices.GOLD_UNDERSPEC_OPTIMAL,
						"The underspecified form of the best-scoring LF equals the underspecified form of the annotated one")
				.setNumberStat("Number of new lexical entries added")
				.addStat(LearningServices.TRIGGERED_UNREACHABLE_UPDATE,
						"Sample unreachable under the grammar, but triggered an early update")
				.addStat(LearningServices.TRIGGERED_EARLY_UPDATE,
						"Sample triggered an early parameter update")
				.addStat(LearningServices.TRIGGERED_UPDATE,
						"Sample triggered a parameter update")
				.addStat(LearningServices.SECOND_STAGE_CORRECT,
						"Corret result is max-scoring during second-stage gradient computation")
				.addStat(LearningServices.SECOND_STAGE_GRADIENT_GENERATED,
						"Second-stage gradient generated")
				.build();
	}

	protected ILexiconImmutable<LogicalExpression> generateLexicalEntries(
			final LabeledAmrSentence dataItem,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int dataItemNumber, int epochNumber) {

		// Generate lexical entries.
		final long genlexStart = System.currentTimeMillis();

		final ILexiconImmutable<LogicalExpression> generatedLexicon = genlex
				.generate(dataItem, model, categoryServices);
		stats.mean("GENLEX",
				(System.currentTimeMillis() - genlexStart) / 1000.0, "sec");
		LOG.info("Generated lexicon size = %d (%.4fsec) [mean: %.4fsec]",
				generatedLexicon.size(),
				(System.currentTimeMillis() - genlexStart) / 1000.0,
				stats.getMean("GENLEX"));
		if (generatedLexicon.size() > 0) {
			stats.count("Reachable under GENLEX", epochNumber);
		}

		// Log the newly created entries.
		int newLexicalEntries = 0;
		for (final LexicalEntry<LogicalExpression> entry : generatedLexicon
				.toCollection()) {
			if (genlex.isGenerated(entry)) {
				if (!model.getLexicon().contains(entry)) {
					++newLexicalEntries;
					LOG.info("Generated new LexicalEntry: [%s / %.2f] %s [%s]",
							entry.getOrigin(), model.score(entry), entry,
							model.getTheta()
									.printValues(model.computeFeatures(entry)));

				}
				for (final LexicalEntry<LogicalExpression> linkedEntry : entry
						.getLinkedEntries()) {
					if (!model.getLexicon().contains(linkedEntry)) {
						LOG.info(
								"Generated new (linked) LexicalEntry: [%s] %s [%s]",
								entry.getOrigin(), linkedEntry,
								model.getTheta().printValues(
										model.computeFeatures(linkedEntry)));
						++newLexicalEntries;
					}
				}
			}
		}

		// Record statistics.
		if (newLexicalEntries > 0) {
			stats.appendSampleStat(dataItemNumber, epochNumber,
					newLexicalEntries);
		}

		return generatedLexicon;
	}

}
