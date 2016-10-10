package edu.uw.cs.lil.amr.learn.gradient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorUtils;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.CKYDerivation;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.CKYParserOutput;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.chart.Cell;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.chart.Chart;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.Span;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphParserOutput;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.graph.IJointGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.math.LogSumExp;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.GetAmrSubExpressions;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.learn.LearningServices;
import edu.uw.cs.lil.amr.parser.AbstractAmrParser;
import edu.uw.cs.lil.amr.parser.EvaluationResult;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.GraphAmrParserOutput;

/**
 * Computes an approximate gradient for a single data item with the objective of
 * maximizing the joint probability p(a | x) = \\sum_z p(a | z, x) \\sum_y p(z,
 * y | x). Can compute the full gradient, or a partial one (for early updates),
 * if the full one is not reachable.
 *
 * @author Yoav Artzi
 */
public class SimpleGradient implements IGradientFunction {

	public static final ILogger	LOG					= LoggerFactory
			.create(SimpleGradient.class);
	private static final long	serialVersionUID	= 4094997131637662028L;
	private final Integer		conditionedBeam;
	private final boolean		doPartialUpdates;
	private final boolean		doSecondStageUpdates;
	private final boolean		doUnreachableUpdates;
	private final boolean		hard;

	public SimpleGradient(boolean hard, Integer conditionedBeam,
			boolean doSecondStageUpdates, boolean doPartialUpdates,
			boolean doUnreachableUpdates) {
		this.hard = hard;
		this.conditionedBeam = conditionedBeam;
		this.doSecondStageUpdates = doSecondStageUpdates;
		this.doPartialUpdates = doPartialUpdates;
		this.doUnreachableUpdates = doUnreachableUpdates;
	}

