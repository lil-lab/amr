package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.HashSet;
import java.util.Set;

import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Get the set of all {@link INode} of a {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class GetVariables implements IFactorGraphVisitor {

	private final Set<INode>	variables	= new HashSet<>();

	public static Set<INode> of(FactorGraph graph) {
		final GetVariables visitor = new GetVariables();
		visitor.visit(graph.getRoot());
		return visitor.variables;
	}

	@Override
	public void visit(LogicalConstantNode node) {
		variables.add(node);
	}

	@Override
	public void visit(SkolemIdNode node) {
		variables.add(node);
	}

}
