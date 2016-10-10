package edu.uw.cs.lil.amr.lambda;

import java.util.IdentityHashMap;
import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.collections.MapOverlay;

/**
 * Given a underspecified logical form A, a specified logical form B, clone B
 * using objects from A while keeping the output semantically equivalent. The
 * traversal order of the result is identical to A and it uses {@link SkolemId}s
 * from A. If this is not possible, return <code>null</code>.
 *
 * @author Yoav Artzi
 */
public class InstanceClone implements ILogicalExpressionVisitor {

	private LogicalExpression		currentSpecified;
	private LogicalExpression		currentUnderspecified;
	private LogicalExpression		result			= null;
	private Map<Variable, Variable>	variableMapping	= new IdentityHashMap<>();

	public InstanceClone(LogicalExpression underspecifiedExp,
			LogicalExpression specifiedExp) {
		this.currentUnderspecified = underspecifiedExp;
		this.currentSpecified = specifiedExp;
	}

	public static LogicalExpression of(LogicalExpression underspecifiedTarget,
			LogicalExpression specifiedExp) {
		final LogicalExpression underspecifiedExp = AMRServices
				.underspecify(specifiedExp);
		final InstanceClone visitor = new InstanceClone(underspecifiedExp,
				specifiedExp);
		visitor.visit(underspecifiedTarget);

		final ReplaceVariables replaceVisitor = new ReplaceVariables(
				visitor.variableMapping);
		replaceVisitor.visit(visitor.result);
		return replaceVisitor.result;
	}

	@Override
	public void visit(Lambda lambda) {
		if (currentUnderspecified instanceof Lambda) {
			final Lambda underspecifiedLambda = (Lambda) currentUnderspecified;
			final Lambda specifiedLambda = (Lambda) currentSpecified;

			currentUnderspecified = underspecifiedLambda.getArgument();
			currentSpecified = specifiedLambda.getArgument();
			lambda.getArgument().accept(this);
			if (result == null) {
				return;
			}
			final Variable newVariable = (Variable) result;

			currentUnderspecified = underspecifiedLambda.getBody();
			currentSpecified = specifiedLambda.getBody();
			lambda.getBody().accept(this);
			if (result == null) {
				return;
			}

			if (newVariable == specifiedLambda.getArgument()
					&& result == specifiedLambda.getBody()) {
				result = currentSpecified;
			} else {
				result = new Lambda(newVariable, result);
			}
		} else {
			result = null;
		}
	}

	@Override
	public void visit(Literal literal) {
		if (currentUnderspecified instanceof Literal) {
			final Literal underspecifiedLiteral = (Literal) currentUnderspecified;
			final Literal specifiedLiteral = (Literal) currentSpecified;
			final int underspecifiedLiteralNumArgs = underspecifiedLiteral
					.numArgs();
			final int literalNumArgs = literal.numArgs();

			if (underspecifiedLiteralNumArgs != literalNumArgs) {
				result = null;
				return;
			}

			// Process the predicate.
			currentUnderspecified = underspecifiedLiteral.getPredicate();
			currentSpecified = specifiedLiteral.getPredicate();
			literal.getPredicate().accept(this);
			if (result == null) {
				return;
			}
			final LogicalExpression newPredicate = result;

			final LogicalExpression[] newArgs = new LogicalExpression[literalNumArgs];
			if (literal.getPredicateType().isOrderSensitive()) {
				for (int i = 0; i < literalNumArgs; ++i) {
					currentUnderspecified = underspecifiedLiteral.getArg(i);
					currentSpecified = specifiedLiteral.getArg(i);
					literal.getArg(i).accept(this);
					if (result == null) {
						return;
					}
					newArgs[i] = result;
				}
			} else {
				// Case order of arguments doesn't matter. Greedily pair
				// arguments.
				final boolean[] underspecifiedArgsCopy = new boolean[underspecifiedLiteralNumArgs];
				final boolean[] specifiedArgsCopy = new boolean[specifiedLiteral
						.numArgs()];
				final Map<Variable, Variable> originalMapping = variableMapping;
				for (int j = 0; j < literalNumArgs; ++j) {
					final LogicalExpression arg = literal.getArg(j);
					boolean found = false;
					final int length = underspecifiedArgsCopy.length;
					for (int i = 0; i < length; ++i) {
						if (!underspecifiedArgsCopy[i]
								&& !specifiedArgsCopy[i]) {
							final MapOverlay<Variable, Variable> overlay = new MapOverlay<>(
									originalMapping);
							variableMapping = overlay;
							currentUnderspecified = underspecifiedLiteral
									.getArg(i);
							currentSpecified = specifiedLiteral.getArg(i);
							arg.accept(this);
							if (result != null) {
								newArgs[j] = result;
								found = true;
								underspecifiedArgsCopy[i] = true;
								specifiedArgsCopy[i] = true;
								originalMapping.putAll(overlay.getOverlayMap());
								break;
							}
						}
					}
					if (!found) {
						result = null;
						return;
					}
				}
			}

			result = new Literal(newPredicate, newArgs);
		} else {
			result = null;
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		if (currentUnderspecified.equals(
				OverloadedLogicalConstant.getWrapped(logicalConstant))) {
			result = currentSpecified;
		} else {
			result = null;
		}
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		result = null;
		if (currentUnderspecified instanceof Variable) {
			if (!variableMapping.containsKey(currentUnderspecified)) {
				// Create mapping if doesn't exist.
				variableMapping.put((Variable) currentUnderspecified, variable);
			}

			if (variableMapping.get(currentUnderspecified).equals(variable)) {
				// Case consistent with existing mapping.
				result = variable;
			}
		}
	}

	/**
	 * Given a mapping from {@link Variable} to {@link Variable}, replace
	 * subexpressions according to the mapping. This might change the semantics
	 * of the {@link LogicalExpression}, specifically it might change the
	 * scoping of variables and the set of free variables for each sub
	 * expression. For example, this might happen if a free variable with
	 * {@link Variable} object that is bound. Careful with using this visitor.
	 *
	 * @author Yoav Artzi
	 */
	public static class ReplaceVariables implements ILogicalExpressionVisitor {

		private final Map<Variable, Variable>	replacements;
		private LogicalExpression				result	= null;

		public ReplaceVariables(Map<Variable, Variable> replacements) {
			this.replacements = replacements;
		}

		@Override
		public void visit(Lambda lambda) {
			if (replacements.containsKey(lambda)) {
				result = replacements.get(lambda);
			} else {
				lambda.getArgument().accept(this);
				final LogicalExpression newArg = result;
				lambda.getBody().accept(this);
				if (result == lambda.getBody()
						&& newArg == lambda.getArgument()) {
					result = lambda;
				} else {
					if (newArg instanceof Variable) {
						result = new Lambda((Variable) newArg, result);
					} else {
						result = null;
					}
				}
			}
		}

		@Override
		public void visit(Literal literal) {
			if (replacements.containsKey(literal)) {
				result = replacements.get(literal);
			} else {
				literal.getPredicate().accept(this);
				if (result == null) {
					return;
				}
				final LogicalExpression newPredicate = result;

				final int len = literal.numArgs();
				final LogicalExpression[] newArgs = new LogicalExpression[len];
				boolean argChanged = false;
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					arg.accept(this);
					if (result == null) {
						return;
					}
					newArgs[i] = result;
					if (arg != result) {
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
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			result = replacements.containsKey(logicalConstant)
					? replacements.get(logicalConstant) : logicalConstant;

		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			result = replacements.containsKey(variable)
					? replacements.get(variable) : variable;
		}

	}

}