	@Override
	public GradientComputation of(LabeledAmrSentence dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			int dataItemNumber, int epochNumber, GraphAmrParser parser,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger) {

		// Accumulate the collection of stats. This allows us to transfer the
		// updates on the network, if the stats objects in on another machine.
		final StatUpdates statUpdates = new StatUpdates();

		// Parse with current model conditioned on getting the correct logical
		// form.
		final GraphAmrParserOutput conditionedOutput = parser.parse(
				dataItem.getSample(), dataItemModel,
				filterFactory.createJointFilter(dataItem), conditionedBeam);
		// The values that are pending to be recoded in stats must be extracted,
		// so we won't be forced to transfer the entire output.
		final long inferenceTime = conditionedOutput.getInferenceTime();
		statUpdates.mean("conditioned inference", inferenceTime / 1000.0,
				"sec");
		LOG.info("Conditioned inference time: %.4fsec",
				conditionedOutput.getInferenceTime() / 1000.0);
		parserOutputLogger.log(conditionedOutput, dataItemModel, String
				.format("%d-%d-conditioned", epochNumber, dataItemNumber));

		// Conditioned derivations.
		final List<GraphAmrDerivation> conditionedDerivations = conditionedOutput
				.getDerivations(dataItem.getLabel());

		// Logging.
		LearningServices.logConditionedDerivations(statUpdates,
				conditionedOutput, dataItem, epochNumber, dataItemNumber,
				dataItemModel, conditionedDerivations);

		// Parse with the current model.
		final GraphAmrParserOutput modelOutput = parser
				.parse(dataItem.getSample(), dataItemModel);
		final long modelInferenceTime = modelOutput.getInferenceTime();
		statUpdates.mean("model parse", modelInferenceTime / 1000.0, "sec");
		parserOutputLogger.log(modelOutput, dataItemModel,
				String.format("%d-%d-model", epochNumber, dataItemNumber));
		LOG.info("Model inference time: %.4fsec",
				modelOutput.getInferenceTime() / 1000.0);

		// The underspecified form is used for logging and early update.
		final Category<LogicalExpression> underspecifiedCategory = Category
				.create(AMRServices.getCompleteSentenceSyntax(),
						AMRServices.underspecifyAndStrip(dataItem.getLabel()));

		LearningServices.logModelDerivations(modelOutput, statUpdates,
				dataItemNumber, epochNumber, dataItem, dataItemModel,
				conditionedDerivations, underspecifiedCategory);

		if (conditionedDerivations.isEmpty()) {
			LOG.info(
					"Correct LF is not reachable, trying to compute unreachable partial gradient");
			return computeUnreachableGradient(dataItem, conditionedOutput,
					modelOutput, dataItemModel, statUpdates, dataItemNumber,
					epochNumber, parser);
		}

		// Compute the set of lexical entries used in the viterbi correct
		// derivations. Each viterbi derivations packs multiple derivations,
		// some of them not max-scoring, so we take the max lexical entries.
		final Set<LexicalEntry<LogicalExpression>> viterbiEntries = conditionedOutput
				.getMaxDerivations(dataItem.getLabel()).stream()
				.map(GraphAmrDerivation::getMaxLexicalEntries)
				.flatMap(s -> s.stream()).collect(Collectors.toSet());

		// Step A: Compute the positive half of the gradient: conditioned on
		// getting successful validation

		// Create the gradient.
		final IHashVector gradient = HashVectorFactory.create();

		if (hard) {
			// Case hard updates: the positive portion of the gradient is the
			// mean of the features used in all max-scoring correct parses.
			assert !conditionedDerivations
					.isEmpty() : "Unexpected state, should have been verified earlier";
			final IHashVector maxFeatures = HashVectorFactory.create();
			for (final GraphAmrDerivation derivation : conditionedDerivations) {
				derivation.getMeanMaxFeatures().addTimesInto(
						1.0 / conditionedDerivations.size(), maxFeatures);
			}
			maxFeatures.addTimesInto(1.0, gradient);
			LOG.info("Positive max features: %s", maxFeatures);
		} else {
			final double logConditionedNorm = conditionedOutput
					.logNorm(dataItem.getLabel());
			assert logConditionedNorm != Double.NEGATIVE_INFINITY : "No positive expected features, unexpected state given earlier verification of conditioned derivations";
			// Case have complete valid parses.
			final IHashVector expectedFeatures = conditionedOutput
					.logExpectedFeatures(dataItem.getLabel());
			expectedFeatures.add(-logConditionedNorm);
			expectedFeatures.applyFunction(value -> Math.exp(value));
			expectedFeatures.dropNoise();
			expectedFeatures.addTimesInto(1.0, gradient);
			LOG.info("Positive expected features: %s", expectedFeatures);
		}

		// Step B: Compute the negative half of the gradient: expectation under
		// the current model.

		// Compute optimality flag.
		boolean labelIsOptimal;
		final List<? extends IJointGraphDerivation<LogicalExpression, LogicalExpression>> maxDerivations = modelOutput
				.getMaxDerivations();
		if (maxDerivations.size() == 1 && dataItem.getLabel()
				.equals(maxDerivations.get(0).getResult())) {
			labelIsOptimal = true;
		} else {
			labelIsOptimal = false;
		}

		// Check if the base parse was pruned.
		final boolean basePruned = modelOutput.getBaseParserOutput()
				.getDerivations(
						c -> StripOverload.of(c).equals(underspecifiedCategory))
				.isEmpty();

		final double logNorm = modelOutput.logNorm();
		if (logNorm == Double.NEGATIVE_INFINITY || basePruned) {
			LOG.info(
					"No negative expected features (or base derivation pruned), trying a chart-only early update");
			statUpdates.count(
					"No negative expected features (or base derivation pruned)",
					epochNumber);
			return computePartialGradient(epochNumber, dataItemNumber,
					underspecifiedCategory,
					conditionedOutput.getBaseParserOutput(),
					modelOutput.getBaseParserOutput(), statUpdates,
					dataItemModel, dataItem, parser);
		} else {
			// Case have complete parses.
			final IHashVector expectedFeatures = modelOutput
					.logExpectedFeatures();
			expectedFeatures.add(-logNorm);
			expectedFeatures.applyFunction(value -> Math.exp(value));
			expectedFeatures.dropNoise();
			expectedFeatures.addTimesInto(-1.0, gradient);
			LOG.info("Negative expected features: %s", expectedFeatures);
		}

		if (gradient.isBad()) {
			LOG.error("Bad gradient: %s -- log-norm: %.4f", gradient, logNorm);
			LOG.error("feats: %s",
					dataItemModel.getTheta().printValues(gradient));
			throw new IllegalStateException("Bad gradient");
		}

		statUpdates.appendSampleStat(dataItemNumber, epochNumber,
				LearningServices.TRIGGERED_UPDATE);

		LOG.info("Gradient: %s", gradient);

		return new GradientComputation(gradient, false, labelIsOptimal,
				statUpdates, viterbiEntries,
				modelOutput.isExact() && conditionedOutput.isExact());

	}

