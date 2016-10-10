package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.uw.cs.lil.amr.lambda.StripOverload;

/**
 * Extract the upper-most sub-expression of a given type, replace it with a
 * provided placeholder constant, return both the extracted expression and the
 * original expression with the placeholder. The extracted expression can be of
 * any type, except a variable. The logical expression is traversed depth-first
 * and only a single expression is extracted.
 *
 * @author Yoav Artzi
 */
public class ExtractTypedSubExpression implements ILogicalExpressionVisitor {

	private int						depth	= 0;
	private final LogicalExpression	placeholder;
	private LogicalExpression		result	= null;
	private final LogicalExpression	subExp;
	private final int				subExpDepth;

	public ExtractTypedSubExpression(LogicalExpression placeholder,
			LogicalExpression subExp, int depth) {
		this.placeholder = placeholder;
		this.subExp = subExp;
		this.subExpDepth = depth;
	}

	public static Result of(LogicalExpression exp,
			LogicalExpression placeholder, Type type, boolean skipCoordinations) {
		final GetDepthOrdering depthVisitor = new GetDepthOrdering(
				skipCoordinations);
		depthVisitor.visit(exp);
		int depth = 0;
		for (final List<LogicalExpression> depthSubExpressions : depthVisitor.subExpressionByDepth) {
			for (final LogicalExpression subExp : depthSubExpressions) {
				if (subExp.getType().equals(type)) {
					final ExtractTypedSubExpression visitor = new ExtractTypedSubExpression(
							placeholder, subExp, depth);
					visitor.visit(exp);
					return new Result(StripOverload.of(visitor.result), subExp);
				}
			}
			++depth;
		}
		return null;
	}

	@Override
	public void visit(Lambda lambda) {
		if (depth == subExpDepth && lambda == subExp) {
			result = placeholder;
		} else if (depth < subExpDepth) {
			++depth;
			lambda.getBody().accept(this);
			--depth;
			if (lambda.getBody() == result) {
				result = lambda;
			} else {
				result = new Lambda(lambda.getArgument(), result);
			}
		} else {
			result = lambda;
		}
	}

	@Override
	public void visit(Literal literal) {
		if (depth == subExpDepth && literal == subExp) {
			result = placeholder;
		} else if (depth < subExpDepth) {
			++depth;
			literal.getPredicate().accept(this);
			if (literal.getPredicate() == result) {
				final int len = literal.numArgs();
				int i = 0;
				boolean argChanged = false;
				final LogicalExpression[] newArgs = new LogicalExpression[len];
				for (; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					arg.accept(this);
					newArgs[i] = result;
					if (arg != result) {
						argChanged = true;
						break;
					}
				}
				++i;
				for (; i < len; ++i) {
					newArgs[i] = literal.getArg(i);
				}
				if (argChanged) {
					result = new Literal(literal.getPredicate(), newArgs);
				} else {
					result = literal;
				}
			} else {
				result = new Literal(result, literal);
			}
			--depth;
		} else {
			result = literal;
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		if (depth == subExpDepth && logicalConstant == subExp) {
			result = placeholder;
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
		// Variables are ignored.
		result = variable;
	}

	public static class Result {
		private final LogicalExpression	expressionWithPlaceholder;
		private final LogicalExpression	extractedSubExpression;

		public Result(LogicalExpression expressionWithPlaceholder,
				LogicalExpression extractedSubExpression) {
			this.expressionWithPlaceholder = expressionWithPlaceholder;
			this.extractedSubExpression = extractedSubExpression;
		}

		public LogicalExpression getExpressionWithPlaceholder() {
			return expressionWithPlaceholder;
		}

		public LogicalExpression getExtractedSubExpression() {
			return extractedSubExpression;
		}
	}

	/**
	 * Get a list of depths, each depth contains a list of logical expressions
	 * in that depth in the given logical form. Logical expressions that contain
	 * no {@link LogicalConstant}s are ignored.
	 *
	 * @author Yoav Artzi
	 */
	private static class GetDepthOrdering implements ILogicalExpressionVisitor {

		private int									depth					= 0;
		private final boolean						skipCoordinations;
		private final List<List<LogicalExpression>>	subExpressionByDepth	= new LinkedList<>();
		private boolean								variablesOnly			= false;

		public GetDepthOrdering(boolean skipCoordinations) {
			this.skipCoordinations = skipCoordinations;
			subExpressionByDepth.add(new LinkedList<>());
		}

		@Override
		public void visit(Lambda lambda) {
			addCurrent(lambda);
			increaseDepth();
			lambda.getBody().accept(this);
			--depth;
		}

		@Override
		public void visit(Literal literal) {
			increaseDepth();
			boolean literalHasOnlyVariables = true;
			literal.getPredicate().accept(this);
			literalHasOnlyVariables &= variablesOnly;
			final int len = literal.numArgs();
			for (int i = 0; i < len; ++i) {
				literal.getArg(i).accept(this);
				literalHasOnlyVariables &= variablesOnly;
			}
			--depth;

			if (!literalHasOnlyVariables
					&& (!skipCoordinations || !LogicLanguageServices
							.isCoordinationPredicate(literal.getPredicate()))) {
				addCurrent(literal);
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			addCurrent(logicalConstant);
			variablesOnly = false;
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do -- not collecting variables.
			variablesOnly = true;
		}

		private void addCurrent(LogicalExpression current) {
			subExpressionByDepth.get(depth).add(current);
		}

		private void increaseDepth() {
			++depth;
			if (subExpressionByDepth.size() < depth + 1) {
				subExpressionByDepth.add(new LinkedList<>());
			}
		}

	}

}
