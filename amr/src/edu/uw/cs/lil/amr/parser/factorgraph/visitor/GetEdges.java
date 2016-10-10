package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.LinkedHashSet;
import java.util.Set;

import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Edge;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Get the set of all edges in a {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class GetEdges implements IFactorGraphVisitor {

	private final Set<Edge>	allEdges	= new LinkedHashSet<>();

	private GetEdges() {
		// Use static access method.
	}

	public static Set<Edge> of(FactorGraph graph) {
		final GetEdges visitor = new GetEdges();
		visitor.visit(graph.getRoot());
		return visitor.allEdges;
	}

	@Override
	public void visit(LogicalConstantNode node) {
		final int numEdges = node.numEdges();
		for (int i = 0; i < numEdges; ++i) {
			allEdges.add(node.getEdge(i));
		}
	}

	@Override
	public void visit(SkolemIdNode node) {
		final int numEdges = node.numEdges();
		for (int i = 0; i < numEdges; ++i) {
			allEdges.add(node.getEdge(i));
		}
	}

}
