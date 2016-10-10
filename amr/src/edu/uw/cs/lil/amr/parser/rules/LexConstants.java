package edu.uw.cs.lil.amr.parser.rules;

import java.util.Iterator;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.ComplexSyntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Slash;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.mapping.ScopeMapping;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.ILexicalRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.LexicalResult;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.utils.collections.iterators.TransformedIterator;
import edu.cornell.cs.nlp.utils.collections.stackmap.HashStackMap;
import edu.cornell.cs.nlp.utils.collections.stackmap.IdentityFastStackMap;
import edu.cornell.cs.nlp.utils.counter.Counter;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.OverloadedLogicalConstant;

/**
 * {@link ILexicalRule} that overloads underspecified constants with the surface
 * form of the tokens.
 *
 * @author Yoav Artzi
 */
public class LexConstants implements ILexicalRule<LogicalExpression> {

	private static final UnaryRuleName	NAME				= UnaryRuleName
			.create("lexconst");

	private static final long			serialVersionUID	= -1336373855260259420L;

	private static LexicalResult<LogicalExpression> createResult(
			LexicalEntry<LogicalExpression> entry) {
		final Category<LogicalExpression> category = entry.getCategory();
		if (category.getSemantics() == null) {
			return new LexicalResult<>(NAME, category, entry);

		} else {
			final LogicalExpression overloaded = LexicalizeConstants.of(
					category.getSemantics(), entry.getTokens(),
					category.getSyntax());
			return new LexicalResult<>(NAME,
					overloaded != category.getSemantics()
							? category.cloneWithNewSemantics(overloaded)
							: category,
					entry);
		}
	}

	@Override
	public Iterator<LexicalResult<LogicalExpression>> apply(TokenSeq tokens,
			SentenceSpan span, ILexiconImmutable<LogicalExpression> lexicon) {
		return new TransformedIterator<>(entry -> createResult(entry),
				lexicon.get(tokens));
	}

	@Override
	public UnaryRuleName getName() {
		return NAME;
	}

	public static class Creator
			implements IResourceObjectCreator<LexConstants> {

		private final String type;

		public Creator() {
			this("rule.lex.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public LexConstants create(Parameters params,
				IResourceRepository repo) {
			return new LexConstants();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, LexConstants.class)
					.setDescription(
							"that overloads underspecified constants with the surface form of the tokens")
					.build();
		}

	}

