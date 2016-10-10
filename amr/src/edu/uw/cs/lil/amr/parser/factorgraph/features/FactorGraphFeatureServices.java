/*******************************************************************************
 * Copyright (C) 2011 - 2015 Yoav Artzi, All rights reserved.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *******************************************************************************/
package edu.uw.cs.lil.amr.parser.factorgraph.features;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.collections.CollectionUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Edge;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Factor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.table.ColumnHeader;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

public class FactorGraphFeatureServices {

	public static final ILogger LOG = LoggerFactory
			.create(FactorGraphFeatureServices.class);

	/**
	 * Create a factor using the given feature function and connect it as
	 * required to the given nodes. The factor involves only the provided nodes.
	 * However, it won't be explicitly connected to dummy nodes.
	 *
	 * @param featureFunction
	 *            A function to generate the vector containing the feature. It
	 *            takes logical expression in the same order the nodes are
	 *            provided. This function doesn't modify the input array.
	 * @param weights
	 *            Model weights.
	 * @param nodes
	 *            The nodes connected to the factor. At least one of the nodes
	 *            must be a non-dummy node. Dummy nodes won't have explicit
	 *            edges to the new factor. A node can be null.
	 */
	public static void addFactor(
			BiFunction<LogicalExpression[], IBaseNode[], IHashVectorImmutable> featureFunction,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			String factorId, IBaseNode... nodes) {
		final int numNodes = nodes.length;

		final long startTime = System.currentTimeMillis();

		LOG.debug("Creating a factor between %s nodes ...", numNodes);

		// Create the table. Headers include only non-dummy nodes. Also, collect
		// the non-dummy nodes and create the indexing array.

		// Count the number of non-dummy nodes.
		int nonDummyCount = 0;
		for (int i = 0; i < numNodes; ++i) {
			if (nodes[i] instanceof INode) {
				++nonDummyCount;
			}
		}

		if (nonDummyCount <= 0) {
			throw new IllegalStateException(
					"Can't create a disconnected factor - at least one input node must be a variable (non-dummy) node.");
		}

		// Create the indexing array and collect the headers for the table.
		// Also, create the logical expression array to hand to the feature
		// function. The array includes only the values for the dummy nodes at
		// this point. The others will be set appropriate in the for loop below.
		final LogicalExpression[] nodeValues = new LogicalExpression[numNodes];
		final int[] nonDummyNodes = new int[nonDummyCount];
		final ColumnHeader[] headers = new ColumnHeader[nonDummyCount];
		for (int i = 0, nonDummyIndex = 0; i < numNodes; ++i) {
			if (nodes[i] instanceof INode) {
				headers[nonDummyIndex] = ((INode) nodes[i]).getColumnHeader();
				nonDummyNodes[nonDummyIndex++] = i;
			} else {
				nodeValues[i] = nodes[i] == null ? null
						: nodes[i].getExpression();
			}
		}

		// Create the table.
		final long timeMarker = System.currentTimeMillis();
		final FactorTable table = new FactorTable(true, headers);
		LOG.debug(() -> {
			LOG.debug(
					"Table creation time (size=%d, headers=%d --> %s): %.4fsec",
					table.size(), headers.length,
					Arrays.stream(headers)
							.map(header -> String.valueOf(header.numValues()))
							.collect(Collectors.joining("x")),
					(System.currentTimeMillis() - timeMarker) / 1000.0);
		});

		// Set all the table values.
		boolean featuresSet = false;
		final Map<INode, LogicalExpression> mapping = new HashMap<>();
		for (final List<LogicalExpression> values : CollectionUtils
				.cartesianProduct(Arrays.stream(headers)
						.map((ColumnHeader header) -> header.getNode()
								.slowGetAssignments())
						.collect(Collectors.toList()))) {
			// Set the variable mapping for the table and update nodeValues with
			// the current values for the non-dummy nodes.
			for (int i = 0; i < nonDummyCount; ++i) {
				mapping.put((INode) nodes[nonDummyNodes[i]], values.get(i));
				nodeValues[nonDummyNodes[i]] = values.get(i);
			}

			// Compute the features.
			final IHashVectorImmutable featureVector = featureFunction
					.apply(nodeValues, nodes);

			if (featureVector.size() != 0) {
				featuresSet = true;
			}

			// Set the appropriate value in the table with the features.
			table.set(mapping, model.score(featureVector), featureVector);
		}

		// If no features were set, don't create the factor.
		if (!featuresSet) {
			LOG.debug("Skipped creating an empty factor");
			return;
		}

		// Create the factor.
		final Factor factor = new Factor(table, factorId);

		// Create the connecting edges (to all non-dummy nodes).
		for (int i = 0; i < nonDummyCount; ++i) {
			final INode variableNode = (INode) nodes[nonDummyNodes[i]];
			final Edge edge = new Edge(variableNode, factor, true);
			factor.addEdge(edge);
			variableNode.addEdge(edge);
		}

		LOG.debug(
				"Created a factor between %d nodes with a table of size %d: %.4fsec",
				numNodes, table.size(),
				(System.currentTimeMillis() - startTime) / 1000.0);

	}

