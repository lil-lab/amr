package edu.uw.cs.lil.amr.parser;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ApplyAndSimplify;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsSet;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;

/**
 * Tries to relax the logical form and force it into a valid AMR with minimal
 * addition of constants.
 *
 * @author Yoav Artzi
 */
public class SloppyAmrClosure implements ILogicalExpressionVisitor {

	public static final ILogger LOG = LoggerFactory
			.create(SloppyAmrClosure.class);

	private final Multiset<Variable>	boundVariables	= HashMultiset.create();
	private final LogicalConstant		dummyTypingPredicate;

	private LogicalExpression result = null;

	private SloppyAmrClosure() {
		// Usage via 'of' method.
		this.dummyTypingPredicate = LogicalConstant.create("UNK",
				AMRServices.getTypingPredicateType(), true);
	}

	public static LogicalExpression of(LogicalExpression semantics) {
		final Type etType = LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded(
						LogicLanguageServices.getTypeRepository()
								.getTruthValueType(),
						LogicLanguageServices.getTypeRepository()
								.getEntityType());
		final Type eType = LogicLanguageServices.getTypeRepository()
				.getEntityType();

		LogicalExpression stripped = semantics;
		while (!(stripped.getType().equals(etType)
				|| stripped.getType().equals(eType))
				&& stripped instanceof Lambda) {
			final Variable variable = ((Lambda) stripped).getArgument();
			if (variable.getType().isComplex() && variable.getType().getRange()
					.equals(variable.getType().getDomain())) {
				final Variable argVariable = new Variable(
						variable.getType().getDomain());
				stripped = ApplyAndSimplify.of(stripped,
						new Lambda(argVariable, argVariable));
			} else {
				stripped = ((Lambda) stripped).getBody();
			}
		}

		final SloppyAmrClosure visitor = new SloppyAmrClosure();
		visitor.visit(stripped);

		final LogicalExpression resultSemantics;
		if (visitor.result == null) {
			LOG.info(() -> {
				if (GetConstantsSet.of(semantics)
						.stream().filter(c -> AMRServices
								.getTypingPredicateType().equals(c.getType()))
						.count() != 0) {
					// This is fairly rare.
					LOG.debug(
							"Failed to close to AMR, but there are instance predicates: %s",
							semantics);
				}
			});
			return null;
		} else if (visitor.result.getType().equals(eType)) {
			resultSemantics = visitor.result;
		} else if (visitor.result.getType().equals(etType)) {
			resultSemantics = AMRServices.skolemize(visitor.result);
		} else {
			// Mostly (or even only) happens in the case of a coordination that
			// is not finalized.
			LOG.info("Unexpected return type from closing to AMR: %s -> %s",
					semantics, visitor.result);
			return null;
		}

		// TODO Fix to avoid this, pretty common (~900)
		if (IsValidAmr.of(resultSemantics, false, true)
				&& AMRServices.isSkolemTerm(resultSemantics)) {
			return resultSemantics;
		} else {
			LOG.info("Result of closing to AMR is invalid: %s -> %s", semantics,
					resultSemantics);
			return null;
		}
	}

	@Deprecated
	public static LogicalExpression simpleClosure(LogicalExpression semantics) {
		// TODO Remove if not using, or at least put in a different class
		final LogicalExpression closed;
		if (semantics.numFreeVariables() == 0
				&& AMRServices.isSkolemTermBody(semantics)) {
			closed = AMRServices.skolemize(semantics);
		} else {
			closed = topEntitiesClosure(semantics);
		}
		LOG.info("Simple closure: %s -> %s", semantics, closed);
		return closed;
	}

	private static LogicalExpression topEntitiesClosure(
			LogicalExpression semantics) {
		final GetTopMostEntities visitor = new GetTopMostEntities();
		visitor.visit(semantics);
		if (visitor.topMostEntities.isEmpty()) {
			return null;
		} else if (visitor.topMostEntities.size() == 1) {
			return visitor.topMostEntities.get(0);
		} else {
			final LogicalExpression body = CoordinationServices
					.createCoordination(
							visitor.topMostEntities.toArray(
									new LogicalExpression[visitor.topMostEntities
											.size()]),
							visitor.topMostEntities.get(0).getType(),
							CoordinationServices.getCoordinationPredicate(
									CoordinationServices.getConjunctionLabel(),
									visitor.topMostEntities.get(0).getType()));
			if (body != null) {
				return AMRServices.skolemize(body);
			} else {
				return null;
			}
		}
	}

	@Override
	public void visit(Lambda lambda) {
		boundVariables.add(lambda.getArgument());
		lambda.getBody().accept(this);
		boundVariables.remove(lambda.getArgument());

		if (result == null) {
			return;
		} else if (result != lambda.getBody()) {
			result = new Lambda(lambda.getArgument(), result);
		} else {
			result = lambda;
		}
	}

