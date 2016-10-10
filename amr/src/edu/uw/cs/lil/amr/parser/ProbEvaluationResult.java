package edu.uw.cs.lil.amr.parser;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

/**
 * Second step "evaluation" result with probability.
 *
 * @author Yoav Artzi
 */
public class ProbEvaluationResult extends EvaluationResult {

	private static final long	serialVersionUID	= 3321186333616467790L;
	/**
	 * Normalized log-probability of this evaluation (in practice, approximated
	 * by a product of marginals).
	 */
	private final double		logProbability;

	public ProbEvaluationResult(double score, double logProbability,
			IHashVectorImmutable features, LogicalExpression result) {
		super(score, features, result);
		this.logProbability = logProbability;
	}

	public double getLogProbability() {
		return logProbability;
	}

}
