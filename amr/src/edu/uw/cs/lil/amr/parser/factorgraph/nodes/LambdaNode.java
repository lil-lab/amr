package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Lambda {@link IBaseNode}.
 *
 * @author Yoav Artzi
 */
public class LambdaNode extends AbstractDummyNode {

	private final IBaseNode		argumentNode;

	private final IBaseNode		bodyNode;

	private final Lambda	lambda;

	public LambdaNode(Lambda lambda, AbstractDummyNode argumentNode,
			AbstractDummyNode bodyNode, int id) {
		super(id, SetUtils
				.<AbstractDummyNode> createSet(argumentNode, bodyNode));
		this.lambda = lambda;
		this.argumentNode = argumentNode;
		this.bodyNode = bodyNode;
	}

	@Override
	public void accept(IFactorGraphVisitor visitor) {
		visitor.visit(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final LambdaNode other = (LambdaNode) obj;
		if (lambda == null) {
			if (other.lambda != null) {
				return false;
			}
		} else if (!lambda.equals(other.lambda)) {
			return false;
		}
		return true;
	}

	public IBaseNode getArgument() {
		return argumentNode;
	}

	public IBaseNode getBody() {
		return bodyNode;
	}

	@Override
	public LogicalExpression getExpression() {
		return lambda;
	}

	public Lambda getLambda() {
		return lambda;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (lambda == null ? 0 : lambda.hashCode());
		return result;
	}

}
