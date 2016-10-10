package edu.uw.cs.lil.amr.parser.rules.amrspecials.dummyref;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Shifting rule to convert a relation with a dummy entity to a -of relation:
 * S[dcl] : (lambda $0:e (and:<t*,t> ... (pred:<e,<e,t>> $0 DUMMY:e) ...))) ->
 * S[x]\S[x] : (lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1)
 * (pred-of:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:d (lambda $0:e (and:<t*,t>
 * ...)))))))
 *
 * @author Yoav Artzi
 */
public class SentenceWithDummy implements IUnaryParseRule<LogicalExpression> {

	private static final String							LABEL				= "s_with_dummy";

	private static final long							serialVersionUID	= 886746376282499511L;

	private final ICategoryServices<LogicalExpression>	categoryServices;
	private final LogicalExpression						helperCategory;
	private final UnaryRuleName							name;
	private final Syntax								sourceSyntax;

	private final Syntax								targetSyntax;

	public SentenceWithDummy(
			ICategoryServices<LogicalExpression> categoryServices) {
		this.categoryServices = categoryServices;
		this.name = UnaryRuleName.create(LABEL);
		this.targetSyntax = Syntax.read("S[x]\\S[x]");
		this.sourceSyntax = Syntax.read("S[dcl]");
		this.helperCategory = categoryServices
				.readSemantics("(lambda $2:<e,<e,t>> (lambda $3:<e,t> (lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) ($2 $1 (a:<id,<<e,t>,e>> na:id $3)))))))");
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> category, SentenceSpan span) {
		if (isValidArgument(category, span)) {
			final Pair<Lambda, LogicalConstant> pair = DummyEntityServices
					.stripDummy((Lambda) category.getSemantics());
			if (pair != null) {
				final LogicalConstant inversedRelation = AMRServices
						.makeRelationPassive(pair.second(), true);
				if (inversedRelation != null) {
					return new ParseRuleResult<>(name, Category.create(
							targetSyntax, categoryServices.apply(
									categoryServices.apply(helperCategory,
											inversedRelation), pair.first())));
				}
			}
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
		return category.getSyntax().equals(sourceSyntax)
				&& category.getSemantics() instanceof Lambda
				&& DummyEntityServices.hasDummyEntity((Lambda) category
						.getSemantics());
	}

	public static class Creator implements
			IResourceObjectCreator<SentenceWithDummy> {

		private final String	type;

		public Creator() {
			this("rule.shift.amr.sdummy");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SentenceWithDummy create(Parameters params,
				IResourceRepository repo) {
			return new SentenceWithDummy(
					repo.get(ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, SentenceWithDummy.class)
					.setDescription(
							"Shifting rule to convert a relation with a dummy entity to a -of relation: "
									+ "S[dcl] : (lambda $0:e (and:<t*,t> ... (pred:<e,<e,t>> $0 DUMMY:e) ...))) -> S[x]\\S[x] : (lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (pred-of:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:d (lambda $0:e (and:<t*,t> ...)))))))")
					.build();
		}
	}

}
