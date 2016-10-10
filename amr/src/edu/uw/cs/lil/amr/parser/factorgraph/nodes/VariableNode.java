package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Collections;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Variable {@link IBaseNode}.
 *
 * @author Yoav Artzi
 */
public class VariableNode extends AbstractDummyNode {

	private final Variable	variable;

	public VariableNode(Variable variable, int id) {
		super(id, Collections.emptySet());
		this.variable = variable;
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
		final VariableNode other = (VariableNode) obj;
		if (variable == null) {
			if (other.variable != null) {
				return false;
			}
		} else if (!variable.equals(other.variable)) {
			return false;
		}
		return true;
	}

	@Override
	public LogicalExpression getExpression() {
		return variable;
	}

	public Variable getVariable() {
		return variable;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (variable == null ? 0 : variable.hashCode());
		return result;
	}

}
