package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * {@link IBaseNode} for {@link Literal}. Allows setting child nodes.
 *
 * @author Yoav Artzi
 */
public class LiteralNode extends AbstractDummyNode {

	private final List<IBaseNode>	argNodes;

	private final Literal		literal;

	private final IBaseNode			predicateNode;

	public LiteralNode(Literal literal, AbstractDummyNode predicateNode,
			List<AbstractDummyNode> argNodes, int id) {
		super(id, SetUtils.union(SetUtils.createSingleton(predicateNode),
				argNodes));
		this.literal = literal;
		this.predicateNode = predicateNode;
		this.argNodes = Collections.unmodifiableList(new ArrayList<>(argNodes));
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
		final LiteralNode other = (LiteralNode) obj;
		if (literal == null) {
			if (other.literal != null) {
				return false;
			}
		} else if (!literal.equals(other.literal)) {
			return false;
		}
		return true;
	}

	public List<IBaseNode> getArgs() {
		return argNodes;
	}

	@Override
	public LogicalExpression getExpression() {
		return literal;
	}

	public Literal getLiteral() {
		return literal;
	}

	public IBaseNode getPredicate() {
		return predicateNode;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (literal == null ? 0 : literal.hashCode());
		return result;
	}

}