	private GradientComputation attemptSecondStageGradient(
			LabeledAmrSentence dataItem, AbstractAmrParser<?> parser,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			StatUpdates statUpdates, int dataItemNumber, int epochNumber) {
		final IHashVector gradient = computeSecondStageGradient(dataItem,
				parser, model, statUpdates, dataItemNumber, epochNumber);

		if (gradient == null) {
			return new GradientComputation(null, true, false, statUpdates,
					Collections.emptySet(), false);
		} else {
			LOG.info(
					"Computed second stage partial gradient only -- first stage computation failed");
			statUpdates.appendSampleStat(dataItemNumber, epochNumber,
					LearningServices.TRIGGERED_UNREACHABLE_UPDATE);
			return new GradientComputation(gradient, true, false, statUpdates,
					Collections.emptySet(), false);
		}
	}

	private GradientComputation computePartialGradient(int epochNumber,
			int dataItemNumber,
			Category<LogicalExpression> underspecifiedCategory,
			IGraphParserOutput<LogicalExpression> conditionedOutput,
			IGraphParserOutput<LogicalExpression> modelOutput,
			StatUpdates statUpdates,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			LabeledAmrSentence dataItem, GraphAmrParser parser) {

		if (!doPartialUpdates) {
			LOG.info("Partial updates disalbed -- skipping to second stage");
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}

		LOG.info("Trying within-chart early update");

		// If we have no complete conditioned derivation, skip early update
		// altogether.
		final List<? extends IGraphDerivation<LogicalExpression>> conditionedViterbiDerivations = conditionedOutput
				.getMaxDerivations(e -> StripOverload.of(e)
						.equals(underspecifiedCategory));
		if (conditionedViterbiDerivations.isEmpty()) {
			LOG.info(
					"No positive portion of the gradient for early update, skipping");
			statUpdates.count(
					"No positive portion of the gradient for early update",
					epochNumber);
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}

		// Collect the lexical entries used in the viterbi derivations. Each
		// viterbi derivations packs multiple derivations, some of them not
		// max-scoring, so we take the max lexical entries.
		final Set<LexicalEntry<LogicalExpression>> viterbiEntries = conditionedViterbiDerivations
				.stream().map(IGraphDerivation::getMaxLexicalEntries)
				.flatMap(s -> s.stream()).collect(Collectors.toSet());

		if (!(conditionedOutput instanceof CKYParserOutput
				&& modelOutput instanceof CKYParserOutput)) {
			LOG.error("Unexpected parser output objects -- no chart avaialble");
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}

		// Create the gradient.
		final IHashVector gradient = HashVectorFactory.create();
		final CKYParserOutput<LogicalExpression> ckyConditionedOutput = (CKYParserOutput<LogicalExpression>) conditionedOutput;
		final CKYParserOutput<LogicalExpression> ckyModelOutput = (CKYParserOutput<LogicalExpression>) modelOutput;

		// Iterate over all the correct derivation.
		for (final CKYDerivation<LogicalExpression> derivation : hard
				? ckyConditionedOutput.getMaxDerivations(
						e -> StripOverload.of(e).equals(underspecifiedCategory))
				: ckyConditionedOutput.getDerivations(e -> StripOverload.of(e)
						.equals(underspecifiedCategory))) {

			// Get the spans that require updating with the categories that
			// should condition the positive portion of the gradient.
			final Map<Span, Set<Cell<LogicalExpression>>> spanDictionary = ckyModelOutput
					.getChart()
					.getMaxNonOverlappingSpans(derivation.getCell(), hard);

			// Iterate over all spans and categories to generate the updates.
			for (final Entry<Span, Set<Cell<LogicalExpression>>> entry : spanDictionary
					.entrySet()) {
				final IHashVectorImmutable spanGradient = computeSpanGradient(
						entry.getKey(), entry.getValue(), ckyConditionedOutput,
						ckyModelOutput, dataItemModel, dataItem);
				if (spanGradient != null) {
					// Add the span gradient to the gradient.
					spanGradient.addTimesInto(1.0, gradient);
				}
			}
		}

		statUpdates.appendSampleStat(dataItemNumber, epochNumber,
				LearningServices.TRIGGERED_EARLY_UPDATE);

		// Try to compute and add the gradient from the second stage.
		final IHashVectorImmutable secondStage = computeSecondStageGradient(
				dataItem, parser, dataItemModel, statUpdates, dataItemNumber,
				epochNumber);
		if (secondStage != null) {
			secondStage.addTimesInto(1.0, gradient);
		}

		LOG.info("Gradient (partial): %s", gradient);

		return new GradientComputation(gradient.size() == 0 ? null : gradient,
				true, false, statUpdates, viterbiEntries, false);
	}

