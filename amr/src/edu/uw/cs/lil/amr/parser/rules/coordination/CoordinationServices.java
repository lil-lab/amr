package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jregex.Pattern;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax.Unification;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.language.type.ComplexType;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Service class for coordination rules.
 *
 * @author Yoav Artzi
 */
public class CoordinationServices {
	public static final ILogger			LOG			= LoggerFactory
															.create(CoordinationServices.class);
	private static CoordinationServices	INSTANCE	= null;

	private final Pattern				argmentPredicatePattern;

	/**
	 * The base name for argument predicates (i.e., c_op for c_op1, c_op2, c_op3
	 * and so on).
	 */
	private final String				argumentPredicateBaseName;

	/**
	 * AMR unary instance predicate for conjunctions.
	 */
	private final LogicalConstant		entityConjunctionInstance;

	/**
	 * AMR unary instance predicate for disjunctions.
	 */
	private final LogicalConstant		entityDisjunctionInstance;

	private final Syntax				pluralNounPhraseSyntax;

	/**
	 * The base name of the constant used in the lexicon to indicate
	 * conjunctions.
	 */
	protected final String				conjunctionLabel;
	/**
	 * The base name of the constant used in the lexicon to indicate
	 * disjunctions.
	 */
	protected final String				disjunctionLabel;

	public CoordinationServices(String argumentPredicateBaseName,
			LogicalConstant entityConjunctionInstance,
			LogicalConstant entityDisjunctionInstance, String conjunctionLabel,
			String disjunctionLabel, Syntax pluralNounPhraseSyntax) {
		this.argumentPredicateBaseName = argumentPredicateBaseName;
		this.entityConjunctionInstance = entityConjunctionInstance;
		this.entityDisjunctionInstance = entityDisjunctionInstance;
		this.conjunctionLabel = conjunctionLabel;
		this.disjunctionLabel = disjunctionLabel;
		this.pluralNounPhraseSyntax = pluralNounPhraseSyntax;
		this.argmentPredicatePattern = new Pattern(argumentPredicateBaseName
				+ "\\d+");
	}

	/**
	 * Create an AMR coordination, e.g., (lambda $0:e (and:<t*,t> (C:<e,t> $0)
	 * (c_op1:<e,<e,t>> $0 a1) ... (c_opn:<e,<e,t>> $0 an))).
	 *
	 * @param coordinatedElements
	 *            The entities being coordinated a1,...,an, where the type of
	 *            each ai is baseType.
	 * @param baseType
	 *            The type of all coordinated entities.
	 * @param coordinationWrapper
	 *            The wrapping predicate that packed this coordination, as
	 *            initialized by {@link CoordinationC1Rule}. C is inferred from
	 *            this wrapper.
	 * @return <e,t>-typed logical expression.
	 */
	public static LogicalExpression createCoordination(
			LogicalExpression[] coordinatedElements, Type baseType,
			LogicalConstant coordinationWrapper) {
		// Create coordination variable.
		final Variable coordinationVariable = new Variable(
				LogicLanguageServices.getTypeRepository().getEntityType());

		final LogicalExpression[] conjunctionElements = new LogicalExpression[coordinatedElements.length + 1];

		// Wrap each coordinated element with a c_op predicate and get the
		// common type.
		Type commonType = null;
		for (int i = 0; i < coordinatedElements.length; ++i) {
			final LogicalExpression element = coordinatedElements[i];
			if (commonType == null || element.getType().isExtending(commonType)) {
				commonType = element.getType();
			} else if (!commonType.isExtending(element.getType())) {
				return null;
			}
			conjunctionElements[i + 1] = new Literal(createpCOpPredicate(i + 1,
					baseType), ArrayUtils.create(coordinationVariable, element));
		}

		/*
		 * Create the coordination instance, the conjunction of all the c_op
		 * literals and the instance unary literal.
		 */
		final LogicalConstant predicate = getCoordinationPredicate(
				coordinationWrapper.getBaseName(), commonType);
		if (predicate == null) {
			return null;
		}
		conjunctionElements[0] = new Literal(predicate,
				ArrayUtils.create(coordinationVariable));

		return new Lambda(coordinationVariable, new Literal(
				LogicLanguageServices.getConjunctionPredicate(),
				conjunctionElements));
	}

	/**
	 * Given a list of arguments, a type and a predicate base name create a
	 * literal. The type of the predicate is <type,...,<type,type>...>.
	 */
	public static Literal createLiteral(String name,
			LogicalExpression[] arguments, Type type) {

		// Create the type of the predicate.
		ComplexType predicateType = LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded(type, type);
		for (int i = 1; i < arguments.length; i++) {
			predicateType = LogicLanguageServices.getTypeRepository()
					.getTypeCreateIfNeeded(predicateType, type);
		}

		return new Literal(LogicalConstant.create(name, predicateType, true),
				arguments);
	}

	/**
	 * Dynamically create c_op predicates, e.g., c_op1:<e,<e,t>>,
	 * c_op2:<e,<i,t>> and so on.
	 */
	public static LogicalConstant createpCOpPredicate(int num, Type argType) {
		final String baseName = INSTANCE.argumentPredicateBaseName + num;
		final Type type = LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded(
						LogicLanguageServices.getTypeRepository()
								.getTypeCreateIfNeeded(
										LogicLanguageServices
												.getTypeRepository()
												.getTruthValueType(), argType),
						LogicLanguageServices.getTypeRepository()
								.getEntityType());
		return LogicalConstant.create(baseName, type, true);
	}

	public static String getConjunctionLabel() {
		return INSTANCE.conjunctionLabel;
	}

