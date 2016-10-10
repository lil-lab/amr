package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Given a mapping of {@link INode} to {@link LogicalExpression} assignments,
 * compute the log product of marginals, while marginalizing over all
 * un-assigned nodes. Assumes all belief are computed for the input
 * {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class MarginalsProduct implements IFactorGraphVisitor {

	private final Map<INode, LogicalExpression>	mapping;
	private double								marginalProduct	= 0.0;

	public MarginalsProduct(Map<INode, LogicalExpression> mapping) {
		this.mapping = mapping;
	}

	public static double of(FactorGraph graph,
			Map<INode, LogicalExpression> mapping) {
		final MarginalsProduct visitor = new MarginalsProduct(mapping);
		visitor.visit(graph.getRoot());
		return visitor.marginalProduct;
	}

	@Override
	public void visit(LogicalConstantNode node) {
		marginalProduct += node.getBelief().get(mapping);
	}

	@Override
	public void visit(SkolemIdNode node) {
		marginalProduct += node.getBelief().get(mapping);
	}

}
