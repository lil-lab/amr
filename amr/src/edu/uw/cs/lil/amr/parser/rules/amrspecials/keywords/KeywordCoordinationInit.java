package edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationSyntax;

/**
 * Unary rule to initiate coordination of <e,t>-typed keywords: A : f -> C{KEY}
 * : c(skolemize(f)), where f is <k,t>-typed and k is a type.
 *
 * @author Yoav Artzi
 */
public class KeywordCoordinationInit implements
		IUnaryParseRule<LogicalExpression> {

	private static final String	LABEL				= "keyword_c1";
	private static final long	serialVersionUID	= -1383474197123157337L;
	private final UnaryRuleName	name				= UnaryRuleName
															.create(LABEL);

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> category, SentenceSpan span) {
		if (!isValidArgument(category, span)) {
			return null;
		}

		final Literal skolemized = AMRServices.skolemize(category
				.getSemantics());
		if (skolemized == null) {
			return null;
		}

		return new ParseRuleResult<>(name, Category.<LogicalExpression> create(
				new CoordinationSyntax(AMRServices.KEY), CoordinationServices
						.createLiteral(CoordinationServices
								.getConjunctionLabel(), ArrayUtils
								.create(skolemized), LogicLanguageServices
								.getTypeRepository().getEntityType())));
	}

	@Override
	public UnaryRuleName getName() {
		return name;
	}

	@Override
	public boolean isValidArgument(Category<LogicalExpression> category,
			SentenceSpan span) {
		return span.length() == 1 && span.isEnd()
				&& KeywordUtil.isValidCategory(category);
	}

	public static class Creator implements
			IResourceObjectCreator<KeywordCoordinationInit> {
		private final String	type;

		public Creator() {
			this("rule.shifting.amr.keywordc1");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public KeywordCoordinationInit create(Parameters params,
				IResourceRepository repo) {
			return new KeywordCoordinationInit();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, KeywordCoordinationInit.class)
					.setDescription(
							"Unary rule to initiate coordination of <e,t>-typed keywords: A : f -> C{KEY} : c(skolemize(f)), where f is <k,t>-typed and k is a type.")
					.build();
		}

	}

}
