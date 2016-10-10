package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.VariableNode;

/**
 * Given a configuration, which is a mapping of nodes to assignments, return the
 * {@link LogicalExpression}. If a variable is not specified, it will be
 * assigned its underspecified value.
 * 
 * @author Yoav Artzi
 */
public class GetExpression implements IFactorGraphVisitor {

	public static final ILogger					LOG		= LoggerFactory
																.create(GetExpression.class);

	private final Map<INode, LogicalExpression>	mapping;

	private LogicalExpression					result	= null;

	public GetExpression(Map<INode, LogicalExpression> mapping) {
		this.mapping = mapping;
	}

	public static LogicalExpression of(FactorGraph graph,
			Map<INode, LogicalExpression> mapping) {
		final GetExpression visitor = new GetExpression(mapping);
		visitor.visit(graph.getRoot());
		return visitor.result;
	}

	@Override
	public void visit(LambdaNode node) {
		node.getArgument().accept(this);
		final Variable argument = (Variable) result;
		node.getBody().accept(this);
		result = new Lambda(argument, result);
	}

	@Override
	public void visit(LiteralNode node) {
		node.getPredicate().accept(this);
		final LogicalExpression predicate = result;

		final LogicalExpression[] args = new LogicalExpression[node.getArgs()
				.size()];
		int i = 0;
		for (final IBaseNode child : node.getArgs()) {
			child.accept(this);
			args[i++] = result;
		}

		result = new Literal(predicate, args);
	}

	@Override
	public void visit(LogicalConstantNode node) {
		processAssignedNode(node);
	}

	@Override
	public void visit(SkolemIdNode node) {
		processAssignedNode(node);
	}

	@Override
	public void visit(VariableNode node) {
		result = node.getExpression();
	}

	private void processAssignedNode(INode node) {
		if (mapping.containsKey(node)) {
			result = mapping.get(node);
		} else {
			result = node.getExpression();
		}
	}
}
