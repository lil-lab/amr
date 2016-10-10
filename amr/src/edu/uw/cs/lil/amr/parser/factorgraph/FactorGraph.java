package edu.uw.cs.lil.amr.parser.factorgraph;

import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.FactorGraphToString;

/**
 * Factor graph for a logical form. Basically provides a pointer to the root
 * node (usually a {@link IBaseNode}).
 *
 * @author Yoav Artzi
 */
public class FactorGraph {

	private final boolean	closure;

	private boolean			hasMarginals	= false;

	private final IBaseNode	root;

	public FactorGraph(IBaseNode root, boolean closure) {
		assert root != null : "Graph must have a root";
		this.root = root;
		this.closure = closure;
	}

	public IBaseNode getRoot() {
		return root;
	}

	public boolean hasMarginals() {
		return hasMarginals;
	}

	public boolean isClosure() {
		return closure;
	}

	public void setHasMarginals(boolean hasMarginals) {
		this.hasMarginals = hasMarginals;
	}

	@Override
	public String toString() {
		return FactorGraphToString.of(this, true);
	}

}
