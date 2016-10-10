package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;

/**
 * Create a factor graph skeleton from a logical form. The graph representation
 * packs the logical form, each variables node includes all of its potential
 * assignments. However, no factor nodes are included. Elements from the logical
 * expression that are not represented in the graph are wrapped to allow easier
 * re-construction of the logical from from the graph.
 *
 * @author Yoav Artzi
 */
public class CreateFactorGraph implements ILogicalExpressionVisitor {
	public static final ILogger											LOG				= LoggerFactory
			.create(CreateFactorGraph.class);
	private final Function<LogicalExpression, Set<LogicalExpression>>	assignmentGenerator;
	private AbstractDummyNode											currentRoot		= null;
	private int															idCounter		= 0;
	private long														numAssignments	= 1;

	private CreateFactorGraph(
			Function<LogicalExpression, Set<LogicalExpression>> assignmentGenerator) {
		this.assignmentGenerator = assignmentGenerator;
	}

	public static FactorGraph of(LogicalExpression exp,
			Function<LogicalExpression, Set<LogicalExpression>> assignmentGenerator,
			boolean closure) {
		final CreateFactorGraph visitor = new CreateFactorGraph(
				assignmentGenerator);
		visitor.visit(exp);
		LOG.debug("Created factor graph with %d potential assignments",
				visitor.numAssignments);
		return new FactorGraph(visitor.currentRoot, closure);
	}

	@Override
	public void visit(Lambda lambda) {
		lambda.getArgument().accept(this);
		final AbstractDummyNode argumentNode = currentRoot;
		lambda.getBody().accept(this);
		final AbstractDummyNode bodyNode = currentRoot;
		currentRoot = new LambdaNode(lambda, argumentNode, bodyNode,
				idCounter++);
	}

	@Override
	public void visit(Literal literal) {
		literal.getPredicate().accept(this);
		final AbstractDummyNode predicateNode = currentRoot;
		final int numArgs = literal.numArgs();
		final List<AbstractDummyNode> argNodes = new ArrayList<>(numArgs);
		for (int i = 0; i < numArgs; ++i) {
			literal.getArg(i).accept(this);
			argNodes.add(currentRoot);
		}
		currentRoot = new LiteralNode(literal, predicateNode, argNodes,
				idCounter++);
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		final Set<LogicalExpression> potentialAssignments = assignmentGenerator
				.apply(logicalConstant);
		if (potentialAssignments.isEmpty()) {
			currentRoot = new LogicalConstantNode(logicalConstant, idCounter++);
		} else {
			numAssignments *= potentialAssignments.size();
			currentRoot = new LogicalConstantNode(logicalConstant,
					potentialAssignments.toArray(
							new LogicalExpression[potentialAssignments.size()]),
					idCounter++);
		}
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		if (variable instanceof SkolemId) {
			currentRoot = new SkolemIdNode((SkolemId) variable, idCounter++);
		} else {
			currentRoot = new VariableNode(variable, idCounter++);
		}
	}
}