	/**
	 * Compute the gradient for the second stage only. This is a rough
	 * approximation of the real computation starting from the gold
	 * underspecified logical form. Specifically, the underspecified logical
	 * form is missing the constant overloading information that results from
	 * the parse tree. Still it provides informative updates to most of the
	 * features used in the second stage.
	 */
	private IHashVector computeSecondStageGradient(LabeledAmrSentence dataItem,
			AbstractAmrParser<?> parser,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			StatUpdates statUpdates, int dataItemNumber, int epochNumber) {

		if (!doSecondStageUpdates) {
			return null;
		}

		final LogicalExpression underspecifiedLabel = AMRServices
				.underspecify(dataItem.getLabel());
		final LogicalExpression underspecifiedStrippedLabel = AMRServices
				.stripSkolemIds(underspecifiedLabel);

		// Get correct evaluation result.
		final EvaluationResult correctEvaluation = parser
				.conditionedSecondStageInference(underspecifiedStrippedLabel,
						dataItem.getSample().getState(), model,
						dataItem.getLabel());

		// Get all evaluation results.
		final Pair<List<EvaluationResult>, Boolean> inferencePair = parser
				.secondStageInference(underspecifiedStrippedLabel,
						dataItem.getSample().getState(), model);
		if (inferencePair == null || correctEvaluation == null) {
			LOG.info("Failed to compute second stage gradient -- skipping");
			return null;
		}

		// If the correct is not present, add it to the list. This will make the
		// expected feature counts a bit more accurate.
		final List<EvaluationResult> evaluations;
		if (inferencePair.first().contains(correctEvaluation)) {
			evaluations = inferencePair.first();
		} else {
			LOG.debug(
					"Correct second-stage evaluation not generated - augmenting list of evaluation with correct second stage");
			final List<EvaluationResult> augmented = new ArrayList<>(
					inferencePair.first());
			augmented.add(correctEvaluation);
			evaluations = Collections.unmodifiableList(augmented);
		}

		LOG.info("Correct features: %s",
				model.getTheta().printValues(correctEvaluation.getFeatures()));

		// Log if the correct evaluation is max-scoring or not.
		final double correctScore = correctEvaluation.getScore();
		boolean found = false;
		for (final EvaluationResult result : evaluations) {
			final double score = result.getScore();
			if (!result.getResult().equals(correctEvaluation.getResult())
					&& score >= correctScore) {
				LOG.info(
						"Correct not max-scoring [%.2f]: higher scored [%.2f] wrong evaluation exists (logging first found):",
						correctScore, score);
				// Log the first.
				LOG.info(AMRServices.toString(result.getResult()));
				LOG.info(model.getTheta().printValues(result.getFeatures()));
				final IHashVector delta = correctEvaluation.getFeatures()
						.addTimes(-1.0, result.getFeatures());
				delta.dropNoise();
				delta.dropZeros();
				LOG.info("Delta: %s", model.getTheta().printValues(delta));
				found = true;
				break;
			}
		}
		if (!found) {
			LOG.info("Correct is max-scoring");
			statUpdates.appendSampleStat(dataItemNumber, epochNumber,
					LearningServices.SECOND_STAGE_CORRECT);
		}

		// Compute the gradient.
		final IHashVector gradient = HashVectorFactory.create();

		// The process is identical for hard updates or soft ones, since there's
		// at most one second stage derivation leading to the correct final
		// logical expression. This is not true in the general inference case,
		// due to overloading of constants with surface form. However, this
		// information is not present in this approximation.

		// Compute the expected features of the unconditioned (negative) part of
		// the update.
		final IHashVector expectedFeatures = HashVectorFactory.create();
		final double norm = LogSumExp.of(
				evaluations.stream().map((evaluation) -> evaluation.getScore())
						.collect(Collectors.toList()));
		for (final EvaluationResult evaluation : evaluations) {
			final double logWeight = evaluation.getScore();
			HashVectorUtils.logSumExpAdd(logWeight, evaluation.getFeatures(),
					expectedFeatures);
		}
		expectedFeatures.add(-norm);
		expectedFeatures.applyFunction(value -> Math.exp(value));
		expectedFeatures.dropNoise();
		expectedFeatures.addTimesInto(-1.0, gradient);

		// Add to the gradient the features of the correct evaluation.
		correctEvaluation.getFeatures().addTimesInto(1.0, gradient);
		gradient.dropNoise();
		gradient.dropZeros();

		if (gradient.size() != 0) {
			statUpdates.appendSampleStat(dataItemNumber, epochNumber,
					LearningServices.SECOND_STAGE_GRADIENT_GENERATED);
		}

		return gradient;
	}

