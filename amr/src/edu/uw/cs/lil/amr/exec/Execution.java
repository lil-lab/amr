package edu.uw.cs.lil.amr.exec;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.exec.IExecution;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.joint.IJointDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;

/**
 * {@link IExecution} wrapper for a complete AMR derivation.
 *
 * @author Yoav Artzi
 */
public class Execution implements IExecution<LogicalExpression> {

	private final IJointDerivation<LogicalExpression, LogicalExpression>	derivation;
	private final IJointDataItemModel<LogicalExpression, LogicalExpression>	model;

	public Execution(
			IJointDerivation<LogicalExpression, LogicalExpression> derivation,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		this.derivation = derivation;
		this.model = model;
	}

	@Override
	public IHashVectorImmutable getFeatures() {
		return derivation.getMeanMaxFeatures();
	}

	@Override
	public LogicalExpression getResult() {
		return derivation.getResult();
	}

	@Override
	public double score() {
		return derivation.getScore();
	}

	@Override
	public String toString() {
		return toString(false);
	}

	@Override
	public String toString(boolean verbose) {
		final StringBuilder sb = new StringBuilder(derivation.toString());

		if (verbose) {
			sb.append("\n");
			for (final IWeightedParseStep<LogicalExpression> step : derivation
					.getMaxSteps()) {
				sb.append(step.toString(false, false, model.getTheta()));
				sb.append("\n");
			}
			sb.append(model.getTheta()
					.printValues(derivation.getMeanMaxFeatures()));
		}

		return sb.toString();
	}
}
