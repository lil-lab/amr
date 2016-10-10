package edu.uw.cs.lil.amr.parser;

import java.util.HashMap;
import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class MergeNamedEntities implements ILogicalExpressionVisitor {

	private final Map<Literal, SkolemId>	namedEntities	= new HashMap<>();
	private LogicalExpression				result			= null;

	private MergeNamedEntities() {
		// Use 'of' method.
	}

	public static LogicalExpression of(LogicalExpression exp) {
		final MergeNamedEntities visitor = new MergeNamedEntities();
		visitor.visit(exp);
		return visitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getBody().accept(this);
		if (result == lambda.getBody()) {
			result = lambda;
		} else {
			result = new Lambda(lambda.getArgument(), result);
		}
	}

	@Override
	public void visit(Literal literal) {
		if (AMRServices.isSkolemTerm(literal)
				&& AMRServices.isNamedEntity(literal)
				&& literal.getArg(0) instanceof SkolemId) {
			if (namedEntities.containsKey(literal)) {
				result = new Literal(AMRServices.createRefPredicate(literal
						.getType()), ArrayUtils.create(namedEntities
						.get(literal)));
			} else {
				namedEntities.put(literal, (SkolemId) literal.getArg(0));
				result = literal;
			}
		} else {
			literal.getPredicate().accept(this);
			final LogicalExpression newPredicate = result;

			final int len = literal.numArgs();
			final LogicalExpression[] newArgs = new LogicalExpression[len];
			boolean argChanged = false;
			for (int i = 0; i < len; ++i) {
				final LogicalExpression arg = literal.getArg(i);
				arg.accept(this);
				newArgs[i] = result;
				if (arg != result) {
					argChanged = true;
				}
			}

			if (argChanged) {
				result = new Literal(newPredicate, newArgs);
			} else if (newPredicate != literal.getPredicate()) {
				result = new Literal(newPredicate, literal);
			} else {
				result = literal;
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		// Nothing to do.
		result = logicalConstant;
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
		result = variable;
	}

}