	/**
	 * Visitor to overload {@link LogicalConstant} base names with surface form
	 * and directionality information.
	 *
	 * @author Yoav Artzi
	 */
	private static class LexicalizeConstants
			implements ILogicalExpressionVisitor {

		private final Counter							counterLeft		= new Counter(
				0);
		private final Counter							counterRight	= new Counter(
				0);
		private boolean									externalLambda	= true;
		private final ScopeMapping<Variable, String>	mapping			= new ScopeMapping<Variable, String>(
				new IdentityFastStackMap<Variable, String>(),
				new HashStackMap<String, Variable>());
		private LogicalExpression						result			= null;
		private Syntax									syntax;
		private final TokenSeq							tokens;

		public LexicalizeConstants(TokenSeq tokens, Syntax syntax) {
			this.syntax = syntax;
			this.tokens = tokens;
		}

		public static LogicalExpression of(LogicalExpression exp,
				TokenSeq tokens, Syntax syntax) {
			final LexicalizeConstants visitor = new LexicalizeConstants(tokens,
					syntax);
			visitor.visit(exp);
			return visitor.result;
		}

		@Override
		public void visit(Lambda lambda) {
			final boolean variablePushed;
			if (syntax instanceof ComplexSyntax && externalLambda) {
				final Slash slash = ((ComplexSyntax) syntax).getSlash();
				// Push a mapping of the current variable to directionality
				// string.
				final Variable variable = lambda.getArgument();
				if (slash == Slash.FORWARD) {
					mapping.push(variable, "R" + counterRight.inc());
				} else if (slash == Slash.BACKWARD) {
					mapping.push(variable, "L" + counterLeft.inc());
				} else {
					// No direction, in case of vertical slash.
					mapping.push(variable, "ND");
				}
				syntax = ((ComplexSyntax) syntax).getLeft();
				variablePushed = true;
			} else {
				variablePushed = false;
			}

			lambda.getBody().accept(this);
			if (result == lambda.getBody()) {
				result = lambda;
			} else {
				result = new Lambda(lambda.getArgument(), result);
			}

			if (variablePushed) {
				mapping.pop(lambda.getArgument());
			}
		}

		@Override
		public void visit(Literal literal) {
			externalLambda = false;
			final int len = literal.numArgs();

			// If the predicate is a constant and one of the arguments is a
			// mapped variable, process it differently to overload with
			// directionality string.
			final StringBuilder directionalityString = new StringBuilder();
			if (literal.getPredicate() instanceof LogicalConstant
					&& AMRServices.isUnderspecified(
							(LogicalConstant) literal.getPredicate())
					&& AMRServices.isAmrRelation(literal.getPredicate())) {
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					if (arg instanceof Variable
							&& mapping.containsKey((Variable) arg)) {
						directionalityString.append("A").append(i)
								.append(mapping.peek((Variable) arg));
					} else if (AMRServices.isSkolemTerm(arg)
							&& ((Literal) arg).numArgs() == 2
							&& ((Literal) arg).getArg(1) instanceof Lambda
							&& ((Lambda) ((Literal) arg).getArg(1))
									.getBody() instanceof Literal
							&& ((Literal) ((Lambda) ((Literal) arg).getArg(1))
									.getBody())
											.getPredicate() instanceof Variable
							&& mapping.containsKey(
									(Variable) ((Literal) ((Lambda) ((Literal) arg)
											.getArg(1)).getBody())
													.getPredicate())) {
						// Handle the case where the variable is embedded in a
						// skolem term as its set. This is relatively common
						// when the category that consumes the argument also
						// skolemizes it. The condition above is so complicated
						// due to how complex types are wrapped with lambda
						// terms when we canonicalize LFs. WARNING: this is
						// super brittle!
						directionalityString.append("A").append(i)
								.append(mapping
										.peek((Variable) ((Literal) ((Lambda) ((Literal) arg)
												.getArg(1)).getBody())
														.getPredicate()));
					}
				}
			}

			final LogicalExpression newPredicate;
			if (directionalityString.length() > 0) {
				newPredicate = processPredicate(
						(LogicalConstant) literal.getPredicate(),
						directionalityString.toString());
			} else {
				literal.getPredicate().accept(this);
				newPredicate = result;
			}

			final LogicalExpression[] newArgs = new LogicalExpression[len];
			boolean argChanged = false;
			for (int i = 0; i < len; ++i) {
				final LogicalExpression arg = literal.getArg(i);
				arg.accept(this);
				newArgs[i] = result;
				if (result != arg) {
					argChanged = true;
				}
			}

			if (argChanged) {
				result = new Literal(newPredicate, newArgs);
			} else if (newPredicate != literal.getPredicate()) {
				result = new Literal(newPredicate, literal);
			} else {
				result = literal;
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			externalLambda = false;
			if (AMRServices.isUnderspecified(logicalConstant)
					&& AMRServices.isAmrRelation(logicalConstant)) {
				// Append the tokens to the name of the constant.
				result = OverloadedLogicalConstant.wrap(logicalConstant,
						tokens);
			} else {
				result = logicalConstant;
			}
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			externalLambda = false;
			// Nothing to do.
			result = variable;
		}

		private LogicalExpression processPredicate(LogicalConstant predicate,
				String directionalityString) {
			return OverloadedLogicalConstant.wrap(predicate, tokens,
					directionalityString);
		}
	}

}
