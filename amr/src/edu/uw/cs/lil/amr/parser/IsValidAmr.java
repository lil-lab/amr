package edu.uw.cs.lil.amr.parser;

import java.util.HashSet;
import java.util.Set;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.HasFreeVariables;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Filter to prune parses that don't conform to AMR standards.
 *
 * @author Yoav Artzi
 */
public class IsValidAmr implements ILogicalExpressionVisitor {

	public static final ILogger	LOG		= LoggerFactory
			.create(IsValidAmr.class);
	private final boolean		complete;
	private final boolean		idsSet;
	private boolean				isValid	= true;

	private IsValidAmr(boolean idsSet, boolean complete) {
		// Use via 'of' method.
		this.idsSet = idsSet;
		this.complete = complete;
	}

	public static boolean of(LogicalExpression mr, boolean idsSet,
			boolean complete) {
		final IsValidAmr visitor = new IsValidAmr(idsSet, complete);
		visitor.visit(mr);
		if (!visitor.isValid) {
			LOG.debug("Non-standard AMR: %s", mr);
		}
		return visitor.isValid;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getArgument().accept(this);
		if (isValid) {
			lambda.getBody().accept(this);
		}
	}

	@Override
	public void visit(Literal literal) {
		// TODO limit where dummy entities can appear (only upper most
		// entity) and we can only have one at most. Dummy rules are currently
		// not used, but this should be fixed.

		if ((complete || (idsSet ? !HasFreeVariables.of(literal, true)
				: literal.numFreeVariables() == 0))
				&& AMRServices.isSkolemTerm(literal)
				&& literal.numArgs() == 2) {
			visitCompleteSkolemTerm(literal);
		} else {
			final int numArgs = literal.numArgs();
			if (LogicLanguageServices
					.isCoordinationPredicate(literal.getPredicate())) {
				// Verify there's at most one unary typing literal.
				boolean unaryLiteralSeen = false;
				final Set<Integer> observedOpPredicates = new HashSet<>();
				for (int i = 0; i < numArgs; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					if (arg instanceof Literal) {
						final Literal argLiteral = (Literal) arg;
						if (argLiteral.numArgs() == 1 && (idsSet
								? !HasFreeVariables
										.of(argLiteral.getPredicate(), true)
								: argLiteral.getPredicate()
										.numFreeVariables() == 0)) {
							if (unaryLiteralSeen) {
								isValid = false;
								LOG.debug(
										"Literal doesn't follow AMR standards - multiple typing predicates: %s",
										literal);
								return;
							} else {
								unaryLiteralSeen = true;
							}
						}

						if (argLiteral.numArgs() == 2 && argLiteral
								.getPredicate() instanceof LogicalConstant) {
							// If it's a opn predicate (e.g.,
							// c_op1:<e,<e,t>>, c_op2:<e,<e,t>> and so on),
							// verify we observe it only once and record its
							// number.
							final Integer n = AMRServices.opPredicateToInteger(
									argLiteral.getPredicate());
							if (n != null) {
								if (!observedOpPredicates.add(n)) {
									LOG.debug(
											"Invalid literal -- re-occurring op predicate (%s): %s",
											argLiteral.getPredicate(), literal);
									isValid = false;
									return;
								}
							}
						}
					}
				}
			}

			literal.getPredicate().accept(this);
			if (isValid) {
				for (int i = 0; i < numArgs; ++i) {
					literal.getArg(i).accept(this);
					if (!isValid) {
						return;
					}
				}
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		// Logical disjunctions never appear in converted AMRs.
		if (LogicLanguageServices.getDisjunctionPredicate()
				.equals(logicalConstant)) {
			isValid = false;
		}
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
	}

	private void visitCompleteSkolemTerm(Literal skolemTerm) {
		if (complete && skolemTerm.numFreeVariables() != 0
				&& (!idsSet || skolemTerm.getFreeVariables().stream()
						.filter(v -> !(v instanceof SkolemId)).count() != 0)) {
			LOG.debug(
					"Invalid skolem term -- complete LF but skolem term has free variables: %s",
					skolemTerm);
			isValid = false;
			return;
		}

		if (!(skolemTerm.getArg(1) instanceof Lambda)) {
			LOG.debug("Invalid skolem term -- non lambda in body: %s",
					skolemTerm);
			isValid = false;
			return;
		}

		final LogicalExpression termBody = ((Lambda) skolemTerm.getArg(1))
				.getBody();
		if (termBody instanceof Literal
				&& LogicLanguageServices.getConjunctionPredicate()
						.equals(((Literal) termBody).getPredicate())) {
			final Literal termBodyLiteral = (Literal) termBody;
			final int len = termBodyLiteral.numArgs();
			boolean unaryTypingLiteralFound = false;
			final Set<Integer> observedOpPredicates = new HashSet<>();
			int largestOpPredicateObserved = 0;
			for (int i = 0; i < len; ++i) {
				final LogicalExpression arg = termBodyLiteral.getArg(i);
				if (arg instanceof Literal) {
					final Literal argLiteral = (Literal) arg;
					final LogicalExpression argPredicate = argLiteral
							.getPredicate();

					if (!(argPredicate instanceof LogicalConstant)) {
						LOG.debug(
								"Invalid skolem term -- non constant in predicate position: %s",
								skolemTerm);
						isValid = false;
						return;
					}

					if (argLiteral.numArgs() == 1) {
						// Case unary typing literal.
						if (unaryTypingLiteralFound) {
							LOG.debug(
									"Invalid skolem term -- multiple unary typing literals: %s",
									skolemTerm);
							isValid = false;
							return;
						}
						unaryTypingLiteralFound = true;
						if (!(argLiteral.getArg(0) instanceof Variable)
								|| argLiteral.getArg(0) instanceof SkolemId) {
							LOG.debug(
									"Invalid skolem term -- typing predicate must take a variable as an argument: %s",
									skolemTerm);
							isValid = false;
							return;
						}
					} else if (argLiteral.numArgs() == 2) {
						// Case binary relational literal.

						// First argument must be a variable.
						if (!(argLiteral.getArg(0) instanceof Variable)
								|| argLiteral.getArg(0) instanceof SkolemId) {
							LOG.debug(
									"Invalid skolem term -- relational predicate must take a variable as its first argument: %s",
									skolemTerm);
							isValid = false;
							return;
						}

						// Second argument can be a constant, a reference or
						// a skolem term.
						final LogicalExpression arg2 = argLiteral.getArg(1);
						if (!(arg2 instanceof LogicalConstant)
								&& (!(arg2 instanceof Literal)
										|| !AMRServices.isRefPredicate(
												((Literal) arg2).getPredicate())
										&& !AMRServices.isSkolemTerm(
												(Literal) arg2))) {
							LOG.debug(
									"Invalid skolem term -- binary predicate with invalid second argument: %s",
									skolemTerm);
							isValid = false;
							return;
						}

						// If it's a opn predicate (e.g., c_op1:<e,<e,t>>,
						// c_op2:<e,<e,t>> and so on), verify we observe it
						// only once and record its number.
						final Integer n = AMRServices
								.opPredicateToInteger(argPredicate);
						if (n != null) {
							if (!observedOpPredicates.add(n)) {
								LOG.debug(
										"Invalid skolem term -- re-occurring op predicate (%s): %s",
										argPredicate, skolemTerm);
								isValid = false;
								return;
							}
							if (n > largestOpPredicateObserved) {
								largestOpPredicateObserved = n;
							}
						}

					} else {
						LOG.debug(
								"Invalid skolem term -- property literal with unexpected number of arguments (%d): %s",
								argLiteral.numArgs(), skolemTerm);
						isValid = false;
						return;
					}

					// Visit the argument.
					argLiteral.accept(this);
				} else {
					LOG.debug(
							"Invalid skolem term -- body must contain only literals: %s",
							skolemTerm);
					isValid = false;
					return;
				}
			}

			if (!unaryTypingLiteralFound) {
				LOG.debug(
						"Invalid skolem term -- no typing predicate found: %s",
						skolemTerm);
				isValid = false;
				return;
			}

			if (largestOpPredicateObserved != observedOpPredicates.size()) {
				LOG.debug(
						"Invalid skolem term -- invalid usage of opn predicates, largest=%d, observed=%s: %s",
						largestOpPredicateObserved, observedOpPredicates,
						skolemTerm);
				isValid = false;
				return;
			}
		} else {
			isValid = termBody instanceof Literal
					&& ((Literal) termBody).numArgs() == 1
					&& ((Literal) termBody).getArg(0) instanceof Variable;
			if (isValid) {
				termBody.accept(this);
			} else {
				LOG.debug(
						"Invalid skolem term -- single property and it's not a typing literal: %s",
						skolemTerm);
			}
		}
	}
}
