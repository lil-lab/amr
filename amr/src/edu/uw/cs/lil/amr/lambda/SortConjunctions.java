package edu.uw.cs.lil.amr.lambda;

import java.util.Arrays;
import java.util.Comparator;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;

/**
 * Visitor to normalize the structure of a AMR logical form. The visitor sorts
 * conjuncts. Unary literals are the first arguments, and later arguments are
 * sorted by their predicate name.
 *
 * @author Yoav Artzi
 *
 */
public class SortConjunctions implements ILogicalExpressionVisitor {

	private static final Comparator<LogicalExpression>	COMPARATOR	= (c1,
			c2) -> {

																		final boolean c1Literal = c1 instanceof Literal;
																		final boolean c2Literal = c2 instanceof Literal;

																		if (c1Literal
																				&& c2Literal) {
																			final Literal literal1 = (Literal) c1;
																			final Literal literal2 = (Literal) c2;

																			if (literal1
																					.numArgs() != literal2
																							.numArgs()) {
																				return Integer
																						.compare(
																								literal1.numArgs(),
																								literal2.numArgs());
																			}

																			if (literal1
																					.getPredicate() instanceof LogicalConstant
																					&& literal2
																							.getPredicate() instanceof LogicalConstant) {
																				return ((LogicalConstant) literal1
																						.getPredicate())
																								.getBaseName()
																								.compareTo(
																										((LogicalConstant) literal2
																												.getPredicate())
																														.getBaseName());
																			}

																			return Boolean
																					.compare(
																							literal1.getPredicate() instanceof LogicalConstant,
																							literal2.getPredicate() instanceof LogicalConstant);
																		} else {
																			return Boolean
																					.compare(
																							c1Literal,
																							c2Literal);
																		}
																	};

	private LogicalExpression							result		= null;

	private SortConjunctions() {
		// Use via 'of' method.
	}

	public static LogicalExpression of(LogicalExpression exp) {
		if (exp == null) {
			return null;
		}
		final SortConjunctions visitor = new SortConjunctions();
		visitor.visit(exp);
		return visitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		// Nothing to do with the argument.

		lambda.getBody().accept(this);
		if (lambda.getBody() == result) {
			result = lambda;
		} else {
			result = new Lambda(lambda.getArgument(), result);
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
			newArgs[i] = result;
			if (arg != result) {
				argChanged = true;
			}
		}

		if (LogicLanguageServices
				.isCoordinationPredicate(literal.getPredicate())) {
			// Sort the elements in the coordination.
			Arrays.sort(newArgs, COMPARATOR);
			argChanged = true;
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
		// Nothing to do.
		result = logicalConstant;
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
		result = variable;
	}

}
