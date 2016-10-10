package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.util.List;
import java.util.ListIterator;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceFreeVariablesIfPresent;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName.Direction;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Abstract coordination rule for adding the last coordinated element into a
 * coordination and generating the coordinated logical form.
 *
 * @author Yoav Artzi
 */
public abstract class AbstractCoordinationCXRule implements
		IBinaryParseRule<LogicalExpression> {

	public static final ILogger								LOG					= LoggerFactory
																						.create(AbstractCoordinationCXRule.class);

	private static final long								serialVersionUID	= -3499575417964483679L;

	protected final ICategoryServices<LogicalExpression>	categoryServices;

	protected final RuleName								ruleName;

	public AbstractCoordinationCXRule(
			ICategoryServices<LogicalExpression> categoryServices,
			String ruleLabel) {
		this.categoryServices = categoryServices;
		this.ruleName = RuleName.create(ruleLabel, Direction.BACKWARD);
		LOG.warn(
				"While it probably has no effect, %s is not handled in NF constraints",
				getName());
	}

	protected static LogicalExpression wrapWithLambdas(LogicalExpression exp,
			List<Variable> variables) {
		LogicalExpression wrapped = exp;
		final ListIterator<Variable> iterator = variables
				.listIterator(variables.size());
		while (iterator.hasPrevious()) {
			final Variable variable = iterator.previous();
			wrapped = new Lambda(variable, wrapped);
		}
		return wrapped;
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> left,
			Category<LogicalExpression> right, SentenceSpan span) {
		final CoordinationSyntax unification = CoordinationServices
				.unifyCategories(left, right);
		if (unification != null) {
			final Category<LogicalExpression> resultCategory = doApply(
					ReplaceFreeVariablesIfPresent.of(left.getSemantics(), right
							.getSemantics().getFreeVariables()),
					(Literal) right.getSemantics(),
					unification.getCoordinatedSyntax(),
					(LogicalConstant) ((Literal) right.getSemantics())
							.getPredicate());
			if (resultCategory != null) {
				return new ParseRuleResult<>(ruleName, resultCategory);
			}
		}

		return null;
	}

	@Override
	public RuleName getName() {
		return ruleName;
	}

	@Override
	public String toString() {
		return ruleName.toString();
	}

	abstract protected Category<LogicalExpression> doApply(
			LogicalExpression first, Literal rest, Syntax resultSyntax,
			LogicalConstant coordinationWrapper);

}
