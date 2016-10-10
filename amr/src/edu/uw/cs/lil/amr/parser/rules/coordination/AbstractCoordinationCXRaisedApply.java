package edu.uw.cs.lil.amr.parser.rules.coordination;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ComplexCategory;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceFreeVariablesIfPresent;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Rule to emulate type raising of each of the coordinated arguments. To do so,
 * applies the function category to each of the arguments, before finalizing the
 * coordination. This is equivalent to type-raising each of the arguments prior
 * to coordination and then consuming the function as an argument.
 *
 * @author Yoav Artzi
 */
public abstract class AbstractCoordinationCXRaisedApply implements
		IBinaryParseRule<LogicalExpression> {
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 6892506882355028971L;

	public static final ILogger							LOG	= LoggerFactory
																	.create(AbstractCoordinationCXRaisedApply.class);

	private final ICategoryServices<LogicalExpression>	categoryServices;
	private final AbstractCoordinationCXRule			cxRule;

	public AbstractCoordinationCXRaisedApply(AbstractCoordinationCXRule cxRule,
			ICategoryServices<LogicalExpression> categoryServices) {
		this.cxRule = cxRule;
		this.categoryServices = categoryServices;
	}

	@Override
	public String toString() {
		return getName().toString();
	}

	protected Category<LogicalExpression> doApply(
			ComplexCategory<LogicalExpression> function,
			Category<LogicalExpression> coordiantedArguments) {
		if (coordiantedArguments.getSyntax() instanceof CoordinationSyntax
				&& function
						.getSyntax()
						.getRight()
						.equals(((CoordinationSyntax) coordiantedArguments
								.getSyntax()).getCoordinatedSyntax())
				&& coordiantedArguments.getSemantics() instanceof Literal
				&& ((Literal) coordiantedArguments.getSemantics())
						.getPredicate() instanceof LogicalConstant
				&& ((Literal) coordiantedArguments.getSemantics()).numArgs() >= 2) {
			// Get all the arguments, apply the function to each one of them,
			// and try to coordinate them. The output syntax is the functor
			// syntax following the application.
			final Literal coordinationLiteral = (Literal) coordiantedArguments
					.getSemantics();
			LogicalExpression arg0 = null;
			final int coordLiteralNumArgs = coordinationLiteral.numArgs();
			final LogicalExpression[] appliedArgs = new LogicalExpression[coordLiteralNumArgs - 1];
			// Accumulate all variables to make sure we don't duplicate any.
			Type argType = null;
			final LogicalExpression func = ReplaceFreeVariablesIfPresent.of(
					function.getSemantics(),
					coordinationLiteral.getFreeVariables());
			for (int i = 0; i < coordLiteralNumArgs; ++i) {
				final LogicalExpression arg = coordinationLiteral.getArg(i);
				final LogicalExpression appliedArg = categoryServices.apply(
						func, arg);
				if (appliedArg == null) {
					return null;
				} else {
					if (i == 0) {
						arg0 = appliedArg;
					} else {
						appliedArgs[i - 1] = appliedArg;
					}
					if (argType == null
							|| appliedArg.getType().isExtending(argType)) {
						argType = appliedArg.getType();
					}
				}
			}
			return cxRule.doApply(arg0, CoordinationServices.createLiteral(
					((LogicalConstant) coordinationLiteral.getPredicate())
							.getBaseName(), appliedArgs, argType), function
					.getSyntax().getLeft(),
					(LogicalConstant) ((Literal) coordiantedArguments
							.getSemantics()).getPredicate());
		}
		return null;
	}
}
