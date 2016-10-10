package edu.uw.cs.lil.amr.parser;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.IDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.InferencePair;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetFactors;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetMapping;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.MarginalsProduct;

public abstract class AbstractAmrParserOutput<DERIV extends IJointDerivation<LogicalExpression, LogicalExpression>, BASEDERIV extends IDerivation<LogicalExpression>>
		implements IJointOutput<LogicalExpression, LogicalExpression> {

	public static final ILogger							LOG	= LoggerFactory
			.create(AbstractAmrParserOutput.class);

	protected final List<Pair<BASEDERIV, FactorGraph>>	derviationPairs;
	protected final InferenceMethod						inferenceMethod;
	protected final long								inferenceTime;
	protected final List<DERIV>							jointDerivations;
	protected final List<DERIV>							maxDerivations;
	protected final boolean								outputExact;

	protected final IHashVectorImmutable				theta;

	public AbstractAmrParserOutput(
			List<Pair<BASEDERIV, FactorGraph>> derviationPairs,
			InferenceMethod inferenceMethod, long inferenceTime,
			List<DERIV> jointDerivations, boolean outputExact,
			IHashVectorImmutable theta) {
		assert InferenceMethod.LBP != inferenceMethod
				|| theta != null : "If using LBP for inference, theta must be provided to compute viterbi score for new derivation pairs";
		this.derviationPairs = derviationPairs;
		this.inferenceMethod = inferenceMethod;
		this.inferenceTime = inferenceTime;
		this.jointDerivations = jointDerivations;
		this.outputExact = outputExact;
		this.theta = theta;

		// Collect max scoring derivations. The score considered here is the
		// viterbi score of the derivation, as defined in AMRDerivation.
		final List<DERIV> maxScoringDerivations = new LinkedList<>();
		double max = -Double.MAX_VALUE;
		for (final DERIV derivation : jointDerivations) {
			if (derivation.getScore() == max) {
				maxScoringDerivations.add(derivation);
			} else if (derivation.getScore() > max) {
				maxScoringDerivations.clear();
				maxScoringDerivations.add(derivation);
				max = derivation.getScore();
			}
		}
		this.maxDerivations = Collections
				.unmodifiableList(maxScoringDerivations);

	}

	@Override
	public List<DERIV> getDerivations() {
		return jointDerivations;
	}

	@Override
	public List<DERIV> getDerivations(boolean includeFails) {
		return getDerivations();
	}

	@Override
	public List<DERIV> getDerivations(IFilter<LogicalExpression> filter) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				return jointDerivations.stream()
						.filter(d -> filter.test(d.getResult()))
						.collect(Collectors.toList());
			case LBP:
				throw new IllegalStateException(
						"Derivation filtering is not possible with AMR LBP inference since we can't enumerate parses and the filter doesn't decompose");
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	@Override
	public abstract List<DERIV> getDerivations(LogicalExpression result);

	@Override
	public long getInferenceTime() {
		return inferenceTime;
	}

	@Override
	public List<DERIV> getMaxDerivations() {
		return maxDerivations;
	}

	@Override
	public List<DERIV> getMaxDerivations(boolean includeFails) {
		return maxDerivations;
	}

	@Override
	public List<DERIV> getMaxDerivations(IFilter<LogicalExpression> filter) {
		switch (inferenceMethod) {
			case NONE:
			case BEAM:
				final List<DERIV> filtered = jointDerivations.stream()
						.filter(d -> filter.test(d.getResult()))
						.collect(Collectors.toList());
				final List<DERIV> maxScoring = new LinkedList<>();
				final double maxScore = -Double.MAX_VALUE;
				for (final DERIV derivation : filtered) {
					final double score = derivation.getViterbiScore();
					if (score == maxScore) {
						maxScoring.add(derivation);
					} else if (score > maxScore) {
						maxScoring.clear();
						maxScoring.add(derivation);
					}
				}
				return maxScoring;
			case LBP:
				throw new IllegalStateException(
						"Max-scoring derivation filtering is not possible with AMR LBP inference since we can't enumerate parses and the filter doesn't decompose");
			default:
				throw new IllegalStateException(
						"Unknown inference method: " + inferenceMethod);
		}
	}

	@Override
	public List<DERIV> getMaxDerivations(LogicalExpression result) {
		// Assumes that every result has at most one derivation leading to it,
		// so it's the max scoring one.
		final List<DERIV> derivations = getDerivations(result);
		assert derivations.size() <= 1;
		return derivations;
	}

	@Override
	public boolean isExact() {
		return outputExact;
	}

	protected List<InferencePair<LogicalExpression, LogicalExpression, BASEDERIV>> getInferencePairs(
			LogicalExpression result) {
		// Get the base parse that gives the underspecified ID-less result and
		// the factor graph created from it.
		final Category<LogicalExpression> baseCategory = Category.create(
				AMRServices.getCompleteSentenceSyntax(),
				AMRServices.underspecifyAndStrip(result));
		final List<InferencePair<LogicalExpression, LogicalExpression, BASEDERIV>> pairs = new LinkedList<>();
		for (final Pair<BASEDERIV, FactorGraph> derivationPair : derviationPairs) {
			if (StripOverload.of(derivationPair.first().getCategory())
					.equals(baseCategory)) {
				final FactorGraph graph = derivationPair.second();

				// Get the mapping from the factor graph that will give the
				// result, if such exists.
				final Map<INode, LogicalExpression> mapping = GetMapping
						.of(graph, result);

				if (mapping != null) {

					// Collect the features using the mapping. Aggregate the
					// score as well. If the graph has no marginals, the
					// aggregated belief will reflect the viterbi score.
					// Otherwise, it is just a meaningless sum of beliefs and we
					// need theta to compute this.
					final IHashVector features = HashVectorFactory.create();
					double beliefSum = 0.0;
					for (final IFactor factor : GetFactors.of(graph)) {
						final FactorTable table = factor.getTable();
						table.getFeatures(mapping).addTimesInto(1.0, features);
						beliefSum += table.get(mapping);
					}

					// Create the evaluation object and the joint derivation.
					final EvaluationResult evaluation = graph.hasMarginals()
							? new ProbEvaluationResult(
									theta.dotProduct(features),
									MarginalsProduct.of(graph, mapping),
									features, result)
							: new EvaluationResult(beliefSum, features, result);

					pairs.add(new InferencePair<>(derivationPair.first(),
							evaluation));
				}

			}
		}

		return pairs;
	}

}
