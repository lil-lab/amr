package edu.uw.cs.lil.amr.lambda;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;

/**
 * Replaces {@link LogicalConstant}s with underspecified placeholders as
 * required and replaces all referring {@link SkolemId}s with placeholders.
 *
 * @author Yoav Artzi
 */
public class Underspecify implements ILogicalExpressionVisitor {

	private final SpecificationMapping	mapping;
	private LogicalExpression			result	= null;

	private Underspecify(SpecificationMapping mapping) {
		this.mapping = mapping;
		// Used through static 'of' method.
	}

	public static LogicalExpression of(LogicalExpression exp,
			SpecificationMapping mapping) {
		final Underspecify visitor = new Underspecify(mapping);
		visitor.visit(exp);
		return visitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getArgument().accept(this);
		final Variable newVar = (Variable) result;
		lambda.getBody().accept(this);
		if (newVar != lambda.getArgument() || result != lambda.getBody()) {
			result = new Lambda(newVar, result);
		} else {
			result = lambda;
		}
	}

	@Override
	public void visit(Literal literal) {
		literal.getPredicate().accept(this);
		final LogicalExpression newPredicate = result;
		boolean argChanged = false;
		final int numArgs = literal
				.numArgs();
		final LogicalExpression[] newArgs = new LogicalExpression[numArgs];
		for (int i = 0; i < numArgs; ++i) {
			final LogicalExpression arg = literal.getArg(i);
			arg.accept(this);
			newArgs[i] = result;
			if (result != arg) {
				argChanged = true;
			}
		}

		final Literal newLiteral;
		if (argChanged) {
			newLiteral = new Literal(newPredicate, newArgs);
		} else if (newPredicate != literal.getPredicate()) {
			newLiteral = new Literal(newPredicate, literal);
		} else {
			newLiteral = literal;
		}

		if (AMRServices.isRefPredicate(newLiteral.getPredicate())
				&& newLiteral.numArgs() == 1
				&& newLiteral.getArg(0) instanceof SkolemId) {
			// Case referring predicate with an ID set.
			result = new Literal(newLiteral.getPredicate(),
					ArrayUtils.create(SkolemServices.getIdPlaceholder()));
		} else {
			result = newLiteral;
		}

	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		result = mapping.underspecify(logicalConstant);
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		result = variable;
	}

}
