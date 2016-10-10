package edu.uw.cs.lil.amr.parser.rules.coordination;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceFreeVariablesIfPresent;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ParseRuleResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName.Direction;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Binary rule to add an item on the left to a coordination: A:f0
 * C[A]:c(f1,...,fn) => C[A]:c(f0,f1,...,fn).
 *
 * @author Yoav Artzi
 */
public class CoordinationC2Rule implements IBinaryParseRule<LogicalExpression> {
	public static final ILogger	LOG					= LoggerFactory
															.create(CoordinationC2Rule.class);
	private static final String	LABEL				= "C2";
	private static final long	serialVersionUID	= 6490597490947023244L;

	private final RuleName		ruleName;

	public CoordinationC2Rule() {
		this(RuleName.create(LABEL, Direction.BACKWARD));
	}

	public CoordinationC2Rule(RuleName name) {
		ruleName = name;
		LOG.warn(
				"While it probably has no effect, %s is not handled in NF constraints",
				getName());
	}

	@Override
	public ParseRuleResult<LogicalExpression> apply(
			Category<LogicalExpression> left,
			Category<LogicalExpression> right, SentenceSpan span) {
		// Try to create the unified syntax (this also verifies the semantics of
		// the input categories).
		final CoordinationSyntax unifiedSyntax = CoordinationServices
				.unifyCategories(left, right);
		if (unifiedSyntax != null) {
			return doApply(left.getSemantics(), (Literal) right.getSemantics(),
					unifiedSyntax);
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

	protected ParseRuleResult<LogicalExpression> doApply(
			LogicalExpression left, Literal right, CoordinationSyntax syntax) {
		final Type baseType = right.getType();

		// Create the argument list and the new semantic form.
		final int numArgs = right.numArgs();
		final LogicalExpression[] arguments = new LogicalExpression[numArgs + 1];
		// Make sure there's no free variable overlap.
		arguments[0] = ReplaceFreeVariablesIfPresent.of(left,
				right.getFreeVariables());
		right.copyArgsIntoArray(arguments, 0, 1, numArgs);

		// Create the return category, including the syntactic
		// component.
		final Category<LogicalExpression> resultCategory = Category
				.<LogicalExpression> create(syntax, CoordinationServices
						.createLiteral(((LogicalConstant) right.getPredicate())
								.getBaseName(), arguments, baseType));

		return new ParseRuleResult<>(ruleName, resultCategory);
	}

	public static class Creator implements
			IResourceObjectCreator<CoordinationC2Rule> {

		private final String	type;

		public Creator() {
			this("rule.coordination.c2");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public CoordinationC2Rule create(Parameters params,
				IResourceRepository repo) {
			return new CoordinationC2Rule();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type, CoordinationC2Rule.class)
					.setDescription("C2 AMR coordination rule.").build();
		}

	}
}
