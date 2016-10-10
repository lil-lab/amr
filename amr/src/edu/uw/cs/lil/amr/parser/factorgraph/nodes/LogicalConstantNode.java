package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Collections;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * {@link LogicalConstant} factor graph {@link INode}.
 *
 * @author Yoav Artzi
 */
public class LogicalConstantNode extends AbstractVariableNode {

	private final LogicalConstant	constant;

	public LogicalConstantNode(LogicalConstant logicalConstant, int id) {
		this(logicalConstant, ArrayUtils
				.<LogicalExpression> create(logicalConstant), id);
	}

	public LogicalConstantNode(LogicalConstant constant,
			LogicalExpression[] assignments, int id) {
		super(id, Collections.emptySet(), assignments);
		this.constant = constant;
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
		final LogicalConstantNode other = (LogicalConstantNode) obj;
		if (constant == null) {
			if (other.constant != null) {
				return false;
			}
		} else if (!constant.equals(other.constant)) {
			return false;
		}
		return true;
	}

	public LogicalConstant getConstant() {
		return constant;
	}

	@Override
	public LogicalExpression getExpression() {
		return constant;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (constant == null ? 0 : constant.hashCode());
		return result;
	}

}
