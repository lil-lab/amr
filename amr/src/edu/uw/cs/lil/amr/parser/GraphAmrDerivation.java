package edu.uw.cs.lil.amr.parser;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IEvaluation;
import edu.cornell.cs.nlp.spf.parser.joint.InferencePair;
import edu.cornell.cs.nlp.spf.parser.joint.graph.JointGraphDerivation;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.math.LogSumExp;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class GraphAmrDerivation
		extends JointGraphDerivation<LogicalExpression, LogicalExpression> {

	public static final ILogger LOG = LoggerFactory
			.create(GraphAmrDerivation.class);

	public GraphAmrDerivation(List<InferenceTriplet> maxTriplets,
			List<InferenceTriplet> triplets, LogicalExpression result,
			double viterbiScore, double logInsideScore) {
		super(new ArrayList<>(maxTriplets), new ArrayList<>(triplets), result,
				viterbiScore, logInsideScore);
	}

	@Override
	public double getScore() {
		return getLogInsideScore();
	}

	@Override
	public String toString() {
		return String.format("[score=%.2f, v=%.2f, i=%.2f] %s", getScore(),
				getViterbiScore(), getLogInsideScore(),
				AMRServices.toString(getResult()));
	}

	public static class Builder {
		private final List<InferenceTriplet>	inferenceTriplets	= new LinkedList<>();

		private final LogicalExpression			result;

		public Builder(IGraphDerivation<LogicalExpression> baseDerivation,
				EvaluationResult evaluationResult, double evalLogNorm) {
			this(evaluationResult.getResult());
			addInferencePair(baseDerivation, evaluationResult, evalLogNorm);
		}

		public Builder(LogicalExpression result) {
			this.result = result;
		}

		public Builder addInferencePair(
				IGraphDerivation<LogicalExpression> baseDerivation,
				IEvaluation<LogicalExpression> evaluationResult,
				double evalLogNorm) {
			// Verify the new pair leads to the same result as the rest.
			if ((result != null || evaluationResult.getResult() != null)
					&& !result.equals(evaluationResult.getResult())) {
				LOG.error("Expected result: %s", result);
				LOG.error("Tried to add: %s", evaluationResult.getResult());
				throw new IllegalStateException(
						"JointDerivation can only account for a single final outcome.");
			}

			inferenceTriplets.add(new InferenceTriplet(baseDerivation,
					evaluationResult, evalLogNorm));
			return this;
		}

		public GraphAmrDerivation build() {
			double maxScore = -Double.MAX_VALUE;
			final List<InferenceTriplet> maxTriplets = new LinkedList<>();
			// The derivation probability marginalizes over all underspecified
			// logical form that result in the final logical form and all CCG
			// trees. The probability is normalized.
			final List<Double> logInsideScores = new ArrayList<>(
					inferenceTriplets.size());
			for (final InferenceTriplet triplet : inferenceTriplets) {
				// The score of a single derivation. This is a partially viterbi
				// score, since it takes the viterbi score for the base
				// derivation.
				final double viterbiScore = triplet.getBaseDerivation()
						.getScore() + triplet.getEvaluationResult().getScore();
				if (viterbiScore > maxScore) {
					maxScore = viterbiScore;
					maxTriplets.clear();
					maxTriplets.add(triplet);
				} else if (viterbiScore == maxScore) {
					maxTriplets.add(triplet);
				}

				// Update the log inside score with this triplet (including both
				// the evaluation and base derivation).
				logInsideScores.add(triplet.logProbability()
						+ triplet.getBaseDerivation().getLogInsideScore());
			}

			return new GraphAmrDerivation(maxTriplets, inferenceTriplets,
					result, maxScore, LogSumExp.of(logInsideScores));
		}

		/**
		 * @return
		 */
		public int numPairs() {
			return inferenceTriplets.size();
		}
	}

	public static class InferenceTriplet extends
			InferencePair<LogicalExpression, LogicalExpression, IGraphDerivation<LogicalExpression>> {

		/**
		 * Log norm that sums over all evaluation for the underspecified logical
		 * form at the root of the base derivation.
		 */
		private final double evalLogNorm;

		public InferenceTriplet(
				IGraphDerivation<LogicalExpression> baseDerivation,
				IEvaluation<LogicalExpression> evaluationResult,
				double evalLogNorm) {
			super(baseDerivation, evaluationResult);
			this.evalLogNorm = evalLogNorm;
		}

		public double getEvalLogNorm() {
			return evalLogNorm;
		}

		public double logProbability() {
			return getEvaluationResult().getScore() - evalLogNorm;
		}
	}
}
