package edu.uw.cs.lil.amr.lambda;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.collections.PowerSet;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceExpression;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.learn.gradient.SimpleGradient;

/**
 * Extract AMR sub-expressions to identify good spans in the chart for early
 * updates. All extracted sub-expressions have one of the following types: e,
 * <e,t>, <<e,t>,<e,t>> and <e,<e,t>>.
 *
 * @author Yoav Artzi
 */
public class GetAmrSubExpressions implements ILogicalExpressionVisitor {

	private final Set<LogicalExpression>	subExpressions	= new HashSet<>();

	private GetAmrSubExpressions() {
		// Private ctor. Use of() method.
	}

	public static Set<LogicalExpression> of(LogicalExpression exp) {
		final GetAmrSubExpressions visitor = new GetAmrSubExpressions();
		visitor.visit(exp);
		return visitor.subExpressions.stream().map(e -> extractEntities(e))
				.flatMap(e -> e.stream()).collect(Collectors.toSet());
	}

	/**
	 * For a given expression, create new expressions by potentially extracting
	 * out a single skolem term and wrapping with a {@link Lambda} term. Only
	 * extract entities from {@link Lambda} terms. For any other expression,
	 * simply return a singleton set with that expression.
	 */
	private static Set<LogicalExpression> extractEntities(
			LogicalExpression expression) {
		final Set<LogicalExpression> entities = GetAllSkolemTerms.of(
				expression, true);
		final Set<LogicalExpression> expressions = new HashSet<>();
		expressions.add(expression);

		if (expression instanceof Lambda
				&& ((Lambda) expression)
						.getComplexType()
						.getRange()
						.equals(LogicLanguageServices.getTypeRepository()
								.getTruthValueType())) {
			for (final LogicalExpression entity : entities) {
				final Variable variable = new Variable(entity.getType());
				expressions.add(new Lambda(variable, ReplaceExpression.of(
						expression, entity, variable)));
			}
		}

		return expressions;
	}

	/**
	 * Tests if the given {@link LogicalExpression} has free variables that are
	 * not {@link SkolemId}s.
	 */
	private static boolean isClosed(LogicalExpression exp) {
		for (final Variable variable : exp.getFreeVariables()) {
			if (!(variable instanceof SkolemId)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void visit(Lambda lambda) {
		if (isClosed(lambda)) {
			subExpressions.add(lambda);
		}
		lambda.getBody().accept(this);
	}

	@Override
	public void visit(Literal literal) {
		if (isClosed(literal)) {
			subExpressions.add(literal);
		}
		final int len = literal.numArgs();
		for (int i = 0; i < len; ++i) {
			literal.getArg(i).accept(this);
		}

		if (LogicLanguageServices.isCoordinationPredicate(literal
				.getPredicate())) {

			// Test if the LF has a single non-SkoleID free variable.
			int freeVariableCounter = 0;
			for (final Variable variable : literal.getFreeVariables()) {
				if (!(variable instanceof SkolemId)) {
					++freeVariableCounter;
				}
			}

			if (freeVariableCounter == 1) {
				// Case logical coordination, extract subsets of arguments to
				// create new partial coordinations.

				// The predicate will be used to construct the new literals.
				final LogicalExpression predicate = literal.getPredicate();

				// Locate the unary typing literal. We assumes there is only
				// one.
				Literal typingLiteral = null;
				Variable entityVariable = null;
				final List<Literal> relationLiterals = new LinkedList<>();
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					if (arg instanceof Literal
							&& ((Literal) arg).numArgs() == 1
							&& ((Literal) arg).getArg(0) instanceof Variable) {
						typingLiteral = (Literal) arg;
						entityVariable = (Variable) ((Literal) arg).getArg(0);
					} else if (arg instanceof Literal
							&& ((Literal) arg).numArgs() == 2) {
						relationLiterals.add((Literal) arg);
					} else {
						SimpleGradient.LOG.error("Invalid AMR: %s", literal);
						return;
					}
				}
				if (typingLiteral == null) {
					SimpleGradient.LOG.error("Invalid AMR: %s", literal);
					return;
				}

				// Sub-expression with only the unary typing literal.
				subExpressions.add(new Lambda(entityVariable, typingLiteral));

				// Create a new literal that will allow to append new conditions
				// to the created sub-expression. The variable is <e,t>-typed,
				// and the literal will be ($0:<e,t> $1:e), so the final open
				// sub-expression will a have the type <<e,t>,<e,t>>.
				final Variable threadingVariable = new Variable(
						LogicLanguageServices.getTypeRepository()
								.getTypeCreateIfNeeded(
										LogicLanguageServices
												.getTypeRepository()
												.getTruthValueType(),
										LogicLanguageServices
												.getTypeRepository()
												.getEntityType()));
				final Literal threadingLiteral = new Literal(threadingVariable,
						ArrayUtils.create(entityVariable));

				for (final List<Literal> relationLiteralsSubset : new PowerSet<>(
						relationLiterals)) {
					if (!relationLiteralsSubset.isEmpty()) {
						// Going to re-use this array, so the second time it
						// will be used, we will make a copy of it.
						final LogicalExpression[] argArray = new LogicalExpression[relationLiteralsSubset
								.size() + 1];
						int i = 1;
						for (final Literal relationLiteral : relationLiteralsSubset) {
							argArray[i++] = relationLiteral;
						}

						// Extract coordination with the unary typing literal
						// and any
						// subset of the other arguments.
						argArray[0] = typingLiteral;
						subExpressions.add(new Lambda(entityVariable,
								new Literal(predicate, argArray)));

						// Extract coordination without the unary typing literal
						// but
						// only with a subset of the other arguments.
						final LogicalExpression[] argArrayWithoutTyping = Arrays
								.copyOf(argArray, argArray.length);
						argArrayWithoutTyping[0] = threadingLiteral;
						subExpressions.add(new Lambda(threadingVariable,
								new Lambda(entityVariable, new Literal(
										predicate, argArrayWithoutTyping))));
					}
				}
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		// Nothing to do.
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
	}

}