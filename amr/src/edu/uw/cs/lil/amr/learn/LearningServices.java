package edu.uw.cs.lil.amr.learn;

import java.util.List;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IDataItemModel;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.InferencePair;
import edu.cornell.cs.nlp.spf.parser.joint.graph.IJointGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.learn.gradient.StatUpdates;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation;
import edu.uw.cs.lil.amr.parser.GraphAmrParserOutput;

public class LearningServices {

	public static final String	BASE_PARSE_PRUNED				= "P";

	public static final String	CORRECT_REACHABLE				= "R";
	public static final String	GOLD_OPTIMAL					= "G";
	public static final String	GOLD_UNDERSPEC_OPTIMAL			= "B";
	public static final ILogger	LOG								= LoggerFactory
			.create(LearningServices.class);
	public static final String	SECOND_STAGE_CORRECT			= "S";
	public static final String	SECOND_STAGE_GRADIENT_GENERATED	= "Q";
	public static final String	TRIGGERED_EARLY_UPDATE			= "E";
	public static final String	TRIGGERED_UNREACHABLE_UPDATE	= "X";

	public static final String	TRIGGERED_UPDATE				= "U";

	private LearningServices() {
		// Service class. No constructor.
	}

	public static void logBaseParse(Category<LogicalExpression> gold,
			IGraphDerivation<LogicalExpression> derivation, boolean verbose,
			IDataItemModel<LogicalExpression> dataItemModel) {
		if (gold.equals(StripOverload.of(derivation.getCategory()))) {
			LOG.info("[v=%.2f, i=%.2f] (correct derivation)",
					derivation.getScore(), derivation.getLogInsideScore());
		} else {
			LOG.info("[v=%.2f, i=%.2f] %s", derivation.getScore(), derivation,
					derivation.getLogInsideScore());
		}
		if (verbose) {
			for (final IWeightedParseStep<LogicalExpression> step : derivation
					.getMaxSteps()) {
				LOG.info("\t%s",
						step.toString(false, false, dataItemModel.getTheta()));
			}
		}
	}

	public static void logConditionedDerivations(StatUpdates statUpdates,
			GraphAmrParserOutput conditionedOutput, LabeledAmrSentence dataItem,
			int epochNumber, int dataItemNumber,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			List<GraphAmrDerivation> conditionedDerivations) {
		if (conditionedDerivations.isEmpty()) {
			LOG.info("No conditioned derivations");
			statUpdates.count(
					"Correct not reachable (under conditioned inference)",
					epochNumber);
		} else {
			LOG.info("Conditioned output is %s",
					conditionedOutput.isExact() ? "exact" : "approximate");
			statUpdates.appendSampleStat(dataItemNumber, epochNumber,
					LearningServices.CORRECT_REACHABLE);
			LOG.info("Created %d conditioned derivations for training sample:",
					conditionedDerivations.size());
			for (final GraphAmrDerivation derivation : conditionedDerivations) {
				LearningServices.logParse(dataItem, derivation, true,
						dataItemModel, conditionedOutput.logNorm());
			}
		}
	}

