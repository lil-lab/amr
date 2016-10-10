package edu.uw.cs.lil.amr.learn.batch;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

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
import edu.cornell.cs.nlp.utils.system.MemoryReport;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.IsValidAmr;

public abstract class AbstractVanillaBatchLearner extends AbstractBatchLearner {

	public AbstractVanillaBatchLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean pruneLexicon,
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
	}

	@Override
	protected void doLexiconInduction(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {

		// Iterating over training data to collect entries.
		int itemCounter = -1;
		final List<ILexiconImmutable<LogicalExpression>> generatedLexicons = new LinkedList<>();
		for (final LabeledAmrSentence dataItem : trainingData) {
			// Record sample processing start time.
			final long startTime = System.currentTimeMillis();

			// Log sample header.
			LOG.info("%d : ================== [%d / LEX]", ++itemCounter,
					epochNumber);
			LOG.info("Sample type: %s", dataItem.getClass().getSimpleName());
			LOG.info("%s", dataItem);

			// Skip sample if reachable in last epoch.
			if (reachableDuringLastEpoch.contains(itemCounter)) {
				LOG.info("Reachable during last epoch -- skipping");
				continue;
			}

			// Skip sample, if over the length limit.
			if (dataItem.getSample().getSample().getTokens()
					.size() > maxSentenceLength) {
				LOG.info("Training sample too long, skipping");
				generatedLexicons.add(new Lexicon<>());
				continue;
			}

			// Skip sample of the label is invalid.
			if (!IsValidAmr.of(dataItem.getLabel(), true, true)) {
				LOG.warn("Annotated LF is an invalid AMR -- skipping");
				generatedLexicons.add(new Lexicon<>());
				continue;
			}

			stats.count("Processed for lexical induction", epochNumber);

			// Track the number of data items that propose an entry.
			final ILexiconImmutable<LogicalExpression> generatedLexicon = generateLexicalEntries(
					dataItem, model, itemCounter, epochNumber);
			LOG.info("Generated %d entries", generatedLexicon.size());
			if (generatedLexicon.size() > 0) {
				stats.count("Reachable under GENLEX", epochNumber);
			}
			generatedLexicons.add(generatedLexicon);

			// Record statistics.
			stats.mean("Sample lexical induction",
					(System.currentTimeMillis() - startTime) / 1000.0, "sec");
			LOG.info(
					"Total sample lexical induction time: %.4fsec [mean: %.4fsec]",
					(System.currentTimeMillis() - startTime) / 1000.0,
					stats.getMean("Sample lexical induction"));
			LOG.info("System memory: %s", MemoryReport.generate());

		}

		// Add all the entries to the model.
		updateModel(model, generatedLexicons);
	}

}
