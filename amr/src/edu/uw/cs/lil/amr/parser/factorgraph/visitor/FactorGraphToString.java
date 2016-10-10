package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.Set;
import java.util.stream.Collectors;

import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Edge;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.VariableNode;

/**
 * Create a string representation (for logging) of a {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class FactorGraphToString implements IFactorGraphVisitor {

	private final StringBuilder	sb	= new StringBuilder();
	private final boolean		verbose;

	public FactorGraphToString(boolean verbose) {
		this.verbose = verbose;
	}

	public static String of(FactorGraph graph, boolean verbose) {
		final FactorGraphToString visitor = new FactorGraphToString(verbose);
		visitor.visit(graph.getRoot());

		// Append all factors.
		visitor.sb.append(GetFactors.of(graph).stream()
				.map((IFactor f) -> f.toString(verbose))
				.collect(Collectors.joining("\n")));

		// Append all edges.
		final Set<Edge> edges = GetEdges.of(graph);
		if (!edges.isEmpty()) {
			visitor.sb.append("\n");
			visitor.sb
					.append(edges.stream().map((edge) -> edge.toString(verbose))
							.collect(Collectors.joining("\n")));
		}

		return visitor.sb.toString();
	}

	@Override
	public void visit(LambdaNode node) {
		sb.append(node.toString(verbose)).append('\n');
		IFactorGraphVisitor.super.visit(node);
	}

	@Override
	public void visit(LiteralNode node) {
		sb.append(node.toString(verbose)).append('\n');
		IFactorGraphVisitor.super.visit(node);
	}

	@Override
	public void visit(LogicalConstantNode node) {
		sb.append(node.toString(verbose)).append('\n');
	}

	@Override
	public void visit(VariableNode node) {
		sb.append(node.toString(verbose)).append('\n');
	}

}
