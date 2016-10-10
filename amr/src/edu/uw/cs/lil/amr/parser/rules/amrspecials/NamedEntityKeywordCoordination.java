package edu.uw.cs.lil.amr.parser.rules.amrspecials;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
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
 * Unary rule to initiate coordination of NE keywords: NP[sg]->C{KEY}.
 *
 * @author Yoav Artzi
 */
public class NamedEntityKeywordCoordination
		implements IUnaryParseRule<LogicalExpression> {

	private static final String	LABEL				= "ne_stamp_coordination";
	private static final long	serialVersionUID	= -8008121820064708909L;
	private final UnaryRuleName	name;
	private final Syntax		sourceSyntax;

	public NamedEntityKeywordCoordination(Syntax sourceSyntax) {
		this.sourceSyntax = sourceSyntax;
		this.name = UnaryRuleName.create(LABEL);
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> category, SentenceSpan span) {
		if (!isValidArgument(category, span)) {
			return null;
		}

		return new ParseRuleResult<>(name,
				Category.<LogicalExpression> create(
						new CoordinationSyntax(AMRServices.KEY),
						CoordinationServices.createLiteral(
								CoordinationServices.getConjunctionLabel(),
								ArrayUtils.create(category.getSemantics()),
								LogicLanguageServices.getTypeRepository()
										.getEntityType())));
	}

	@Override
	public UnaryRuleName getName() {
		return name;
	}

	@Override
	public boolean isValidArgument(Category<LogicalExpression> category,
			SentenceSpan span) {
		// Only apply to the right-most span.
		return category.getSemantics() != null && span.isEnd()
				&& sourceSyntax.equals(category.getSyntax())
				&& category.getSemantics().numFreeVariables() == 0
				&& AMRServices.isSkolemTerm(category.getSemantics())
				&& AMRServices.isNamedEntity((Literal) category.getSemantics());
	}

	public static class Creator
			implements IResourceObjectCreator<NamedEntityKeywordCoordination> {
		private final String type;

		public Creator() {
			this("rule.shifting.amr.necoord");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public NamedEntityKeywordCoordination create(Parameters params,
				IResourceRepository repo) {
			return new NamedEntityKeywordCoordination(
					Syntax.read(params.get("syntax")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, NamedEntityKeywordCoordination.class)
					.setDescription(
							"Unary rule to initiate coordination of NE keywords: NP[sg]->C{NP[sg]}.")
					.addParam("syntax", Syntax.class, "Source syntax.").build();
		}

	}

}
