package edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public abstract class AbstractNpKeyword
		implements IUnaryParseRule<LogicalExpression> {

	private static final long	serialVersionUID	= 316305738789548015L;
	private final UnaryRuleName	name;

	public AbstractNpKeyword(UnaryRuleName name) {
		this.name = name;
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> category, SentenceSpan span) {
		if (isValidArgument(category, span)) {
			return new ParseRuleResult<>(name,
					Category.create(AMRServices.getCompleteSentenceSyntax(),
							category.getSemantics()));
		}
		return null;
	}

	@Override
	public UnaryRuleName getName() {
		return name;
	}

}