	@Override
	public void visit(Literal literal) {
		final int len = literal.numArgs();

		if (literal.numFreeVariables() == 0) {
			result = literal;
			return;
		} else if (AMRServices.isSkolemTerm(literal)) {
			// If skolem term, visit its body, if it returns null, return null;
			if (len == 2) {
				literal.getArg(1).accept(this);
				result = result == null ? null
						: new Literal(literal.getPredicate(),
								ArrayUtils.create(literal.getArg(0), result));
			} else {
				result = null;
			}
		} else if (literal.getPredicate()
				.equals(LogicLanguageServices.getConjunctionPredicate())) {
			// If conjunction, visit each conjunct, dropping these that return
			// null. Create a conjunction from the remaining ones, if there's a
			// single remaining one, return it, if non are left, return null.
			final List<LogicalExpression> newArgs = new ArrayList<>(len);
			// Store opN literals separately. We later will modify their number
			// to make them consistent.
			final List<LogicalExpression> opArgs = new ArrayList<>();
			boolean typingLiteralObserved = false;
			for (int i = 0; i < len; ++i) {
				literal.getArg(i).accept(this);
				if (result != null) {
					if (result instanceof Literal
							&& ((Literal) result).numArgs() == 2
							&& AMRServices.isOpPredicate(
									((Literal) result).getPredicate())) {
						opArgs.add(result);
					} else {
						newArgs.add(result);
						if (result instanceof Literal
								&& ((Literal) result).numArgs() == 1) {
							typingLiteralObserved = true;
						}
					}
				}
			}

			// Resort the c_op literals and rename their predicates to keep them
			// consistent and in order.
			if (!opArgs.isEmpty()) {
				opArgs.sort((l1, l2) -> Integer.compare(
						AMRServices.opPredicateToInteger(
								((Literal) l1).getPredicate()),
						AMRServices.opPredicateToInteger(
								((Literal) l2).getPredicate())));
				for (int i = 0; i < opArgs.size(); ++i) {
					final Literal original = (Literal) opArgs.get(i);
					newArgs.add(
							new Literal(
									AMRServices.integerToOpPredicate(i + 1,
											original.getPredicateType()),
							original));
				}
			}

			if (!typingLiteralObserved) {
				// If we have a single argument, its second argument is a skolem
				// term, return the truth-typed body of that term.
				if (newArgs.size() == 1 && newArgs.get(0) instanceof Literal
						&& ((Literal) newArgs.get(0)).numArgs() == 2
						&& AMRServices.isSkolemTerm(
								((Literal) newArgs.get(0)).getArg(1))) {
					// Do application to get the right variable in the body.
					result = ApplyAndSimplify.of(
							((Literal) ((Literal) newArgs.get(0)).getArg(1))
									.getArg(1),
							((Literal) newArgs.get(0)).getArg(0));
					return;
				}
				if (newArgs.size() > 0 && newArgs.get(0) instanceof Literal
						&& ((Literal) newArgs.get(0)).numArgs() > 1
						&& ((Literal) newArgs.get(0))
								.getArg(0) instanceof Variable) {
					// If we have multiple relations, but not the type, create a
					// fake one.
					newArgs.add(0,
							new Literal(dummyTypingPredicate, ArrayUtils.create(
									(Variable) ((Literal) newArgs.get(0))
											.getArg(0))));
				} else {
					result = null;
					return;
				}
			}

			if (newArgs.isEmpty()) {
				result = null;
			} else if (newArgs.size() > 1) {
				result = new Literal(literal.getPredicate(),
						newArgs.toArray(new LogicalExpression[newArgs.size()]));
			} else if (newArgs.size() == 1) {
				result = newArgs.get(0);
			}
		} else {
			final LogicalExpression[] newArgs = new LogicalExpression[len];
			for (int i = 0; i < len; ++i) {
				literal.getArg(i).accept(this);
				if (result == null) {
					return;
				}
				newArgs[i] = result;
			}

			if (literal.getPredicate() instanceof Variable
					&& !boundVariables.contains(literal.getPredicate())) {
				result = null;
			} else {
				literal.getPredicate().accept(this);
				if (result != null) {
					result = new Literal(result, newArgs);
				}
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		// Remove dummy constants.
		if (logicalConstant.equals(AMRServices.getDummyEntity())) {
			result = null;
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
		if (!boundVariables.contains(variable)
				&& !variable.getType().isComplex()) {
			result = null;
		} else {
			result = variable;
		}
	}

	private static class GetTopMostEntities
			implements ILogicalExpressionVisitor {

		private final List<Literal> topMostEntities = new LinkedList<>();

		@Override
		public void visit(Lambda lambda) {
			lambda.getBody().accept(this);
		}

		@Override
		public void visit(Literal literal) {
			if (literal.numFreeVariables() == 0
					&& AMRServices.isSkolemTerm(literal)) {
				topMostEntities.add(literal);
			} else {
				literal.getPredicate().accept(this);
				final int len = literal.numArgs();
				for (int i = 0; i < len; ++i) {
					literal.getArg(i).accept(this);
				}
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			// Nothing to do.
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do.
		}

	}

}
