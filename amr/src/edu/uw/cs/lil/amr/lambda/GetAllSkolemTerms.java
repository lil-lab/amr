package edu.uw.cs.lil.amr.lambda;

import java.util.HashSet;
import java.util.Set;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;

/**
 * Get all literals that return true to
 * {@link AMRServices#isSkolemTerm(edu.cornell.cs.nlp.spf.mr.lambda.Literal)}.
 *
 * @author Yoav Artzi
 */
public class GetAllSkolemTerms implements ILogicalExpressionVisitor {

	private final boolean					shallow;
	private final Set<LogicalExpression>	skolemTerms	= new HashSet<>();

	private GetAllSkolemTerms(boolean shallow) {
		// Use of() method.
		this.shallow = shallow;
	}

	/**
	 * @param shallow
	 *            Don't visit extract skolem terms.
	 */
	public static Set<LogicalExpression> of(LogicalExpression exp,
			boolean shallow) {
		final GetAllSkolemTerms visitor = new GetAllSkolemTerms(shallow);
		visitor.visit(exp);
		return visitor.skolemTerms;
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getBody().accept(this);
	}

	@Override
	public void visit(Literal literal) {
		final boolean isSkolemTerm = AMRServices.isSkolemTerm(literal);
		if (isSkolemTerm) {
			skolemTerms.add(literal);
		}

		if (!shallow || !isSkolemTerm) {
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
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
	}

}
