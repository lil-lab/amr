package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

abstract class AbstractDummyNode implements IBaseNode {

	private final Set<IBaseNode>	children;
	private final int				id;
	private IBaseNode				parent;

	public AbstractDummyNode(int id, Set<AbstractDummyNode> children) {
		this.id = id;
		this.children = Collections.unmodifiableSet(children);
		// Set parents for all children.
		children.stream().forEach(c -> c.setParent(this));
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
		final AbstractDummyNode other = (AbstractDummyNode) obj;
		if (id != other.id) {
			return false;
		}
		return true;
	}

	@Override
	public final Set<IBaseNode> getChildren() {
		return children;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public final IBaseNode getParent() {
		return parent;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	@Override
	public String toString(boolean verbose) {
		return new StringBuilder("[ DUM ").append(internalToString())
				.append("]").toString();
	}

	private void setParent(IBaseNode node) {
		if (parent == null) {
			this.parent = node;
		} else {
			throw new IllegalStateException("parent already set");
		}
	}

	protected String internalToString() {
		final StringBuilder sb = new StringBuilder()
				.append(String.format("%3d", id)).append(" : ")
				.append(getExpression()).append(" : ");
		if (parent != null) {
			sb.append("parent=").append(parent.getId());
		}

		if (!children.isEmpty()) {
			sb.append(" children=").append(
					children.stream()
							.map((IBaseNode n) -> Integer.toString(n.getId()))
							.collect(Collectors.joining(",")));
		}
		return sb.toString();
	}

}
