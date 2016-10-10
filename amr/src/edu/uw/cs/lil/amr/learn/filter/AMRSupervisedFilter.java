package edu.uw.cs.lil.amr.learn.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.HasFreeVariables;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.utils.counter.Counter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.OverloadedLogicalConstant;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;

public class AMRSupervisedFilter implements
		IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> {

	public static final ILogger								LOG	= LoggerFactory
			.create(AMRSupervisedFilter.class);

	private final Predicate<ParsingOp<LogicalExpression>>	baseFilter;

	private final LogicalExpression							baseLabel;

	private final Map<RelationalPair, Counter>				baseRelationSecondArgPairs;
	private final Map<TypeRelatedTypeTriplet, Counter>		baseTypeRelatedTypeTriplets;
	private final Predicate<LogicalConstant>				constantFilter;
	private final LogicalExpression							label;

	public AMRSupervisedFilter(LogicalExpression baseLabel,
			LogicalExpression label,
			Predicate<ParsingOp<LogicalExpression>> baseFilter,
			Predicate<LogicalConstant> constantFilter) {
		this.baseLabel = StripOverload.of(baseLabel);
		this.label = StripOverload.of(label);
		this.baseFilter = baseFilter;
		this.constantFilter = constantFilter;

		// Get underspecified entities from the label.
		final CollectStats stats = new CollectStats(constantFilter);
		stats.visit(this.baseLabel);

		LOG.debug("Creating AMR supervised filter for base label: %s",
				this.baseLabel);
		LOG.debug("Instance type and related type pairs: %s",
				stats.typeRelatedTypeTriplets);
		LOG.debug("Relation predicate second arg type pairs: %s",
				stats.relationSecondArgPairs);

		this.baseTypeRelatedTypeTriplets = stats.typeRelatedTypeTriplets;
		this.baseRelationSecondArgPairs = stats.relationSecondArgPairs;
	}

	@Override
	public boolean test(ParsingOp<LogicalExpression> op) {

		if (!baseFilter.test(op)) {
			return false;
		}

		LOG.debug("AMR supervised filter, validating: %s", op);
		if (op.getCategory().getSemantics() != null) {
			final CollectStats statsVisitor = new CollectStats(constantFilter,
					baseRelationSecondArgPairs, baseTypeRelatedTypeTriplets);
			statsVisitor.visit(op.getCategory().getSemantics());
			return statsVisitor.isValid;
		} else {
			return true;
		}
	}

	@Override
	public boolean testResult(LogicalExpression result) {
		return label.equals(result);
	}

	@Override
	public boolean testStep(LogicalExpression step) {
		return baseLabel.equals(StripOverload.of(step));
	}

	/**
	 * All statistics and pruning is done on the raw logical constants, without
	 * any surface form and directionality overloading. Therefore, every access
	 * to a constant requires getting the base wrapped constant.
	 *
	 * @author Yoav Artzi
	 *
	 */
	private class CollectStats implements ILogicalExpressionVisitor {

		private final Predicate<LogicalConstant>			constantFilter;
		private boolean										isValid					= true;
		private final Map<RelationalPair, Counter>			relationSecondArgPairs	= new HashMap<>();
		private final Map<RelationalPair, Counter>			relationSecondArgPairsRef;
		private final Map<TypeRelatedTypeTriplet, Counter>	typeRelatedTypeTriplets	= new HashMap<>();
		private final Map<TypeRelatedTypeTriplet, Counter>	typeRelatedTypeTripletsRef;

		public CollectStats(Predicate<LogicalConstant> constantFilter) {
			this(constantFilter, null, null);
		}

		public CollectStats(Predicate<LogicalConstant> constantFilter,
				Map<RelationalPair, Counter> relationSecondArgPairsRef,
				Map<TypeRelatedTypeTriplet, Counter> typeRelatedTypeTripletsRef) {
			this.constantFilter = constantFilter;
			this.relationSecondArgPairsRef = relationSecondArgPairsRef;
			this.typeRelatedTypeTripletsRef = typeRelatedTypeTripletsRef;
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
			final int numArgs = literal.numArgs();
			final LogicalExpression predicate = literal
					.getPredicate() instanceof LogicalConstant
							? OverloadedLogicalConstant.getWrapped(
									(LogicalConstant) literal.getPredicate())
							: literal.getPredicate();
			if (numArgs == 2
					&& !LogicLanguageServices.isCoordinationPredicate(predicate)
					&& predicate instanceof LogicalConstant
					&& constantFilter.test((LogicalConstant) predicate)) {
				countRelationalPairs((LogicalConstant) predicate,
						literal.getArg(1));
				if (!isValid) {
					return;
				}
			}

			if (LogicLanguageServices.isCoordinationPredicate(predicate)) {
				// Try to construct the instance-type-related-type triplets as
				// much as possible. We are trying to do as early as possible,
				// even before the skolem term is closed.
				countInstanceTypeRelatedTypeTriplets(literal);
				if (!isValid) {
					return;
				}
			}

			predicate.accept(this);
			for (int i = 0; i < numArgs; ++i) {
				literal.getArg(i).accept(this);
				if (!isValid) {
					return;
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

		private <S> void count(S object, Map<S, Counter> counters,
				Map<S, Counter> reference) {
			final Counter counter = counters.get(object);
			Counter refCounter = null;
			if (counter == null) {
				// Case new object.
				if (reference == null || reference.containsKey(object)) {
					// Case the object is known to the reference map, so it must
					// have a value of at least 1. Add it to the current
					// counters map.
					counters.put(object, new Counter(1));
				} else {
					// Case the object is not known to the reference map, so
					// invalidate the expression.
					LOG.debug("Invalid - unexpected %s", object);
					isValid = false;
				}
			} else if (reference == null || reference.containsKey(object)
					&& (refCounter = reference.get(object)).value() > counter
							.value()) {
				// Case the reference is null, or the reference counter is
				// bigger than the current counter.
				counter.inc();
			} else {
				// Case object which is not included in the reference, so it
				// invalidates the expression. At this point, it's guaranteed
				// that the reference counter exists.
				LOG.debug(
						"Invalid - %s expected %d times, but observed %d times",
						object, refCounter == null ? -1 : refCounter.value(),
						counter.value() + 1);
				isValid = false;
			}
		}

		/**
		 * @see CollectStats#typeRelatedTypeTriplets
		 * @see TypeRelatedTypeTriplet
		 */
		private void countInstanceTypeRelatedTypeTriplets(
				Literal conjunctionLiteral) {
			// Can collect triplets only from conjunctions with multiple
			// conjuncts.
			final int conjLiteralNumArgs = conjunctionLiteral.numArgs();
			if (conjLiteralNumArgs > 1) {
				// Try to get the typing predicate if one exists.
				final LogicalConstant typingPredicate = OverloadedLogicalConstant
						.getWrapped(
								AMRServices.getTypingPredicateFromConjunction(
										conjunctionLiteral));
				if (typingPredicate == null
						|| !constantFilter.test(typingPredicate)) {
					return;
				}

				// For every binary relation, create a triplet if the second
				// argument has no free variables.
				for (int i = 0; i < conjLiteralNumArgs; ++i) {
					final LogicalExpression arg = conjunctionLiteral.getArg(i);
					if (arg instanceof Literal && ((Literal) arg).numArgs() == 2
							&& ((Literal) arg)
									.getPredicate() instanceof LogicalConstant
							&& !HasFreeVariables.of(((Literal) arg).getArg(1),
									true)) {
						final LogicalExpression arg2 = ((Literal) arg)
								.getArg(1);
						final LogicalConstant relation = OverloadedLogicalConstant
								.getWrapped((LogicalConstant) ((Literal) arg)
										.getPredicate());
						if (arg2 instanceof Literal
								&& AMRServices.isSkolemTerm((Literal) arg2)) {
							final LogicalConstant arg2TypingPredicate = OverloadedLogicalConstant
									.getWrapped(AMRServices.getTypingPredicate(
											(Literal) arg2));
							if (arg2TypingPredicate != null && constantFilter
									.test(arg2TypingPredicate)) {
								count(new TypeRelatedTypeTriplet(
										typingPredicate, relation,
										arg2TypingPredicate),
										typeRelatedTypeTriplets,
										typeRelatedTypeTripletsRef);
								if (!isValid) {
									return;
								}
							}
						} else if (arg2 instanceof LogicalConstant) {
							final LogicalConstant arg2Const = OverloadedLogicalConstant
									.getWrapped((LogicalConstant) arg2);
							if (constantFilter.test(arg2Const)) {
								count(new TypeRelatedTypeTriplet(
										typingPredicate, relation, arg2Const),
										typeRelatedTypeTriplets,
										typeRelatedTypeTripletsRef);
								if (!isValid) {
									return;
								}
							}
						} else if (arg2 instanceof Literal
								&& AMRServices.isRefPredicate(
										((Literal) arg2).getPredicate())) {
							count(new TypeRelatedTypeTriplet(typingPredicate,
									relation, ((Literal) arg2).getPredicate()),
									typeRelatedTypeTriplets,
									typeRelatedTypeTripletsRef);
							if (!isValid) {
								return;
							}
						}
					}
				}
			}
		}

		/**
		 * @param relation
		 *            A non-overloaded logical constant.
		 * @param arg2
		 *            The second argument of the relation.
		 */
		private void countRelationalPairs(LogicalConstant relation,
				LogicalExpression arg2) {
			// Try to collect the relation-second-arg pair, if possible.
			if (arg2 instanceof Literal
					&& AMRServices.isSkolemTerm((Literal) arg2)) {
				final LogicalConstant arg2TypingPredicate = OverloadedLogicalConstant
						.getWrapped(
								AMRServices.getTypingPredicate((Literal) arg2));
				if (arg2TypingPredicate != null) {
					if (constantFilter.test(arg2TypingPredicate)) {
						count(new RelationalPair(relation, arg2TypingPredicate),
								relationSecondArgPairs,
								relationSecondArgPairsRef);
						if (!isValid) {
							return;
						}
						if (CoordinationServices.isAmrCoordinationPredicate(
								arg2TypingPredicate)) {
							// Case this is a coordination instance, need to
							// create pairs with the typing predicate of each of
							// its coordinated items.
							for (final LogicalExpression item : CoordinationServices
									.getCoordinatedItems((Literal) arg2)) {
								countRelationalPairs(relation, item);
								if (!isValid) {
									return;
								}
							}
						}
					}
				}
			} else if (arg2 instanceof LogicalConstant) {
				final LogicalConstant arg2Const = OverloadedLogicalConstant
						.getWrapped((LogicalConstant) arg2);
				if (constantFilter.test(arg2Const)) {
					count(new RelationalPair(relation, arg2Const),
							relationSecondArgPairs, relationSecondArgPairsRef);
					if (!isValid) {
						return;
					}
				}
			} else if (arg2 instanceof Literal && AMRServices
					.isRefPredicate(((Literal) arg2).getPredicate())) {
				count(new RelationalPair(relation,
						((Literal) arg2).getPredicate()),
						relationSecondArgPairs, relationSecondArgPairsRef);
				if (!isValid) {
					return;
				}
			}
		}
	}

	/**
	 * Pair of: (1) relation predicate and (2) a logical expression representing
	 * the instance in the second argument. The second item in the triplet may
	 * be: the typing predicate of the instance, the REF predicate if it's a
	 * reference or a logical constant if it's such.
	 */
	private static class RelationalPair {
		private final LogicalExpression	arg2;
		private final int				hashCode;
		private final LogicalConstant	relation;

		public RelationalPair(LogicalConstant relation,
				LogicalExpression arg2) {
			assert !(relation instanceof OverloadedLogicalConstant) : "All statistics are collected over raw constants";
			this.relation = relation;
			this.arg2 = arg2;
			this.hashCode = calcHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final RelationalPair other = (RelationalPair) obj;
			if (arg2 == null) {
				if (other.arg2 != null) {
					return false;
				}
			} else if (!arg2.equals(other.arg2)) {
				return false;
			}
			if (relation == null) {
				if (other.relation != null) {
					return false;
				}
			} else if (!relation.equals(other.relation)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return "RelationalPair [arg2=" + arg2 + ", relation=" + relation
					+ "]";
		}

		private int calcHashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (arg2 == null ? 0 : arg2.hashCode());
			result = prime * result
					+ (relation == null ? 0 : relation.hashCode());
			return result;
		}

	}

	/**
	 * Triplets of: (1) typing predicate for an instance (only when logical
	 * constant), (2) a relation of this instance with another and (3) a logical
	 * expression representing the other instance. The third item in the triplet
	 * may be: the typing predicate of the instance, the REF predicate if it's a
	 * reference or a logical constant if it's such.
	 */
	private static class TypeRelatedTypeTriplet {
		private final int				hashCode;
		private final LogicalExpression	relatedInstance;
		private final LogicalConstant	relationPredicate;
		private final LogicalConstant	typingPredicate;

		public TypeRelatedTypeTriplet(LogicalConstant typingPredicate,
				LogicalConstant relationPredicate,
				LogicalExpression relatedInstance) {
			assert !(typingPredicate instanceof OverloadedLogicalConstant) : "All statistics are collected over raw constants";
			assert !(relationPredicate instanceof OverloadedLogicalConstant) : "All statistics are collected over raw constants";
			this.typingPredicate = typingPredicate;
			this.relationPredicate = relationPredicate;
			this.relatedInstance = relatedInstance;
			this.hashCode = calcHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final TypeRelatedTypeTriplet other = (TypeRelatedTypeTriplet) obj;
			if (relatedInstance == null) {
				if (other.relatedInstance != null) {
					return false;
				}
			} else if (!relatedInstance.equals(other.relatedInstance)) {
				return false;
			}
			if (relationPredicate == null) {
				if (other.relationPredicate != null) {
					return false;
				}
			} else if (!relationPredicate.equals(other.relationPredicate)) {
				return false;
			}
			if (typingPredicate == null) {
				if (other.typingPredicate != null) {
					return false;
				}
			} else if (!typingPredicate.equals(other.typingPredicate)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return "TypeRelatedTypeTriplet [relatedInstance=" + relatedInstance
					+ ", relationPredicate=" + relationPredicate
					+ ", typingPredicate=" + typingPredicate + "]";
		}

		private int calcHashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (relatedInstance == null ? 0
					: relatedInstance.hashCode());
			result = prime * result + (relationPredicate == null ? 0
					: relationPredicate.hashCode());
			result = prime * result + (typingPredicate == null ? 0
					: typingPredicate.hashCode());
			return result;
		}

	}

}
