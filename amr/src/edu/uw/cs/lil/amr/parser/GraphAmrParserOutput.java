package edu.uw.cs.lil.amr.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorUtils;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphParserOutput;
import edu.cornell.cs.nlp.spf.parser.joint.InferencePair;
import edu.cornell.cs.nlp.spf.parser.joint.graph.IJointGraphOutput;
import edu.cornell.cs.nlp.utils.collections.IScorer;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.math.LogSumExp;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation.InferenceTriplet;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetFactors;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetMapping;

public class GraphAmrParserOutput extends
		AbstractAmrParserOutput<GraphAmrDerivation, IGraphDerivation<LogicalExpression>>
		implements IJointGraphOutput<LogicalExpression, LogicalExpression> {

	public static final ILogger							LOG	= LoggerFactory
			.create(GraphAmrParserOutput.class);
	private final IGraphParserOutput<LogicalExpression>	baseOutput;

	public GraphAmrParserOutput(List<GraphAmrDerivation> jointDerivations,
			IGraphParserOutput<LogicalExpression> baseOutput,
			long inferenceTime,
			List<Pair<IGraphDerivation<LogicalExpression>, FactorGraph>> derviationPairs,
			boolean outputExact, IHashVectorImmutable theta,
			InferenceMethod inferenceMethod) {
		super(derviationPairs, inferenceMethod, inferenceTime, jointDerivations,
				outputExact, theta);
		this.baseOutput = baseOutput;

		assert jointDerivations.stream().map(d -> d.getResult())
				.collect(Collectors.toSet()).size() == jointDerivations
						.size() : "Each derivation must have a unique result";
		assert verifyNorm() : "Invalid probability distribution";
	}

	@Override
	public IGraphParserOutput<LogicalExpression> getBaseParserOutput() {
		return baseOutput;
	}

	@Override
	public List<GraphAmrDerivation> getDerivations(LogicalExpression result) {

		final List<GraphAmrDerivation> existingDerivations = jointDerivations
				.stream()
				.filter(derivation -> derivation.getResult().equals(result))
				.collect(Collectors.toList());

		if (!existingDerivations.isEmpty()
				|| inferenceMethod == InferenceMethod.NONE) {
			// If we have derivations with the given result, or if we are not
			// using a factor graph, just iterate over the inference pairs to
			// get the matching ones.
			return existingDerivations;
		}

		final List<InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>>> inferencePairs = getInferencePairs(
				result);

		if (inferencePairs.isEmpty()) {
			return Collections.emptyList();
		}

		final GraphAmrDerivation.Builder builder = new GraphAmrDerivation.Builder(
				result);
		for (final InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> pair : inferencePairs) {

			// For each inference pair, we need to recover the log norm of the
			// second stage. We can find it from existing derivation pairs. To
			// do so, we search for at least one inference pair that came from
			// the relevant base derivations.
			final IGraphDerivation<LogicalExpression> baseDerivation = pair
					.getBaseDerivation();
			Double evalLogNorm = null;
			for (final GraphAmrDerivation derivation : jointDerivations) {
				for (final InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> inferencePair : derivation
						.getInferencePairs()) {
					if (inferencePair.getBaseDerivation() == baseDerivation
							&& inferencePair instanceof GraphAmrDerivation.InferenceTriplet) {
						evalLogNorm = ((GraphAmrDerivation.InferenceTriplet) inferencePair)
								.getEvalLogNorm();
						break;
					}
				}
				if (evalLogNorm != null) {
					break;
				}
			}
			// If we didn't find a single pair using this base derivation, it
			// means the evaluation of it completely failed. Therefore, we
			// consider that it has a single derivation, the one that we just
			// created.
			if (evalLogNorm == null) {
				evalLogNorm = pair.getEvaluationResult().getScore();
			}

			builder.addInferencePair(pair.getBaseDerivation(),
					pair.getEvaluationResult(), evalLogNorm);
		}

		if (builder.numPairs() == 0) {
			return Collections.emptyList();
		} else {
			// It's clear we created inference pairs on the fly. The mass of
			// these pairs in not included in the normalization term of the
			// output. Therefore, their probability, when normalized, might be
			// larger than 1.
			final GraphAmrDerivation derivation = builder.build();
			LOG.warn(
					"Created a derivation on the fly -- its probability mass (log inside score = %.2f) is not included in the norm (log norm = %.2f)",
					derivation.getLogInsideScore(), logNorm());
			return ListUtils.createSingletonList(derivation);
		}
	}

	@Override
	public IHashVector logExpectedFeatures() {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				return logExpectedFeaturesBeam(lf -> true);
			case LBP:
				return logExpectedFeaturesExpectation();
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	@Override
	public IHashVector logExpectedFeatures(IFilter<LogicalExpression> filter) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				return logExpectedFeaturesBeam(filter::test);
			case LBP:
				throw new IllegalStateException(
						"expected features filtering is not possible with AMR joint parses since we can't enumerate parses and the filter doesn't decompose");
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	/**
	 * Log expected features conditioned on a specific result logical form.
	 */
	public IHashVector logExpectedFeatures(LogicalExpression result) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				LOG.warn(
						"logExpectedFeatures(LogicalExpression) is not optimally implemented for a beam search");
				return logExpectedFeaturesBeam(d -> d.equals(result));
			case LBP:
				return logExpectedFeaturesExpectation(result);
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	@Override
	public double logNorm() {
		// This computation doesn't vary according to the inference method.
		return computeLogNorm(lf -> true);
	}

	@Override
	public double logNorm(IFilter<LogicalExpression> filter) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				return computeLogNorm(filter::test);
			case LBP:
				throw new IllegalStateException(
						"norm filtering is not possible with an AMR joint derivation that is based on comptuing expectations");
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	/**
	 * Log normalization constant conditioned on getting a specific result
	 * logical form.
	 */
	public double logNorm(LogicalExpression result) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				LOG.warn(
						"logExpectedFeatures(LogicalExpression) is not optimally implemented for a beam search");
				return computeLogNorm(lf -> lf.equals(result));
			case LBP:
				return logNormExpectation(result);
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	private double computeLogNorm(Predicate<LogicalExpression> filter) {
		// The normalization constant in the second stage depends on the final
		// logical form. The expected features already consider this. The log
		// norm is, therefore, only the norm form base parser output. However,
		// the norm must only include base derivations that participate in a
		// complete derivation that pass the filter.

		// Collect all the categories at the root of base derivations that
		// participate in complete derivations that pass the filter.
		final Set<Category<LogicalExpression>> participatingCategories = jointDerivations
				.stream().filter(deriv -> filter.test(deriv.getResult()))
				.map(deriv -> deriv.getInferencePairs())
				.flatMap(pairs -> pairs.stream())
				.map(pair -> pair.getBaseDerivation().getCategory())
				.collect(Collectors.toSet());

		return baseOutput.logNorm(participatingCategories::contains);
	}

	private IHashVector logExpectedFeaturesBeam(
			Predicate<LogicalExpression> filter) {
		// Init derivations outside scores. In practice, prune the joint
		// derivation using the filter and implicitly give each an outside score
		// of log(1.0).
		final List<GraphAmrDerivation> derivationsToUse = jointDerivations
				.stream().filter(deriv -> filter.test(deriv.getResult()))
				.collect(Collectors.toList());

		// To propagate the outside scores into the graph of the base
		// output, we create a scorer that uses the outside scores of the joint
		// derivations.

		// Create a mapping for the scorer. For each root category in the chart,
		// which leads to a derivation in derivationsToUse, the map gives the
		// total outside contribution. The outside contribution from each
		// derivation is the second stage probability, which is normalized.
		final Map<Category<LogicalExpression>, Double> initBaseParseOutsideScores = new HashMap<>();
		for (final GraphAmrDerivation derivation : derivationsToUse) {
			for (final InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> pair : derivation
					.getInferencePairs()) {

				// If this derivation included closure, we need to look further
				// into the base derivation before closure to create the scorer.
				// We need to get the categories at the root of the chart.
				final Category<LogicalExpression> category = pair
						.getBaseDerivation().getCategory();

				// The log outside contribution is the current outside score of
				// the complete derivation (implicitly log(1.0) = 0.0)
				// plus (product in log space) the log probability of the
				// evaluation.

				assert pair instanceof InferenceTriplet : "Unexpected inference pair type -- must be a triplet with a log probability";
				final double logOutsideContribution = ((InferenceTriplet) pair)
						.logProbability();

				if (initBaseParseOutsideScores.containsKey(category)) {
					initBaseParseOutsideScores.put(category,
							LogSumExp.of(
									initBaseParseOutsideScores.get(category),
									logOutsideContribution));
				} else {
					initBaseParseOutsideScores.put(category,
							logOutsideContribution);
				}
			}
		}

		// Create the scorer.
		final IScorer<Category<LogicalExpression>> scorer = e -> initBaseParseOutsideScores
				.containsKey(e) ? initBaseParseOutsideScores.get(e)
						: Double.NEGATIVE_INFINITY;

		// Get expected features from base parser output.
		final IHashVector logExpectedFeatures = baseOutput
				.logExpectedFeatures(scorer);

		// Add expected features from the execution result cells.
		for (final GraphAmrDerivation derivation : derivationsToUse) {
			for (final InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> pair : derivation
					.getInferencePairs()) {
				// Explicitly adding 0.0 here to account for the outside
				// score of the evaluation, which is implicitly log(1.0) = 0.0
				// (see above).
				assert pair instanceof InferenceTriplet : "Unexpected inference pair type -- must be a triplet with a log probability";
				final double logWeight = ((InferenceTriplet) pair)
						.logProbability()
						+ pair.getBaseDerivation().getLogInsideScore() + 0.0;
				HashVectorUtils.logSumExpAdd(logWeight,
						pair.getEvaluationResult().getFeatures(),
						logExpectedFeatures);
			}
		}

		return logExpectedFeatures;
	}

	private IHashVector logExpectedFeaturesExpectation() {
		// Collect the expected features from the factors.
		final IHashVector logExpectedFeatures = HashVectorFactory.create();

		final Set<Category<LogicalExpression>> categoriesIncluded = new HashSet<>();
		for (final Pair<IGraphDerivation<LogicalExpression>, FactorGraph> pair : derviationPairs) {
			categoriesIncluded.add(pair.first().getCategory());
			final double logInsideScore = pair.first().getLogInsideScore();
			final FactorGraph graph = pair.second();
			for (final IFactor factor : GetFactors.of(graph)) {
				// Accumulate the sum to do a sanity check.
				double probSum = 0.0;
				for (final Pair<Double, IHashVectorImmutable> valueVector : factor
						.getBelief()) {
					probSum += Math.exp(valueVector.first());
					// The weight is a combination of the inside score of the
					// base parse and the belief of this factor.
					HashVectorUtils.logSumExpAdd(
							valueVector.first() + logInsideScore,
							valueVector.second(), logExpectedFeatures);
				}
				assert Math.abs(
						probSum - 1.0) < 0.00001 : "Belief sum is not ~1.0";
			}
		}

		// Add the expected features from the base output. The expected features
		// of the chart are added without re-weighting. Since the factor graph
		// is normalized and we take all outputs, each base derivation should be
		// weighted by the sum weight of all the factor graph final results,
		// which is 1.0, as it's normalized. In the log space, this means that
		// we are implicitly adding 0.0 to each of the weights.
		HashVectorUtils.sumExpLogged(
				baseOutput.logExpectedFeatures(categoriesIncluded::contains),
				logExpectedFeatures);

		return logExpectedFeatures;
	}

	private IHashVector logExpectedFeaturesExpectation(
			LogicalExpression result) {
		final IHashVector logExpectedFeatures = HashVectorFactory.create();

		// Locate the appropriate factor graph. Get the mapping that leads to
		// result and iterate the factors to extract the weighted features.
		final Category<LogicalExpression> baseCategory = Category.create(
				AMRServices.getCompleteSentenceSyntax(),
				AMRServices.underspecifyAndStrip(result));

		for (final Pair<IGraphDerivation<LogicalExpression>, FactorGraph> pair : derviationPairs) {
			if (StripOverload.of(pair.first().getCategory())
					.equals(baseCategory)) {
				final Map<INode, LogicalExpression> mapping = GetMapping
						.of(pair.second(), result);
				if (mapping != null) {
					final double logInsideScore = pair.first()
							.getLogInsideScore();
					final FactorGraph graph = pair.second();
					for (final IFactor factor : GetFactors.of(graph)) {
						final FactorTable belief = factor.getBelief();

						// A quick sanity check.
						if (!belief.isMappingComplete(mapping)) {
							throw new IllegalStateException(
									"Incomplete mapping -- should never happen");
						}

						// The weight is a combination of the inside score of
						// the base parse and the belief of this factor, give
						// the mapping (which does the conditioning).
						HashVectorUtils.logSumExpAdd(
								belief.get(mapping) + logInsideScore,
								belief.getFeatures(mapping),
								logExpectedFeatures);
					}
				}
			}
		}

		// TODO FIX: this is incorrect the factor graph should re-weight the
		// expected features.

		// Add the expected features from the base output. This will consider
		// only the relevant base parses.
		HashVectorUtils.sumExpLogged(
				baseOutput.logExpectedFeatures(
						(IFilter<Category<LogicalExpression>>) e -> baseCategory
								.equals(StripOverload.of(e))),
				logExpectedFeatures);

		// See above TODO. Uncomment the final return statement once fixed.
		throw new RuntimeException(
				"This method is not computing the correct expected feature weigths, see TODO in code");

		// return logExpectedFeatures;
	}

	private double logNormExpectation(LogicalExpression result) {
		// TODO probably no need to make the distinction between beam search and
		// cases when we have expectations. This implementation is currently
		// broken.

		final List<GraphAmrDerivation> derivations = getDerivations(result);
		// There should be only one derivation giving the result. While multiple
		// base parses and factor graphs result in the same final result, they
		// are all packed into a single AMRDerivation. This is a sanity check.
		if (derivations.size() > 1) {
			throw new IllegalStateException(
					"Assumption broken -- see code for details");
		}
		// Only need to log-sum-exp the log inside scores of the base
		// derivation. This is based on the assumption of a single factor graph
		// derivation.
		return LogSumExp.of(derivations.stream()
				.map((derivation) -> derivation.getLogInsideScore())
				.collect(Collectors.toList()));
	}

	private boolean verifyNorm() {
		final double sum = LogSumExp.of(jointDerivations.stream()
				.map(GraphAmrDerivation::getLogInsideScore)
				.collect(Collectors.toList()));
		final double norm = logNorm();
		if (Math.abs(sum - norm) > 0.01) {
			LOG.error(
					"Probability violation [%d derivations]: sum=%.4f, norm=%.4f, diff=%.4f",
					jointDerivations.size(), sum, norm, sum - norm);
			return false;
		} else {
			return true;
		}
	}

}
