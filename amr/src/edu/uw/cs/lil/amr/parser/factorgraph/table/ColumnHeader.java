package edu.uw.cs.lil.amr.parser.factorgraph.table;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;

/**
 * A single column type in a factored graph table. Each node in the graph (non
 * {@link IFactor} objects) has a corresponding {@link ColumnHeader}.
 *
 * @author Yoav Artzi
 */
public class ColumnHeader {

	private final INode								node;
	private final int								numValues;

	private final Map<LogicalExpression, Integer>	valueIndex;

	public ColumnHeader(INode node) {
		this.node = node;
		final Map<LogicalExpression, Integer> indices = new HashMap<>();
		final int numAssignments = node.numAssignments();
		for (int i = 0; i < numAssignments; ++i) {
			indices.put(node.getAssignment(i), i);
		}
		this.valueIndex = Collections.unmodifiableMap(indices);
		this.numValues = numAssignments;
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
		final ColumnHeader other = (ColumnHeader) obj;
		if (node == null) {
			if (other.node != null) {
				return false;
			}
		} else if (!node.equals(other.node)) {
			return false;
		}
		return true;
	}

	public int getIndex(LogicalExpression value) {
		return valueIndex.get(value);
	}

	public INode getNode() {
		return node;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (node == null ? 0 : node.hashCode());
		return result;
	}

	public int numValues() {
		return numValues;
	}
}
