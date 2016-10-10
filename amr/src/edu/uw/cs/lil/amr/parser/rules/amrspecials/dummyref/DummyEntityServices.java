package edu.uw.cs.lil.amr.parser.rules.amrspecials.dummyref;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Services to handle dummy entities.
 *
 * @author Yoav Artzi
 */
public class DummyEntityServices {

	/**
	 * Given a lambda term (e.g., (lambda $0:e (and:<t*,t> ...))), return 'true'
	 * iff its body contains relation literals and at least one of them has a
	 * dummy entity.
	 */
	public static boolean hasDummyEntity(Lambda lambda) {
		if (lambda.getBody() instanceof Literal) {
			final Literal bodyLiteral = (Literal) lambda.getBody();
			if (LogicLanguageServices.getConjunctionPredicate().equals(
					bodyLiteral.getPredicate())) {
				final int len = bodyLiteral.numArgs();
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = bodyLiteral.getArg(i);
					if (arg instanceof Literal) {
						if (isRelationWithDummy((Literal) arg)) {
							return true;
						}
					}
				}
			} else if (isRelationWithDummy(bodyLiteral)) {
				return true;
			}
		}
		return false;

	}

	/**
	 * Given a skolem term (i.e., (a:<<e,t>,<id,e>> na:id (lambda $0:e ....))),
	 * return 'true' iff it has a relation to a dummy entity.
	 */
	public static boolean hasDummyEntity(Literal literal) {
		if (AMRServices.isSkolemTerm(literal) && literal.numArgs() == 2
				&& literal.getArg(1) instanceof Lambda) {
			return hasDummyEntity((Lambda) literal.getArg(1));
		} else {
			return false;
		}
	}

	/**
	 * Test if this literal depicts a relation with a dummy entity (i.e., of the
	 * form (pred:<e,<e,t>> k:e DUMMY:e)).
	 */
	public static boolean isRelationWithDummy(Literal literal) {
		return literal.numArgs() == 2
				&& literal.getPredicate() instanceof LogicalConstant
				&& literal.getArg(1).equals(AMRServices.getDummyEntity());
	}

	/**
	 * Given a lambda-term (e.g., (lambda $0:e (and:<t*,t> ... (pred:<e,<e,t>>
	 * DUMMY:e)))), removes the literal that includes the DUMMY:e entity as
	 * second argument, and returns its predicate. If the dummy entity appears
	 * more than once, fail.
	 *
	 * @return Pair of the stripped lambda term (in first position) and the
	 *         predicate of the relation with the stripped dummy argument
	 *         (second position).
	 */
	public static Pair<Lambda, LogicalConstant> stripDummy(Lambda lambda) {
		if (lambda.getBody() instanceof Literal) {
			final Literal literalBody = (Literal) lambda.getBody();
			if (literalBody.getPredicate().equals(
					LogicLanguageServices.getConjunctionPredicate())) {
				final int len = literalBody.numArgs();
				LogicalConstant strippedRelation = null;
				final LogicalExpression[] newArgs = new LogicalExpression[len - 1];
				int newArgIndex = 0;
				for (int i = 0; i < len && newArgIndex < len - 1; ++i) {
					final LogicalExpression arg = literalBody.getArg(i);
					if (arg instanceof Literal
							&& ((Literal) arg).numArgs() == 2
							&& ((Literal) arg).getArg(1).equals(
									AMRServices.getDummyEntity())) {
						if (strippedRelation == null
								&& ((Literal) arg).getPredicate() instanceof LogicalConstant) {
							strippedRelation = (LogicalConstant) ((Literal) arg)
									.getPredicate();
						} else {
							return null;
						}
					} else {
						newArgs[newArgIndex++] = arg;
					}
				}
				if (strippedRelation == null) {
					return null;
				} else if (newArgs.length == 1) {
					return Pair.of(
							new Lambda(lambda.getArgument(), newArgs[0]),
							strippedRelation);
				} else {
					return Pair.of(new Lambda(lambda.getArgument(),
							new Literal(literalBody.getPredicate(), newArgs)),
							strippedRelation);
				}
			}
		}
		return null;
	}

	/**
	 * Given a skolem term (i.e., (a:<<e,t>,<id,e>> na:id (lambda $0:e
	 * (and:<t*,t> .... (pred:<e,<e,t>> $0 DUMMY:e) .... )))), removes the
	 * literal that includes the DUMMY:e entity as second argument, and returns
	 * its predicate. If the dummy entity appears more than once, fail.
	 *
	 * @return Pair of stripped skolem term (in first position) and the
	 *         predicate of the relation with the stripped dummy argument
	 *         (second position).
	 */
	public static Pair<Literal, LogicalConstant> stripDummy(Literal literal) {
		if (AMRServices.isSkolemTerm(literal) && literal.numArgs() == 2
				&& literal.getArg(1) instanceof Lambda) {
			final Pair<Lambda, LogicalConstant> pair = stripDummy((Lambda) literal
					.getArg(1));
			if (pair != null) {
				return Pair.of(AMRServices.skolemize(pair.first()),
						pair.second());
			}
		}
		return null;
	}
}