	public static List<Pair<IBaseNode, SkolemIdNode>> getInstancePairs(
			FactorGraph graph) {
		final GetInstancePairs visitor = new GetInstancePairs();
		visitor.visit(graph.getRoot());
		return visitor.pairs;
	}

	/**
	 * Given an instance literal node, get the typing node form the inner
	 * conjunction.
	 */
	public static LiteralNode getTypingNode(LiteralNode node) {
		LiteralNode typingNode = null;
		if (node.getArgs().get(1) instanceof LambdaNode
				&& ((LambdaNode) node.getArgs().get(1))
						.getBody() instanceof LiteralNode
				&& LogicLanguageServices.getTypeRepository().getTruthValueType()
						.equals(((LambdaNode) node.getArgs().get(1)).getBody()
								.getExpression().getType())) {
			final LiteralNode bodyNode = (LiteralNode) ((LambdaNode) node
					.getArgs().get(1)).getBody();
			if (LogicLanguageServices.getConjunctionPredicate()
					.equals(bodyNode.getPredicate().getExpression())) {
				for (final IBaseNode conjunctNode : bodyNode.getArgs()) {
					if (conjunctNode instanceof LiteralNode
							&& ((LiteralNode) conjunctNode).getArgs()
									.size() == 1) {
						if (typingNode == null) {
							typingNode = (LiteralNode) conjunctNode;
						} else {
							throw new IllegalStateException(
									"Multiple typing nodes: "
											+ node.getExpression());
						}
					}
				}
			} else if (bodyNode.getArgs().size() == 1) {
				// Case single predicate, which is the typing predicate.
				return bodyNode;
			}
		}
		return typingNode;
	}

	public static LogicalConstantNode getTypingPredicateNode(LiteralNode node) {
		final LiteralNode typingLiteral = getTypingNode(node);
		if (typingLiteral.getPredicate() instanceof LogicalConstantNode) {
			return (LogicalConstantNode) typingLiteral.getPredicate();
		} else {
			return null;
		}
	}

	/**
	 * Extracts all pairs of skolem IDs and their instance typing predicate. If
	 * a skolem ID has no instance typing predicate, pair it with null.
	 *
	 * @author Yoav Artzi
	 */
	private static class GetInstancePairs implements IFactorGraphVisitor {

		private final List<Pair<IBaseNode, SkolemIdNode>> pairs = new LinkedList<>();

		@Override
		public void visit(LiteralNode node) {
			// If this literal is not an indefinite quantifier, process it with
			// the default method only
			if (AMRServices.isSkolemTerm(node.getLiteral())
					&& node.getArgs().size() == 2) {
				// First argument is the ID.
				if (!(node.getArgs().get(0) instanceof SkolemIdNode)) {
					throw new IllegalStateException("Unexpected quantifier ID: "
							+ node.getExpression());
				}
				final SkolemIdNode id = (SkolemIdNode) node.getArgs().get(0);

				// Get the instance typing predicate. It's the single unary
				// predicate from the conjunction in the body of the lambda
				// term.
				final LiteralNode typingNode = FactorGraphFeatureServices
						.getTypingNode(node);
				final IBaseNode typingNodePredicate = typingNode == null ? null
						: typingNode.getPredicate();
				if (typingNodePredicate == null) {
					LOG.warn("No typing node found: %s", node.getExpression());
				}
				pairs.add(Pair.of(typingNodePredicate, id));
			}
			IFactorGraphVisitor.super.visit(node);
		}
	}

}
