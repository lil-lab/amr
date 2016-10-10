package edu.uw.cs.lil.amr.lambda;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;

/**
 * Verifies that the logical expression is underspecified and stripped. Meaning,
 * all constants that can be underspecified are and no {@link SkolemId}s are
 * present.
 *
 * @author Yoav Artzi
 */
public class IsUnderspecifiedAndStripped implements ILogicalExpressionVisitor {

	private boolean result = true;

	private IsUnderspecifiedAndStripped() {
		// Used through static 'of' method.
	}

	public static boolean of(LogicalExpression exp) {
		final IsUnderspecifiedAndStripped visitor = new IsUnderspecifiedAndStripped();
		visitor.visit(exp);
		return visitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getArgument().accept(this);
		if (!result) {
			return;
		}

		lambda.getBody().accept(this);
	}

	@Override
	public void visit(Literal literal) {
		literal.getPredicate().accept(this);
		if (!result) {
			return;
		}

		final int numArgs = literal.numArgs();
		for (int i = 0; i < numArgs; ++i) {
			literal.getArg(i).accept(this);
			if (!result) {
				return;
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		result = AMRServices.isUnderspecified(logicalConstant)
				|| AMRServices.underspecify(logicalConstant) == logicalConstant;
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		result = !(variable instanceof SkolemId);
	}

}
