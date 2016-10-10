package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.HashSet;
import java.util.Set;

import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Get a set of all the factors in from a {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class GetFactors implements IFactorGraphVisitor {

	private final Set<IFactor>	factors	= new HashSet<>();

	private GetFactors() {
		// Use static 'of' method.
	}

	public static Set<IFactor> of(FactorGraph graph) {
		final GetFactors visitor = new GetFactors();
		visitor.visit(graph.getRoot());
		return visitor.factors;
	}

	@Override
	public void visit(LogicalConstantNode node) {
		final int numEdges = node.numEdges();
		for (int i = 0; i < numEdges; ++i) {
			factors.add(node.getEdge(i).getFactor());
		}
	}

	@Override
	public void visit(SkolemIdNode node) {
		final int numEdges = node.numEdges();
		for (int i = 0; i < numEdges; ++i) {
			factors.add(node.getEdge(i).getFactor());
		}
	}

}