	private IHashVectorImmutable computeSpanGradient(Span span,
			Set<Cell<LogicalExpression>> conditionedCells,
			CKYParserOutput<LogicalExpression> ckyConditionedOutput,
			CKYParserOutput<LogicalExpression> ckyModelOutput,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			LabeledAmrSentence dataItem) {
		assert conditionedCells.stream()
				.allMatch(c -> c.getStart() == span.getStart()
						&& c.getEnd() == span.getEnd());

		final IHashVector spanGradient = HashVectorFactory.create();

		final Set<Category<LogicalExpression>> categories = conditionedCells
				.stream().map(c -> c.getCategory()).collect(Collectors.toSet());
		LOG.info("----------- Span: %s %s", span, dataItem.getSample()
				.getTokens().sub(span.getStart(), span.getEnd() + 1));
		LOG.info("Categories: %s", categories);

		if (hard) {
			// Average the max features from all the categories.
			final int numCells = conditionedCells.size();
			for (final Cell<LogicalExpression> cell : conditionedCells) {
				final IHashVector features = cell
						.computeMaxAvgFeaturesRecursively();
				LOG.info("Max features: %s -> %s", cell.getCategory(),
						features);
				features.addTimesInto(1.0 / numCells, spanGradient);
			}
		} else {
			// Create the positive portion of the gradient.
			final double logNorm = ckyConditionedOutput.getChart()
					.logNorm(categories::contains, span);
			final IHashVector expectedFeatures = ckyConditionedOutput.getChart()
					.logExpectedFeatures(categories::contains, span);
			expectedFeatures.add(-logNorm);
			expectedFeatures.applyFunction(value -> Math.exp(value));
			expectedFeatures.dropNoise();
			expectedFeatures.addTimesInto(1.0, spanGradient);
			LOG.info("Positive expected features (early update): %s",
					expectedFeatures);
		}

		// Negative portion of the gradient.
		final double logNorm = ckyModelOutput.getChart().logNorm(c -> true,
				span);
		if (logNorm == Double.NEGATIVE_INFINITY) {
			LOG.info(
					"No negative early update for this span, skipping early update for it");
			return null;
		} else {
			final IHashVector expectedFeatures = ckyModelOutput.getChart()
					.logExpectedFeatures(
							(IFilter<Category<LogicalExpression>>) c -> true,
							span);
			expectedFeatures.add(-logNorm);
			expectedFeatures.applyFunction(value -> Math.exp(value));
			expectedFeatures.dropNoise();
			expectedFeatures.addTimesInto(-1.0, spanGradient);
			LOG.info("Negative expected features (early update): %s",
					expectedFeatures);
		}

		// Validate the span gradient.
		if (spanGradient.isBad()) {
			LOG.error("Bad partial gradient: %s -- log-norm: %.4f",
					spanGradient, logNorm);
			LOG.error("feats: %s",
					dataItemModel.getTheta().printValues(spanGradient));
			throw new IllegalStateException("Bad gradient");
		}

		return spanGradient;
	}

