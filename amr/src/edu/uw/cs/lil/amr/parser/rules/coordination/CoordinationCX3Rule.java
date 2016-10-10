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
import edu.cornell.cs.nlp.spf.mr.language.type.RecursiveComplexType;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Non-skolemized distributive AMR coordination (case #3):
 * <p>
 * A:f0 C[A]:c(f1,...,fn) =>
 * <p>
 * A:(lambda $1:e ... (lambda $k:e (lambda $0:e (and:<t*,t> (c:<e,t> $0)
 * (c_op1:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !0 f'0)) ... (c_opn+1:<e,<e,t>> $0
 * (a:<id,<<e,t>,e>> !n f'n))))))
 * <p>
 * where fi = (lambda $1 ... (lambda $k f'i)) and f'i is of type <e,t>.
 *
 * @author Yoav Artzi
 */
public class CoordinationCX3Rule extends AbstractCoordinationCXRule {

	private static String		LABEL				= "CX3";
	private static final long	serialVersionUID	= 6301567236391711400L;

	public CoordinationCX3Rule(
			ICategoryServices<LogicalExpression> categoryServices) {
		super(categoryServices, LABEL);
	}

	@Override
	protected Category<LogicalExpression> doApply(LogicalExpression first,
			Literal rest, Syntax resultSyntax,
			LogicalConstant coordinationWrapper) {
		// The newly added element.
		LogicalExpression firstElement = first;

		// Verify that the type of firstElement is <....,<e,t>>.
		Type firstElementFinalType = firstElement.getType();
		while (firstElementFinalType.isComplex()
				&& !(firstElementFinalType instanceof RecursiveComplexType)
				&& !(LogicLanguageServices.getTypeRepository().getEntityType()
						.equals(firstElementFinalType.getDomain()) && LogicLanguageServices
						.getTypeRepository().getTruthValueType()
						.equals(firstElementFinalType.getRange()))) {
			firstElementFinalType = firstElementFinalType.getRange();
		}
		if (!LogicLanguageServices.getTypeRepository().getTruthValueType()
				.equals(firstElementFinalType.getRange())
				|| !LogicLanguageServices.getTypeRepository().getEntityType()
						.equals(firstElementFinalType.getDomain())) {
			return null;
		}

		/*
		 * Create variables to account for the expected arguments for each
		 * coordinated element. Only modify the first element. The other will be
		 * modified just when the coordination is created. Remove all variables
		 * until an <e,t>-typed expression is left.
		 */
		final List<Variable> commonVariables = new LinkedList<>();
		while (firstElement instanceof Lambda
				&& ((Lambda) firstElement).getBody() instanceof Lambda) {
			final Variable variable = new Variable(((Lambda) firstElement)
					.getArgument().getType());
			firstElement = categoryServices.apply(firstElement, variable);
			commonVariables.add(variable);
		}

		// Create the conjunction argument list.
		final int restNumArgs = rest.numArgs();
		final LogicalExpression[] coordinationArguments = new LogicalExpression[restNumArgs + 1];
		final Literal skolemized = AMRServices.skolemize(firstElement);
		if (skolemized == null) {
			return null;
		}
		coordinationArguments[0] = skolemized;
		for (int i = 0; i < restNumArgs; ++i) {
			final LogicalExpression arg = rest.getArg(i);
			// Strip lambdas using common variables.
			LogicalExpression strippedArg = arg;
			for (final Variable variable : commonVariables) {
				strippedArg = categoryServices.apply(strippedArg, variable);
			}
			final Literal skolemizedArg = AMRServices.skolemize(strippedArg);
			if (skolemizedArg == null) {
				return null;
			}
			coordinationArguments[i + 1] = skolemizedArg;
		}

		final LogicalExpression coordination = CoordinationServices
				.createCoordination(
						coordinationArguments,
						LogicLanguageServices.getTypeRepository()
								.generalizeType(
										coordinationArguments[0].getType()),
						coordinationWrapper);

		return coordination == null ? null : Category
				.<LogicalExpression> create(resultSyntax,
						wrapWithLambdas(coordination, commonVariables));

	}
}
