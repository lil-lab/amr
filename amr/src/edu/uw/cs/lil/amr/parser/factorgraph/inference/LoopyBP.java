package edu.uw.cs.lil.amr.parser.factorgraph.inference;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Edge;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetEdges;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetFactors;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetVariables;

public class LoopyBP {
	public static final ILogger	LOG	= LoggerFactory.create(LoopyBP.class);

	public static void of(FactorGraph graph, double changeThreashold,
			int maxIterations) {
		of(graph, changeThreashold, maxIterations, null);
	}

	public static void of(FactorGraph graph, double changeThreashold,
			int maxIterations, Long maxTime) {
		final long startTime = System.currentTimeMillis();

		// Get all the edges in the graph into an array for fast access.
		final Set<Edge> edgeSet = GetEdges.of(graph);
		final Edge[] edges = edgeSet.toArray(new Edge[edgeSet.size()]);

		// Init all messages to 1.0.
		StreamSupport.stream(
				Spliterators.<Edge> spliterator(edges, Spliterator.IMMUTABLE),
				true).forEach(edge -> {
			edge.getToFactorMessage().setAll(1.0);
			edge.getToVariableMessage().setAll(1.0);
		});

		boolean converged = false;
		int iteration = 0;
		while (!converged) {
			// Update all variable-to-factor messages.
			Integer numChanged = StreamSupport
					.stream(Spliterators.<Edge> spliterator(edges,
							Spliterator.IMMUTABLE), true)
					.map((Edge edge) -> {
						final Table table = edge.getToFactorMessage()
								.cloneEmpty();

						final INode variable = edge.getVariable();
						final Map<INode, LogicalExpression> valueMapping = new HashMap<>();
						final int numAssignments = variable.numAssignments();
						final int numEdges = variable.numEdges();
						for (int incomingEdgeIndex = 0; incomingEdgeIndex < numEdges; ++incomingEdgeIndex) {
							final Edge incomingEdge = variable
									.getEdge(incomingEdgeIndex);
							// Skip the current edge.
							if (incomingEdge != edge) {
								final Table incomingTable = incomingEdge
										.getToVariableMessage();
								for (int assignmentIndex = 0; assignmentIndex < numAssignments; ++assignmentIndex) {
									final LogicalExpression assignment = variable
											.getAssignment(assignmentIndex);
									// Set the mapping to marginalize
									// everything, for
									// all lines with the current
									// assignment
									// for the
									// variable.
									valueMapping.put(variable, assignment);
									// Multiply by the marginalized
									// value
									// (marginalized
									// over all variables except the one
									// of
									// this edge).
									// Since the tables are in
									// log-space,
									// instead of
									// multiplication, simply add the
									// value.
									table.add(valueMapping,
											incomingTable.get(valueMapping));
								}
							}
						}
						table.normalize();
						final boolean changed = !table.equals(
								edge.getToFactorMessage(), changeThreashold);

						edge.setToFactorMessage(table);
						return changed;
					})
					.collect(
							Collectors
									.summingInt((Boolean changed) -> (changed ? 1
											: 0)));

			// Update all factor-to-variables messages.
			numChanged += StreamSupport
					.stream(Spliterators.<Edge> spliterator(edges,
							Spliterator.IMMUTABLE), true)
					.map((Edge edge) -> {

						final INode variable = edge.getVariable();
						final IFactor factor = edge.getFactor();

						// Create a table to aggregate the product before
						// marginalization (sum).
						final Table productTable = factor.getTable().clone();

						// Multiply each incoming message into the product
						// table.
						final Map<INode, LogicalExpression> valueMapping = new HashMap<>();
						final int numEdges = factor.numEdges();
						for (int incomingEdgeIndex = 0; incomingEdgeIndex < numEdges; ++incomingEdgeIndex) {
							final Edge incomingEdge = factor
									.getEdge(incomingEdgeIndex);
							// Skip the current edge.
							if (incomingEdge != edge) {
								final Table incomingTable = incomingEdge
										.getToFactorMessage();
								valueMapping.clear();
								final int numAssignments = incomingEdge
										.getVariable().numAssignments();
								for (int assignmentIndex = 0; assignmentIndex < numAssignments; ++assignmentIndex) {
									final LogicalExpression assignment = incomingEdge
											.getVariable().getAssignment(
													assignmentIndex);
									valueMapping.put(
											incomingEdge.getVariable(),
											assignment);
									// Use addition instead of
									// multiplication since all
									// calculation is in log-space.
									productTable.add(valueMapping,
											incomingTable.get(valueMapping));
								}
							}
						}

						final Table table = edge.getToVariableMessage()
								.cloneEmpty();

						// Marginalize all variables except the current one
						// (the target
						// of this edge).
						valueMapping.clear();
						final int numAssignments = variable.numAssignments();
						for (int k = 0; k < numAssignments; ++k) {
							final LogicalExpression assignment = variable
									.getAssignment(k);
							valueMapping.put(variable, assignment);
							table.set(valueMapping,
									productTable.get(valueMapping));
						}
						table.normalize();

						final boolean changed = !table.equals(
								edge.getToVariableMessage(), changeThreashold);

						edge.setToVariableMessage(table);
						return changed;
					})
					.collect(
							Collectors
									.summingInt((Boolean changed) -> (changed ? 1
											: 0)));

			if (numChanged == 0) {
				converged = true;
				LOG.info("LBP converged after %d iterations", iteration);
			} else if (maxTime != null
					&& System.currentTimeMillis() - startTime > maxTime) {
				LOG.info("LBP timed out after %d iterations (time: %d)",
						iteration, System.currentTimeMillis() - startTime);
				break;
			} else if (++iteration > maxIterations) {
				LOG.info(
						"LBP reached maximum number of iterations (%d) without convergence",
						maxIterations);
				break;
			}

		}

		// Set the belief of each variable node.
		GetVariables
				.of(graph)
				.parallelStream()
				.forEach((INode variable) -> {
					// The final belief is going to be a normalized
					// distribution, so not
					// in log-space. However, some of the computation ahead
					// are
					// in
					// log-space. The values will be normalized and
					// exponentiated at the
					// end.
						final Table variableBelief = new Table(true, variable
								.getColumnHeader());
						// Set initial values to log(1.0).
						variableBelief.setAll(0.0);
						final Map<INode, LogicalExpression> mapping = new HashMap<>();
						final int len = variable.numEdges();
						for (int j = 0; j < len; ++j) {
							final Edge edge = variable.getEdge(j);
							final int numAssignments = variable
									.numAssignments();
							for (int k = 0; k < numAssignments; ++k) {
								final LogicalExpression assignment = variable
										.getAssignment(k);
								mapping.put(variable, assignment);
								// Multiplication in log-space, so
								// summation.
								variableBelief.add(mapping, edge
										.getToVariableMessage().get(mapping));
							}
						}

						// Normalize. TODO do we need this?
						variableBelief.normalize();

						variable.setBelief(variableBelief);
					});

		// Set the belief of each factor node.
		GetFactors
				.of(graph)
				.parallelStream()
				.forEach(
						(factor) -> {
							final FactorTable factorBelief = factor.getTable()
									.clone();
							final int len = factor.numEdges();
							for (int j = 0; j < len; ++j) {
								final Edge edge = factor.getEdge(j);
								final Table toFactorMessage = edge
										.getToFactorMessage();
								final Map<INode, LogicalExpression> mapping = new HashMap<>();
								final INode variable = edge.getVariable();
								// Multiply in the incoming message.
								final int numAssignments = variable
										.numAssignments();
								for (int k = 0; k < numAssignments; ++k) {
									final LogicalExpression assignment = variable
											.getAssignment(k);
									mapping.put(variable, assignment);
									factorBelief.add(mapping,
											toFactorMessage.get(mapping));
								}
							}
							// Normalize. The belief is originally not
							// normalized due to the factor values being
							// multiplied in (all other elements are
							// normalized).
							factorBelief.normalize();

							factor.setBelief(factorBelief);
						});

		graph.setHasMarginals(true);

		LOG.info("LBP time: %fsec (iterations=%d, %sconverged)",
				(System.currentTimeMillis() - startTime) / 1000.0, iteration,
				converged ? "" : "not ");
	}

}
