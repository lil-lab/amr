package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.Simplify;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Logical coordination (case #1) of truth-typed coordinates:
 * <p>
 * A:f0 C[A]:c(f1,...,fn) =>
 * <p>
 * A:(lambda $0:e ... (lambda $k:e (c:<t*,t> T(f0) ... T(fn))))
 * <p>
 * given that T(f) extracts the outermost truth-typed subexpression of f and the
 * variables used are the common extracted ones. c must be a conjunction.
 * Disjunction are not allowed using this rule (they use the specialized AMR
 * coordination).
 *
 * @author Yoav Artzi
 */
public class CoordinationCX1Rule extends AbstractCoordinationCXRule {
	private static String		LABEL				= "CX1";
	private static final long	serialVersionUID	= -5429945410166247087L;

	public CoordinationCX1Rule(
			ICategoryServices<LogicalExpression> categoryServices) {
		super(categoryServices, LABEL);
	}

	@Override
	protected Category<LogicalExpression> doApply(LogicalExpression first,
			Literal rest, Syntax outputSyntax,
			LogicalConstant coordinationWrapper) {
		// Only conjunction are allowed using this rule.
		if (!CoordinationServices.getConjunctionLabel().equals(
				coordinationWrapper.getBaseName())) {
			return null;
		}

		// The newly added element.
		LogicalExpression updatedFirst = first;

		// Verify that the final return type of each of the arguments is
		// truth-value. If not return an empty collection.
		final Type firstElementFinalType = AMRServices
				.getFinalType(updatedFirst.getType());
		if (!LogicLanguageServices.getTypeRepository().getTruthValueType()
				.equals(firstElementFinalType)) {
			return null;
		}

		/*
		 * Create variables to account for the expected arguments for each
		 * coordinated element. Only modify the first element. The other will be
		 * modified just when the coordination is created.
		 */
		final List<Variable> commonVariables = new LinkedList<>();
		while (updatedFirst instanceof Lambda) {
			final Variable variable = new Variable(((Lambda) updatedFirst)
					.getArgument().getType());
			updatedFirst = categoryServices.apply(updatedFirst, variable);
			commonVariables.add(variable);
		}

		/*
		 * The final type of the coordinated elements, after all expected
		 * variables are consumed, is truth-typed, coordinate using logical
		 * conjunction or disjunction. Create the coordinated semantic
		 * expression. Wrap with lambda terms for all expected variables.
		 */

		// Create the conjunction argument list.
		final int restNumArgs = rest.numArgs();
		final LogicalExpression[] coordinationArguments = new LogicalExpression[restNumArgs + 1];
		coordinationArguments[0] = updatedFirst;
		for (int i = 0; i < restNumArgs; ++i) {
			final LogicalExpression arg = rest.getArg(i);
			// Strip lambdas using common variables.
			LogicalExpression strippedArg = arg;
			for (final Variable variable : commonVariables) {
				strippedArg = categoryServices.apply(strippedArg, variable);
			}
			if (!LogicLanguageServices.getTypeRepository().getTruthValueType()
					.equals(strippedArg.getType())) {
				return null;
			}
			coordinationArguments[i + 1] = strippedArg;
		}

		// Normalize for duplications that result from combining adverbial
		// phrases, preposition phrases or adjectives. For example, naively
		// combining two adjectives N/N:\lambda x. \lambda f(x) \land red(x) and
		// N/N:\lambda x. \lambda f(x) \land white(x) will result in the
		// following duplication: N/N:\lambda x. \lambda f(x) \land red(x) \land
		// f(x) \land white(x). We need to strip the redundant f(x).
		if (coordinationArguments[0] instanceof Literal
				&& LogicLanguageServices.getConjunctionPredicate().equals(
						((Literal) coordinationArguments[0]).getPredicate())) {
			Literal argumentToStrip = null;
			for (int i = 0; i < ((Literal) coordinationArguments[0]).numArgs(); ++i) {
				final LogicalExpression arg = ((Literal) coordinationArguments[0])
						.getArg(i);
				if (arg instanceof Literal && ((Literal) arg).numArgs() == 1
						&& ((Literal) arg).getArg(0) instanceof Variable
						&& ((Literal) arg).getPredicate() instanceof Variable) {
					if (argumentToStrip == null) {
						argumentToStrip = (Literal) arg;
					} else {
						// We found two arguments to strip. This is not the case
						// we are looking for, so don't strip.
						argumentToStrip = null;
						break;
					}
				}
			}

			if (argumentToStrip != null) {
				for (int i = 1; i < coordinationArguments.length; ++i) {
					final LogicalExpression coordinationArgument = coordinationArguments[i];
					boolean found = false;
					if (coordinationArgument instanceof Literal
							&& LogicLanguageServices.getConjunctionPredicate()
									.equals(((Literal) coordinationArgument)
											.getPredicate())) {
						for (int j = 0; j < ((Literal) coordinationArgument)
								.numArgs(); ++j) {
							if (((Literal) coordinationArgument).getArg(j)
									.equals(argumentToStrip)) {
								found = true;
								break;
							}
						}
					}
					if (!found) {
						// The argument to strip wasn't found in this argument,
						// so abort normalization and continue.
						argumentToStrip = null;
						break;
					}
				}
			}

			if (argumentToStrip != null) {
				// Case the argument is present in all coordinated arguments, so
				// modify each argument to remove it.
				for (int i = 0; i < coordinationArguments.length; ++i) {
					final LogicalExpression coordinationArgument = coordinationArguments[i];
					final int numLiteralArgs = ((Literal) coordinationArgument)
							.numArgs();
					final LogicalExpression[] newLiteralArgs = new LogicalExpression[numLiteralArgs - 1];
					int newIndex = 0;
					boolean stripped = false;
					for (int j = 0; j < numLiteralArgs; ++j) {
						final LogicalExpression arg = ((Literal) coordinationArgument)
								.getArg(j);
						if (!arg.equals(argumentToStrip)) {
							newLiteralArgs[newIndex++] = arg;
						} else if (stripped) {
							// We only allow one argument exactly to be
							// stripped.
							return null;
						} else {
							stripped = true;
						}
					}
					assert newIndex == newLiteralArgs.length;
					if (newIndex == 1) {
						coordinationArguments[i] = newLiteralArgs[0];
					} else {
						coordinationArguments[i] = new Literal(
								((Literal) coordinationArgument).getPredicate(),
								newLiteralArgs);
					}
				}

				// Create the coordination and wrap with a conjunction with the
				// stripped argument.
				final LogicalExpression semantics = wrapWithLambdas(
						Simplify.of(new Literal(
								LogicLanguageServices.getConjunctionPredicate(),
								ArrayUtils.<LogicalExpression> create(
										argumentToStrip,
										new Literal(
												CoordinationServices
														.getCoordinationPredicate(
																coordinationWrapper
																		.getBaseName(),
																LogicLanguageServices
																		.getTypeRepository()
																		.getTruthValueType()),
												coordinationArguments)))),
						commonVariables);

				return Category.create(outputSyntax, semantics);
			}
		}

		final LogicalExpression semantics = wrapWithLambdas(
				new Literal(CoordinationServices.getCoordinationPredicate(
						coordinationWrapper.getBaseName(),
						LogicLanguageServices.getTypeRepository()
								.getTruthValueType()), coordinationArguments),
				commonVariables);

		// Must simplify here to eliminate any redundant and:<t*,t> that might
		// be generated by the process.
		return Category.create(outputSyntax, Simplify.of(semantics));
	}
}
