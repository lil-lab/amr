package edu.uw.cs.lil.amr.parser;

import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.IDerivation;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.InferencePair;
import edu.cornell.cs.nlp.spf.parser.joint.JointDerivation;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class AmrDerivation
		extends JointDerivation<LogicalExpression, LogicalExpression> {

	public static final ILogger LOG = LoggerFactory.create(AmrDerivation.class);

	public AmrDerivation(
			List<InferencePair<LogicalExpression, LogicalExpression, IDerivation<LogicalExpression>>> maxPairs,
			List<InferencePair<LogicalExpression, LogicalExpression, IDerivation<LogicalExpression>>> pairs,
			LogicalExpression result, double viterbiScore) {
		super(maxPairs, pairs, result, viterbiScore);
	}

	@Override
	public String toString() {
		return AMRServices.toString(getResult());
	}

	public static class Builder {
		protected final List<InferencePair<LogicalExpression, LogicalExpression, IDerivation<LogicalExpression>>>	inferencePairs	= new LinkedList<>();
		protected final LogicalExpression																			result;

		public Builder(IGraphDerivation<LogicalExpression> baseDerivation,
				EvaluationResult evaluationResult) {
			this.result = evaluationResult.getResult();
			addInferencePair(baseDerivation, evaluationResult);
		}

		public Builder(LogicalExpression result) {
			this.result = result;
		}

		public Builder addInferencePair(
				IGraphDerivation<LogicalExpression> baseDerivation,
				EvaluationResult evaluationResult) {
			// Verify the new pair leads to the same result as the rest.
			if ((result != null || evaluationResult.getResult() != null)
					&& !result.equals(evaluationResult.getResult())) {
				LOG.error("Expected result: %s", result);
				LOG.error("Tried to add: %s", evaluationResult.getResult());
				throw new IllegalStateException(
						"JointDerivation can only account for a single final outcome.");
			}
			inferencePairs
					.add(new InferencePair<>(baseDerivation, evaluationResult));
			return this;
		}

		public AmrDerivation build() {
			double maxScore = -Double.MAX_VALUE;
			final List<InferencePair<LogicalExpression, LogicalExpression, IDerivation<LogicalExpression>>> maxPairs = new LinkedList<>();
			for (final InferencePair<LogicalExpression, LogicalExpression, IDerivation<LogicalExpression>> pair : inferencePairs) {
				// The score of a single derivation. This is a partially viterbi
				// score, since it takes the viterbi score for the base
				// derivation.
				final double score = pair.getBaseDerivation().getScore()
						+ pair.getEvaluationResult().getScore();
				if (score > maxScore) {
					maxScore = score;
					maxPairs.clear();
					maxPairs.add(pair);
				} else if (score == maxScore) {
					maxPairs.add(pair);
				}
			}

			return new AmrDerivation(maxPairs, inferencePairs, result,
					maxScore);
		}
	}

}
