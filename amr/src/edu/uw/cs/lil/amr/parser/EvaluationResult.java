package edu.uw.cs.lil.amr.parser;

import java.io.Serializable;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IEvaluation;

/**
 * Second-step evaluation result.
 * 
 * @author Yoav Artzi
 */
public class EvaluationResult implements IEvaluation<LogicalExpression>,
		Serializable {

	private static final long			serialVersionUID	= 1373322401412033267L;

	private final IHashVectorImmutable	features;

	private final LogicalExpression		result;

	/**
	 * Linear score (not exponentiated).
	 */
	private final double				score;

	public EvaluationResult(double score, IHashVectorImmutable features,
			LogicalExpression result) {
		this.score = score;
		this.features = features;
		this.result = result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final EvaluationResult other = (EvaluationResult) obj;
		if (features == null) {
			if (other.features != null) {
				return false;
			}
		} else if (!features.equals(other.features)) {
			return false;
		}
		if (result == null) {
			if (other.result != null) {
				return false;
			}
		} else if (!result.equals(other.result)) {
			return false;
		}
		return true;
	}

	@Override
	public IHashVectorImmutable getFeatures() {
		return features;
	}

	@Override
	public LogicalExpression getResult() {
		return result;
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 1;
		hash = prime * hash + (features == null ? 0 : features.hashCode());
		hash = prime * hash
				+ (this.result == null ? 0 : this.result.hashCode());
		return hash;
	}

	@Override
	public String toString() {
		return result.toString();
	}

}
