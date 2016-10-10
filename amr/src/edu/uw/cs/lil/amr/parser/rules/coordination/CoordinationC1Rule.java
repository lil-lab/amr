package edu.uw.cs.lil.amr.parser.rules.coordination;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName.Direction;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Binary coordination rule to combine the combinator with the first combined
 * element: C:c A:f => C[A]:c(f).
 *
 * @author Yoav Artzi
 */
public class CoordinationC1Rule implements IBinaryParseRule<LogicalExpression> {
	public static final ILogger	LOG					= LoggerFactory
															.create(CoordinationC1Rule.class);
	private static final String	LABEL				= "C1";
	private static final long	serialVersionUID	= -7437025368175046921L;
	private final RuleName		ruleName			= RuleName.create(LABEL,
															Direction.FORWARD);

	public CoordinationC1Rule() {
		LOG.warn(
				"While it probably has no effect, %s is not handled in NF constraints",
				getName());
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> left,
			Category<LogicalExpression> right, SentenceSpan span) {
		if (left.getSyntax().equals(Syntax.C)
				&& !(right.getSyntax() instanceof CoordinationSyntax)
				&& left.getSemantics() instanceof LogicalConstant
				&& CoordinationServices.isCoordinator((LogicalConstant) left
						.getSemantics()) && right.getSemantics() != null) {
			// The type of the coordinated expressions.
			final Type type = LogicLanguageServices.getTypeRepository()
					.generalizeType(right.getSemantics().getType());

			// Create the semantics, syntax, and the category.
			return new ParseRuleResult<>(ruleName,
					Category.<LogicalExpression> create(new CoordinationSyntax(
							right.getSyntax()), CoordinationServices
							.createLiteral(((LogicalConstant) left
									.getSemantics()).getBaseName(), ArrayUtils
									.create(right.getSemantics()), type)));

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

	public static class Creator implements
			IResourceObjectCreator<CoordinationC1Rule> {
		private final String	type;

		public Creator() {
			this("rule.coordination.c1");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public CoordinationC1Rule create(Parameters params,
				IResourceRepository repo) {
			return new CoordinationC1Rule();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type, CoordinationC1Rule.class)
					.setDescription("C1 AMR cooridnation rule.").build();
		}
	}

}
