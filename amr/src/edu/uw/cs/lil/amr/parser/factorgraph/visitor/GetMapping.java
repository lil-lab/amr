package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.HashMap;
import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.lambda.InstanceClone;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Given a result, get the mapping of from {@link INode}s in this graph to
 * {@link LogicalExpression} assignments that will give the result.
 *
 * @author Yoav Artzi
 */
public class GetMapping implements IFactorGraphVisitor {

	private LogicalExpression					currentResult;
	private boolean								fail	= false;
	private final Map<INode, LogicalExpression>	mapping	= new HashMap<>();

	/**
	 * @param alignedResult
	 *            The final result. It must be aligned with the traversed graph.
	 */
	public GetMapping(LogicalExpression alignedResult) {
		this.currentResult = alignedResult;
	}

	public static Map<INode, LogicalExpression> of(FactorGraph graph,
			LogicalExpression result) {
		final LogicalExpression alignedResult = InstanceClone.of(graph
				.getRoot().getExpression(), result);

		if (alignedResult != null) {
			final GetMapping visitor = new GetMapping(alignedResult);
			visitor.visit(graph.getRoot());
			if (!visitor.fail) {
				return visitor.mapping;
			}
		}
		return null;
	}

	@Override
	public void visit(IBaseNode node) {
		node.accept(this);
	}

	@Override
	public void visit(LambdaNode node) {
		final Lambda resultLambda = (Lambda) currentResult;

		// Visit the argument.
		currentResult = resultLambda.getArgument();
		node.getArgument().accept(this);

		if (fail) {
			return;
		}

		// Visit the body.
		currentResult = resultLambda.getBody();
		node.getBody().accept(this);
	}

	@Override
	public void visit(LiteralNode node) {
		final Literal resultLiteral = (Literal) currentResult;

		// Predicate.
		currentResult = resultLiteral.getPredicate();
		node.getPredicate().accept(this);

		// Arguments.
		for (int i = 0; i < node.getArgs().size(); ++i) {
			if (fail) {
				return;
			}
			currentResult = resultLiteral.getArg(i);
			node.getArgs().get(i).accept(this);
		}

	}

	@Override
	public void visit(LogicalConstantNode node) {
		// Verify that that currentResult is a possible assignment.
		if (node.slowGetAssignments().contains(currentResult)) {
			mapping.put(node, currentResult);
		} else {
			fail = true;
		}
	}

	@Override
	public void visit(SkolemIdNode node) {
		if (node.slowGetAssignments().contains(currentResult)) {
			mapping.put(node, currentResult);
		} else {
			fail = true;
		}
	}
}