	public static void logModelDerivations(GraphAmrParserOutput modelOutput,
			StatUpdates statUpdates, int dataItemNumber, int epochNumber,
			LabeledAmrSentence dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			List<GraphAmrDerivation> conditionedDerivations,
			Category<LogicalExpression> underspecifiedCategory) {
		// Logging.
		LOG.info("Created %d model derivations for training sample",
				modelOutput.getDerivations().size());
		LOG.info("Output is %s",
				modelOutput.isExact() ? "exact" : "approximate");

		// Record if the best is the gold standard, if such debug
		// information is available.
		final List<? extends IJointGraphDerivation<LogicalExpression, LogicalExpression>> maxDerivations = modelOutput
				.getMaxDerivations();
		if (maxDerivations.size() == 1 && dataItem.getLabel()
				.equals(maxDerivations.get(0).getResult())) {
			LOG.info("Correct label is the max scoring derivation");
			statUpdates.appendSampleStat(dataItemNumber, epochNumber,
					LearningServices.GOLD_OPTIMAL);
		} else {
			if (maxDerivations.size() == 1) {
				if (underspecifiedCategory.getSemantics()
						.equals(StripOverload
								.of(AMRServices.underspecifyAndStrip(
										maxDerivations.get(0).getResult())))) {
					LOG.info(
							"Underspecified label is the max-scoring underspecified derivation (inference failed in the factor graph)");
					statUpdates.appendSampleStat(dataItemNumber, epochNumber,
							LearningServices.GOLD_UNDERSPEC_OPTIMAL);
				}
			}

			final double modelOutputLogNorm = modelOutput.logNorm();
			if (maxDerivations.size() < 5) {
				LOG.info("There are %d max-scoring derivations:",
						maxDerivations.size());
				for (final IJointGraphDerivation<LogicalExpression, LogicalExpression> derivation : maxDerivations) {
					LearningServices.logParse(dataItem, derivation, true,
							dataItemModel, modelOutputLogNorm);

					// Log if the base stripped and underspecified logical form
					// is the same as the correct one. Essentially, if we got
					// the structure right.
					if (underspecifiedCategory.getSemantics()
							.equals(StripOverload
									.of(AMRServices.underspecifyAndStrip(
											derivation.getResult())))) {
						LOG.info("Structure is correct!");
					}

					if (!conditionedDerivations.isEmpty()) {
						// Log the difference in the features with the correct
						// derivation.
						LOG.info(
								"Feature delta with correct conditioned derivations (in the order they are displayed above):");
						int i = 0;
						for (final GraphAmrDerivation correctDerivation : conditionedDerivations) {
							final IHashVector delta = HashVectorFactory
									.create();
							correctDerivation.getMeanMaxFeatures()
									.addTimesInto(1.0, delta);
							derivation.getMeanMaxFeatures().addTimesInto(-1.0,
									delta);
							delta.dropZeros();
							delta.dropNoise();
							LOG.info("Delta with correct derivation #%d: %s",
									i++, dataItemModel.getTheta()
											.printValues(delta));
						}
					}

				}
			} else {
				LOG.info(
						"Too many (%d > 5) max-scoring derivations, skipping logging",
						maxDerivations.size());
			}

			// Iterate over all derivations, try to find a derivation for which
			// its base parse gives the correct underspecified form and log it.
			for (final GraphAmrDerivation derivation : modelOutput
					.getDerivations()) {
				for (final InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> inferencePair : derivation
						.getInferencePairs()) {
					final IGraphDerivation<LogicalExpression> base = inferencePair
							.getBaseDerivation();
					if (StripOverload.of(base.getCategory())
							.equals(underspecifiedCategory)) {
						LOG.info(
								"Found a joint derivation with the correct base parse:");
						LearningServices.logParse(dataItem, derivation, true,
								dataItemModel, modelOutputLogNorm);
						// Only log the first derivation found.
						break;
					}
				}
			}

			LOG.info("There are %d max-scoring base derivations", modelOutput
					.getBaseParserOutput().getBestDerivations().size());

			boolean found = false;
			for (final IGraphDerivation<LogicalExpression> derivation : modelOutput
					.getBaseParserOutput().getBestDerivations()) {
				if (StripOverload.of(derivation.getCategory())
						.equals(underspecifiedCategory)) {
					found = true;
				}
			}
			if (found) {
				LOG.info(
						"Underspecified logical form exists in base derivation and is max scoring (there are %d max scoring base derivations)",
						modelOutput.getBaseParserOutput().getBestDerivations()
								.size());
				statUpdates.count(
						"Correct base parse has max-score (but full derivation does not)",
						epochNumber);
			} else {
				final List<? extends IGraphDerivation<LogicalExpression>> correctBaseDerivations = modelOutput
						.getBaseParserOutput().getDerivations(c -> StripOverload
								.of(c).equals(underspecifiedCategory));
				if (!correctBaseDerivations.isEmpty()) {
					LOG.info(
							"Underspecified logical form exists in base derivation (there are %d base derivations)",
							modelOutput.getBaseParserOutput()
									.getAllDerivations().size());
					statUpdates.count(
							"Base parse is not max-scoring (but in the chart)",
							epochNumber);

					LOG.info("Found %d correct base derivations:",
							correctBaseDerivations.size());
					for (final IGraphDerivation<LogicalExpression> derivation : correctBaseDerivations) {
						LearningServices.logBaseParse(underspecifiedCategory,
								derivation, true, dataItemModel);
					}
				} else {
					LOG.info(
							"Correct derivation lost to pruning in base parse");
					statUpdates.appendSampleStat(dataItemNumber, epochNumber,
							LearningServices.BASE_PARSE_PRUNED);
				}
			}
		}

	}

	public static void logParse(LabeledAmrSentence dataItem,
			IJointGraphDerivation<LogicalExpression, LogicalExpression> derivation,
			boolean verbose, IDataItemModel<LogicalExpression> dataItemModel,
			double logNorm) {
		final boolean isGold = dataItem.getLabel()
				.equals(derivation.getResult());
		if (isGold) {
			LOG.info("[prob=%.2f] (correct derivation)",
					Math.exp(derivation.getLogInsideScore() - logNorm));
		} else {
			LOG.info("[v prob=%.2f] %s",
					Math.exp(derivation.getLogInsideScore() - logNorm),
					derivation);
		}
		if (verbose) {
			for (final IWeightedParseStep<LogicalExpression> step : derivation
					.getMaxSteps()) {
				LOG.info("\t%s",
						step.toString(false, false, dataItemModel.getTheta()));
			}
			LOG.info("Features: %s", dataItemModel.getTheta()
					.printValues(derivation.getMeanMaxFeatures()));
		}

	}

}