	/**
	 * Computers a gradient when the base parse is unreachable under the current
	 * grammar (during conditioned inference). The gradient is computed based on
	 * greedily identified maximal spans that contain sub-expressions of the
	 * labeled LF and follow CCGBank parsing constraints.
	 */
	private GradientComputation computeUnreachableGradient(
			LabeledAmrSentence dataItem, GraphAmrParserOutput conditionedOutput,
			GraphAmrParserOutput modelOutput,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			StatUpdates statUpdates, int dataItemNumber, int epochNumber,
			GraphAmrParser parser) {

		if (!doUnreachableUpdates) {
			LOG.info(
					"Unrechable updates disabled -- trying second stage update");
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}

		if (!(conditionedOutput
				.getBaseParserOutput() instanceof CKYParserOutput)) {
			LOG.error("Unexpected parser output objects -- no chart avaialble");
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}

		// Get all AMR sub-expression from the stripped and underspecified
		// labeled LF. Remove solitary references, as they lead to noisy
		// updates.
		final Set<LogicalExpression> subExpressions = GetAmrSubExpressions
				.of(AMRServices.underspecifyAndStrip(dataItem.getLabel()))
				.stream().collect(Collectors.toSet());

		// Identify the max-scoring spans that hold sub-expressions of the
		// labeled LF. If multiple spans contain the same sub-expression, prefer
		// the one with the highest score.
		final CKYParserOutput<LogicalExpression> baseConditionedOutput = (CKYParserOutput<LogicalExpression>) conditionedOutput
				.getBaseParserOutput();
		final Chart<LogicalExpression> chart = baseConditionedOutput.getChart();
		final Map<LogicalExpression, List<Cell<LogicalExpression>>> subExpCells = new HashMap<>();
		final Map<LogicalExpression, Double> subExpCellViterbiScores = new HashMap<>();
		for (final Cell<LogicalExpression> cell : chart) {
			final Category<LogicalExpression> category = cell.getCategory();
			if (category.getSemantics() == null) {
				continue;
			}

			// Verify that the cell is a valid span according to CCGBank
			// constraints, if available.
			final TokenSeq tokens = dataItem.getSample().getTokens()
					.sub(cell.getStart(), cell.getEnd() + 1);
			final Set<Syntax> ccgBankCategories = dataItem
					.getCCGBankCategories(tokens);
			if (ccgBankCategories != null) {
				if (ccgBankCategories.isEmpty()) {
					continue;
				}
				boolean found = false;
				for (final Syntax syntax : ccgBankCategories) {
					if (cell.getCategory().getSyntax().stripAttributes()
							.equals(syntax.stripAttributes())) {
						found = true;
						break;
					}
				}
				if (!found) {
					continue;
				}
			}

			final LogicalExpression semantics = StripOverload.of(
					AMRServices.underspecifyAndStrip(category.getSemantics()));
			if (subExpressions.contains(semantics)) {
				if (!subExpCellViterbiScores.containsKey(semantics)
						|| subExpCellViterbiScores.get(semantics) < cell
								.getViterbiScore()) {
					if (!subExpCells.containsKey(semantics)) {
						subExpCells.put(semantics, new LinkedList<>());
					} else {
						subExpCells.get(semantics).clear();
					}
					subExpCells.get(semantics).add(cell);
					subExpCellViterbiScores.put(semantics,
							cell.getViterbiScore());
				} else if (subExpCellViterbiScores.containsKey(semantics)
						&& subExpCellViterbiScores.get(semantics) == cell
								.getViterbiScore()) {
					subExpCells.get(semantics).add(cell);
				}
			}
		}

		// Sort all sub-expression cells according to their viterbi score and in
		// descending order pick the ones to use for the update. If the
		// considered cell is overlapping with an already selected cell, skip
		// it. This is a greedy process to select the best cells.

		// Descending order (reverse of the natural order).
		final List<Cell<LogicalExpression>> sorted = subExpCells.values()
				.stream().flatMap(s -> s.stream())
				.sorted(Collections.reverseOrder((l1, l2) -> Double
						.compare(l1.getViterbiScore(), l2.getViterbiScore())))
				.collect(Collectors.toList());
		final Map<Span, Double> spanScores = new HashMap<>();
		final Map<Span, Set<Cell<LogicalExpression>>> spanCells = new HashMap<>();
		final boolean[] usedSpans = new boolean[dataItem.getSample().getTokens()
				.size()];
		for (final Cell<LogicalExpression> cell : sorted) {
			boolean spanCovered = false;
			for (int i = cell.getStart(); i <= cell.getEnd(); ++i) {
				if (usedSpans[i]) {
					spanCovered = true;
					break;
				}
			}
			final Span span = Span.of(cell.getStart(), cell.getEnd());
			if (spanCovered) {
				// Add only if this span exactly exists and the score is equal
				// to the existing.
				if (spanScores.containsKey(span)
						&& spanScores.get(span) == cell.getViterbiScore()) {
					spanCells.get(span).add(cell);
				}

				continue;
			}
			// Select the cell. Mark the span as used.
			spanScores.put(span, cell.getViterbiScore());
			final Set<Cell<LogicalExpression>> cellSet = new HashSet<>();
			cellSet.add(cell);
			spanCells.put(span, cellSet);
			for (int i = cell.getStart(); i <= cell.getEnd(); ++i) {
				usedSpans[i] = true;
			}
		}

		if (!(modelOutput.getBaseParserOutput() instanceof CKYParserOutput)) {
			LOG.error("Unexpected model inference base output: %s",
					modelOutput.getBaseParserOutput().getClass());
			return attemptSecondStageGradient(dataItem, parser, dataItemModel,
					statUpdates, dataItemNumber, epochNumber);
		}
		final CKYParserOutput<LogicalExpression> modelBaseOutput = (CKYParserOutput<LogicalExpression>) modelOutput
				.getBaseParserOutput();

		// Log found cells.
		final List<Cell<LogicalExpression>> allCells = spanCells.values()
				.stream().flatMap(s -> s.stream()).collect(Collectors.toList());
		LOG.info(
				"From %d sub-expressions, identified %d and selected %d cells:",
				subExpressions.size(), subExpCells.size(), allCells.size());
		for (final Cell<LogicalExpression> cell : allCells) {
			LOG.info("[%d-%d] %s :- %s", cell.getStart(),
					cell.getEnd(), dataItem.getSample().getTokens()
							.sub(cell.getStart(), cell.getEnd() + 1),
					cell.getCategory());
			for (final IWeightedParseStep<LogicalExpression> step : cell
					.getMaxSteps()) {
				LOG.info("\t%s",
						step.toString(false, false, dataItemModel.getTheta()));
			}
			LOG.info("---------------------------------------------");
		}

		final IHashVector gradient = HashVectorFactory.create();
		for (final Entry<Span, Set<Cell<LogicalExpression>>> entry : spanCells
				.entrySet()) {
			final IHashVectorImmutable spanGradient = computeSpanGradient(
					entry.getKey(), entry.getValue(), baseConditionedOutput,
					modelBaseOutput, dataItemModel, dataItem);
			if (spanGradient != null) {
				// Add the span gradient to the gradient.
				spanGradient.addTimesInto(1.0, gradient);
			}
		}

		statUpdates.appendSampleStat(dataItemNumber, epochNumber,
				LearningServices.TRIGGERED_UNREACHABLE_UPDATE);

		// Try to compute and add the gradient from the second stage.
		final IHashVectorImmutable secondStage = computeSecondStageGradient(
				dataItem, parser, dataItemModel, statUpdates, dataItemNumber,
				epochNumber);
		if (secondStage != null) {
			secondStage.addTimesInto(1.0, gradient);
		}

		LOG.info("Gradient (partial, unreachable): %s", gradient);

		// When generating gradient for an example that is not reachable, not
		// lexical entries are returned in the viterbi set. This is a
		// conservative choice to avoid pulloting the set of entries that are
		// kept during learning (i.e., not pruned).
		return new GradientComputation(gradient.size() == 0 ? null : gradient,
				true, false, statUpdates, Collections.emptySet(), false);
	}