	/**
	 * Given a coordination skolem term, return all the coordinated elements
	 * (i.e., the second arguments in all c_opX binary literals in the upper
	 * most conjunction).
	 */
	public static List<LogicalExpression> getCoordinatedItems(
			Literal coordination) {
		final LogicalExpression coordinationBody = ((Lambda) coordination
				.getArg(1)).getBody();
		if (coordinationBody instanceof Literal
				&& LogicLanguageServices
						.isCoordinationPredicate(((Literal) coordinationBody)
								.getPredicate())) {
			final Literal coordinationBodyLiteral = (Literal) coordinationBody;
			final int coordinationBodyLiteralNumArgs = coordinationBodyLiteral
					.numArgs();
			final List<LogicalExpression> items = new ArrayList<>(
					coordinationBodyLiteralNumArgs);
			for (int i = 0; i < coordinationBodyLiteralNumArgs; ++i) {
				final LogicalExpression arg = coordinationBodyLiteral.getArg(i);
				if (arg instanceof Literal
						&& ((Literal) arg).numArgs() == 2
						&& ((Literal) arg).getPredicate() instanceof LogicalConstant
						&& isCOpPredicate((LogicalConstant) ((Literal) arg)
								.getPredicate())) {
					items.add(((Literal) arg).getArg(1));
				}
			}
			return items;
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * Given a coordination predicate, as initiated by
	 * {@link CoordinationC1Rule}, return the appropriate coordination instance
	 * of conjunction predicate.
	 *
	 * @param coordinationWrapper
	 *            Coordination predicate, as initiated by
	 *            {@link CoordinationC1Rule}.
	 * @param coordinatedType
	 *            The type of the coordinated items.
	 */
	public static LogicalConstant getCoordinationPredicate(
			String coordinationWrapperBaseName, Type coordinatedType) {
		if (coordinatedType.equals(LogicLanguageServices.getTypeRepository()
				.getTruthValueType())) {
			return coordinationWrapperBaseName
					.equals(INSTANCE.conjunctionLabel) ? LogicLanguageServices
					.getConjunctionPredicate() : LogicLanguageServices
					.getDisjunctionPredicate();
		} else if (coordinatedType.equals(LogicLanguageServices
				.getTypeRepository().getEntityType())) {
			return coordinationWrapperBaseName
					.equals(INSTANCE.conjunctionLabel) ? INSTANCE.entityConjunctionInstance
					: INSTANCE.entityDisjunctionInstance;
		} else {
			LOG.debug("Unsupported coordinationw wrapper type: %s with %s",
					coordinationWrapperBaseName, coordinatedType);
			return null;
		}
	}

	/**
	 * Checks if the given constant is a coordination typing predicate (i.e.,
	 * and:<e,t> or or:<e,t>).
	 */
	public static boolean isAmrCoordinationPredicate(LogicalConstant predicate) {
		return predicate.equals(INSTANCE.entityConjunctionInstance)
				|| predicate.equals(INSTANCE.entityDisjunctionInstance);
	}

	public static boolean isCoordinator(LogicalConstant constant) {
		return constant.getBaseName().equals(INSTANCE.conjunctionLabel)
				|| constant.getBaseName().equals(INSTANCE.disjunctionLabel);
	}

	public static boolean isCOpPredicate(LogicalConstant predicate) {
		return INSTANCE.argmentPredicatePattern
				.matches(predicate.getBaseName());
	}

	public static void set(CoordinationServices services) {
		INSTANCE = services;
	}

	/**
	 * Validate the semantics of two categories as CX arguments.
	 */
	private static boolean validSemantics(LogicalExpression left,
			LogicalExpression right) {
		return left.getType().equals(
				LogicLanguageServices.getTypeRepository().generalizeType(
						right.getType()))
				&& right instanceof Literal
				&& ((Literal) right).getPredicate() instanceof LogicalConstant;
	}

	/**
	 * Validate that left is of form Y : a and the right is of form C[X] : b,
	 * where Y and X can be unified and a and b are valid semantics for
	 * coordination according to
	 * {@link #validSemantics(LogicalExpression, LogicalExpression)}. If valid,
	 * return the syntax C[Z], where Z is the unification of X and Y.
	 */
	protected static CoordinationSyntax unifyCategories(
			Category<LogicalExpression> left, Category<LogicalExpression> right) {
		final CoordinationSyntax unifiedSyntax = unifySyntax(left.getSyntax(),
				right.getSyntax());
		if (unifiedSyntax != null
				&& validSemantics(left.getSemantics(), right.getSemantics())) {
			return unifiedSyntax;
		} else {
			return null;
		}
	}

	/**
	 * Validate that the right syntax is C[X] and left is Y, such that X and Y
	 * may be unified. Return C[Z] where Z is the unification of X and Y.
	 */
	protected static CoordinationSyntax unifySyntax(Syntax left, Syntax right) {
		if (right instanceof CoordinationSyntax
				&& !(left instanceof CoordinationSyntax)) {
			final Syntax coordinatedSyntax = ((CoordinationSyntax) right)
					.getCoordinatedSyntax();
			if (coordinatedSyntax.unify(Syntax.NP) != null
					&& left.unify(Syntax.NP) != null) {
				// Special case for NPs. We can coordinate NPs with different
				// attributes. For example, we can coordinate singular and
				// plural NPs.
				return new CoordinationSyntax(INSTANCE.pluralNounPhraseSyntax);
			}

			final Unification unification = coordinatedSyntax.unify(left);
			if (unification != null) {
				return new CoordinationSyntax(unification.getUnifiedSyntax());
			}
		}
		return null;
	}
}
