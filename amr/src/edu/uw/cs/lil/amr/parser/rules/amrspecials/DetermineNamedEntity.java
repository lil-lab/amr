package edu.uw.cs.lil.amr.parser.rules.amrspecials;

import java.util.Collections;
import java.util.Set;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryReversibleParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Unary rule to skolemize a named entity: N[sg] : f -> NP[sg] : skolemize(f),
 * where f is a named entity.
 *
 * @author Yoav Artzi
 */
public class DetermineNamedEntity implements
		IUnaryReversibleParseRule<LogicalExpression> {

	private static final String	LABEL				= "bare_ne";
	private static final long	serialVersionUID	= 8499833420107435001L;
	private final UnaryRuleName	name;
	private final Syntax		sourceSyntax;
	private final Syntax		targetSyntax;

	public DetermineNamedEntity(Syntax sourceSyntax, Syntax targetSyntax) {
		this.sourceSyntax = sourceSyntax;
		this.targetSyntax = targetSyntax;
		this.name = UnaryRuleName.create(LABEL);
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> category, SentenceSpan span) {
		if (isValidArgument(category, span)) {
			return new ParseRuleResult<>(name, Category.create(targetSyntax,
					AMRServices.skolemize(category.getSemantics())));
		}
		return null;
	}

	@Override
	public UnaryRuleName getName() {
		return name;
	}

	@Override
	public boolean isValidArgument(Category<LogicalExpression> category,
			SentenceSpan span) {
		return category.getSemantics() != null
				&& sourceSyntax.equals(category.getSyntax())
				&& category.getSemantics() instanceof Lambda
				&& AMRServices.isNamedEntityBody((Lambda) category
						.getSemantics());
	}

	@Override
	public Set<Category<LogicalExpression>> reverseApply(
			Category<LogicalExpression> result, SentenceSpan span) {
		if (result.getSemantics() != null
				&& targetSyntax.equals(result.getSyntax())
				&& result.getSemantics() instanceof Literal
				&& AMRServices.isNamedEntity((Literal) result.getSemantics())) {
			return SetUtils.createSingleton(Category.create(sourceSyntax,
					((Literal) result.getSemantics()).getArg(1)));
		} else {
			return Collections.emptySet();
		}
	}

	public static class Creator implements
			IResourceObjectCreator<DetermineNamedEntity> {

		private final String	type;

		public Creator() {
			this("rule.shifting.amr.determine.ne");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public DetermineNamedEntity create(Parameters params,
				IResourceRepository repo) {
			return new DetermineNamedEntity(Syntax.read(params.get("source")),
					Syntax.read(params.get("target")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, DetermineNamedEntity.class)
					.setDescription(
							"Unary rule to skolemize a named entity: N[sg] : f -> NP[sg] : skolemize(f), where f is a named entity.")
					.addParam("source", Syntax.class, "Source syntax")
					.addParam("target", Syntax.class, "Target syntax").build();
		}

	}

}
