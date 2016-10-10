package edu.uw.cs.lil.amr.lambda;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;

/**
 * Strips all overloaded constant names from a complete
 * {@link LogicalExpression}.
 *
 * @author Yoav Artzi
 */
public class StripOverload implements ILogicalExpressionVisitor {

	private LogicalExpression result = null;

	public static Category<LogicalExpression> of(
			Category<LogicalExpression> category) {
		if (category.getSemantics() == null) {
			return category;
		} else {
			return category.cloneWithNewSemantics(of(category.getSemantics()));
		}

	}

	public static LogicalExpression of(LogicalExpression exp) {
		final StripOverload visitor = new StripOverload();
		visitor.visit(exp);
		return visitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getBody().accept(this);
		if (lambda.getBody() != result) {
			result = new Lambda(lambda.getArgument(), result);
		} else {
			result = lambda;
		}
	}

	@Override
	public void visit(Literal literal) {
		literal.getPredicate().accept(this);
		final LogicalExpression newPredicate = result;

		final int len = literal.numArgs();
		final LogicalExpression[] newArgs = new LogicalExpression[len];
		boolean argChanged = false;
		for (int i = 0; i < len; ++i) {
			final LogicalExpression arg = literal.getArg(i);
			arg.accept(this);
			argChanged |= arg != result;
			newArgs[i] = result;
		}

		if (argChanged) {
			result = new Literal(newPredicate, newArgs);
		} else if (newPredicate != literal.getPredicate()) {
			result = new Literal(newPredicate, literal);
		} else {
			result = literal;
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		result = OverloadedLogicalConstant.getWrapped(logicalConstant);
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
		result = variable;
	}

}
