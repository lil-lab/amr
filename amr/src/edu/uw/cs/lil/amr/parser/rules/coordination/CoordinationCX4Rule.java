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
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ContainsConstantType;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceExpression;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.ExtractTypedSubExpression.Result;

/**
 * Skolemized collective AMR coordination (case #4):
 * <p>
 * A:f0 C[A]:c(f1,...,fn) =>
 * <p>
 * A:(lambda $2:e ... (lambda $k:e (lambda $0:e (pred:<e,<e,t> $0
 * (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (c:<e,t> $1)
 * (c_op1:<e,<e,t>> $1 f'0) ... (c_opn+1:<e,<e,t>> $1 f'n))))))))
 * <p>
 * where fi = (lambda $2:e ... (lambda $k:e (lambda $0:e (pred:<e,<e,t>> $0
 * f'i)))).
 *
 * @author Yoav Artzi
 */
public class CoordinationCX4Rule extends AbstractCoordinationCXRule {

	private static String			LABEL				= "CX4";
	private static final long		serialVersionUID	= -8210930813774043342L;
	private final LogicalConstant	extractionPlaceholder;

	public CoordinationCX4Rule(
			ICategoryServices<LogicalExpression> categoryServices) {
		super(categoryServices, LABEL);
		this.extractionPlaceholder = LogicalConstant
				.create(LABEL, LogicLanguageServices.getTypeRepository()
						.getEntityType(), true);
	}

	@Override
	protected Category<LogicalExpression> doApply(LogicalExpression first,
			Literal rest, Syntax resultSyntax,
			LogicalConstant coordinationWrapper) {
		// Get the variables of the first argument and strip it from lambda
		// terms.
		LogicalExpression firstElement = first;
		final List<Variable> commonVariables = new LinkedList<>();
		while (firstElement instanceof Lambda) {
			commonVariables.add(((Lambda) firstElement).getArgument());
			firstElement = ((Lambda) firstElement).getBody();
		}

		// Extract the upper-most e-typed element from the first element.
		final Result extraction = ExtractTypedSubExpression.of(firstElement,
				extractionPlaceholder, LogicLanguageServices
						.getTypeRepository().getEntityType(), false);
		if (extraction == null) {
			return null;
		}
		firstElement = extraction.getExtractedSubExpression();
		final LogicalExpression remainder = extraction
				.getExpressionWithPlaceholder();

		// The remaining expression is used collectively over the coordination
		// entity we are creating. If this expression includes other entities,
		// return null. This avoids "swallowing" repeating entities.
		if (ContainsConstantType.of(remainder,
				AMRServices.getTypingPredicateType())) {
			return null;
		}

		// For each of the remaining arguments, strip all lambdas using
		// applications, extract the sub-expression and verify the remainder is
		// identical.
		final int restNumArgs = rest.numArgs();
		final LogicalExpression[] coordinationArguments = new LogicalExpression[restNumArgs + 1];
		coordinationArguments[0] = firstElement;
		for (int i = 0; i < restNumArgs; ++i) {
			LogicalExpression strippedArg = rest.getArg(i);
			for (final Variable variable : commonVariables) {
				strippedArg = categoryServices.apply(strippedArg, variable);
				if (strippedArg == null) {
					return null;
				}
			}
			final Result argExtraction = ExtractTypedSubExpression.of(
					strippedArg, extractionPlaceholder, LogicLanguageServices
							.getTypeRepository().getEntityType(), false);
			if (argExtraction == null
					|| !argExtraction.getExpressionWithPlaceholder().equals(
							remainder)) {
				return null;
			}
			coordinationArguments[i + 1] = argExtraction
					.getExtractedSubExpression();
		}

		// Create the AMR coordination.
		final LogicalExpression coordination = CoordinationServices
				.createCoordination(coordinationArguments,
						LogicLanguageServices.getTypeRepository()
								.getEntityType(), coordinationWrapper);
		if (coordination == null) {
			return null;
		}

		// Place the coordination inside the remainder.
		final LogicalExpression finalBody = ReplaceExpression.of(remainder,
				extractionPlaceholder, AMRServices.skolemize(coordination));

		// Wrap with lambda terms for the common variables and create the
		// category.
		final LogicalExpression finalSemantics = wrapWithLambdas(finalBody,
				commonVariables);
		return Category
				.<LogicalExpression> create(resultSyntax, finalSemantics);
	}

}
