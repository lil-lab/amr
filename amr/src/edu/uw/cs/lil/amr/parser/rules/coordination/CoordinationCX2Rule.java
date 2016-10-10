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
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Skolemized distributive AMR coordination (case #2):
 * <p>
 * A:f0 C[A]:c(f1,...,fn) =>
 * <p>
 * A:(lambda $1 ... (lambda $k (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>
 * (c:<e,t> $0) (c_op1:<e,<e,t>> $0 f'0) ... (c_opn+1:<e,<e,t>> $0 f'n))))))
 * <p>
 * where fi = (lambda $1 ... (lambda $k f'i)) and f'i is of type e.
 *
 * @author Yoav Artzi
 */
public class CoordinationCX2Rule extends AbstractCoordinationCXRule {

	private static String		LABEL				= "CX2";
	private static final long	serialVersionUID	= 1107330626299658513L;

	public CoordinationCX2Rule(
			ICategoryServices<LogicalExpression> categoryServices) {
		super(categoryServices, LABEL);
	}

	@Override
	protected Category<LogicalExpression> doApply(LogicalExpression first,
			Literal rest, Syntax resultSyntax,
			LogicalConstant coordinationWrapper) {
		LogicalExpression updatedFirst = first;

		// Verify that the final return type of each of the arguments is
		// not truth-value. If not return an empty collection.
		final Type firstElementFinalType = AMRServices
				.getFinalType(updatedFirst.getType());
		if (LogicLanguageServices.getTypeRepository().getTruthValueType()
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
			coordinationArguments[i + 1] = strippedArg;
		}

		final LogicalExpression coordination = CoordinationServices
				.createCoordination(
						coordinationArguments,
						LogicLanguageServices.getTypeRepository()
								.generalizeType(
										coordinationArguments[0].getType()),
						coordinationWrapper);
		if (coordination == null) {
			return null;
		} else {
			return Category.<LogicalExpression> create(
					resultSyntax,
					wrapWithLambdas(AMRServices.skolemize(coordination),
							commonVariables));
		}

	}
}
