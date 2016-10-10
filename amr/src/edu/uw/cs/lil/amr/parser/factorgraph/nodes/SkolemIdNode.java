package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Collections;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * {@link SkolemId} {@link IBaseNode}.
 *
 * @author Yoav Artzi
 */
public class SkolemIdNode extends AbstractVariableNode {

	private SkolemId	skolemId;

	public SkolemIdNode(SkolemId skolemId, int id) {
		this(skolemId, id, ArrayUtils.<LogicalExpression> create(skolemId));
	}

	public SkolemIdNode(SkolemId skolemId, int id,
			LogicalExpression[] assignments) {
		super(id, Collections.emptySet(), assignments);
		this.skolemId = skolemId;
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
		final SkolemIdNode other = (SkolemIdNode) obj;
		if (skolemId == null) {
			if (other.skolemId != null) {
				return false;
			}
		} else if (!skolemId.equals(other.skolemId)) {
			return false;
		}
		return true;
	}

	@Override
	public LogicalExpression getExpression() {
		return skolemId;
	}

	public SkolemId getSkolemId() {
		return skolemId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (skolemId == null ? 0 : skolemId.hashCode());
		return result;
	}

	public void setSkolemId(SkolemId skolemId) {
		this.skolemId = skolemId;
	}

}
