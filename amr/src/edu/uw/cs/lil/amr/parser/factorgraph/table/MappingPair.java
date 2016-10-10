package edu.uw.cs.lil.amr.parser.factorgraph.table;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;

public class MappingPair {

	private final INode				node;
	private final LogicalExpression	value;

	private MappingPair(INode node, LogicalExpression value) {
		this.node = node;
		this.value = value;
	}

	public static MappingPair of(INode node, LogicalExpression value) {
		return new MappingPair(node, value);
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
		final MappingPair other = (MappingPair) obj;
		if (node == null) {
			if (other.node != null) {
				return false;
			}
		} else if (!node.equals(other.node)) {
			return false;
		}
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		} else if (!value.equals(other.value)) {
			return false;
		}
		return true;
	}

	public INode getNode() {
		return node;
	}

	public LogicalExpression getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (node == null ? 0 : node.hashCode());
		result = prime * result + (value == null ? 0 : value.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "MappingPair [node=" + node + ", value=" + value + "]";
	}

}