	public static class Creator
			implements IResourceObjectCreator<SimpleGradient> {

		private final String type;

		public Creator() {
			this("gradient.simple");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SimpleGradient create(Parameters params,
				IResourceRepository repo) {
			Integer conditionedBeam;
			if (params.contains("conditionedBeam")) {
				conditionedBeam = params.getAsInteger("conditionedBeam");
			} else {
				conditionedBeam = null;
			}

			return new SimpleGradient(params.getAsBoolean("hard", false),
					conditionedBeam, params.getAsBoolean("secondStage", true),
					params.getAsBoolean("partialUpdates", true),
					params.getAsBoolean("unreachableUpdates", true));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, SimpleGradient.class)
					.setDescription(
							"Computes an approximate gradient for a single data item with the objective of maximizing the joint probability p(a | x) = \\sum_z p(a | z, x) \\sum_y p(z, y | x)")
					.addParam("secondStage", Boolean.class,
							"Attempt second-stage updates (default:true)")
					.addParam("partialUpdates", Boolean.class,
							"Attempt partial updates (when the full correct derivation is not reachable, but the underspecified one is) (default: true)")
					.addParam("unreachableUpdates", Boolean.class,
							"Attempt unreachable updates (when the underspecified LF is not reachable) (default: true)")
					.addParam("conditionedBeam", Integer.class,
							"Beam size for conditioned inference (default: null, use default beam)")
					.addParam("hard", Boolean.class,
							"Hard updates (similar to hard EM) (default: false)")
					.build();
		}

	}

}
