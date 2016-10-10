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
package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.parser.joint.IEvaluation;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.collections.CollectionUtils;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.ProbEvaluationResult;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.Edge;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.VariableNode;

/**
 * Get the set of max scoring {@link IEvaluation} from a factor graph. The
 * maximum number of evaluations to get is limited to a given number. If there
 * are more max evaluations than the limit, an empty list is returned.
 *
 * @author Yoav Artzi
 */
public class GetMaxEvaluations implements IFactorGraphVisitor {
	public static final ILogger												LOG					= LoggerFactory
																										.create(GetMaxEvaluations.class);

	private final int														limit;
	/**
	 * Joint probability of the collected expression. Approximate using a
	 * product of marginals.
	 */
	private double															marginalsProduct	= 0.0;
	private List<Pair<LogicalExpression, Map<INode, LogicalExpression>>>	maxes;

	private final boolean													sloppy;

	private GetMaxEvaluations(int limit, boolean sloppy) {
		this.limit = limit;
		this.sloppy = sloppy;
	}

	public static List<ProbEvaluationResult> of(FactorGraph graph, int limit,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean sloppy) {
		final GetMaxEvaluations visitor = new GetMaxEvaluations(limit, sloppy);
		visitor.visit(graph.getRoot());

		// Get all factors, to get the features for each evaluation.
		final Set<IFactor> factors = GetFactors.of(graph);

		return visitor.maxes
				.stream()
				.map((pair) -> {
					// Collect the features.
					final Map<INode, LogicalExpression> mapping = pair.second();
					final IHashVector features = HashVectorFactory.create();
					for (final IFactor factor : factors) {
						if (sloppy) {
							// Verify that all the mapping fully specified the
							// assignment for this factor. If it doesn't, skip
							// the factor.
							boolean skipFactor = false;

							final int len = factor.numEdges();
							for (int j = 0; j < len; ++j) {
								final Edge edge = factor.getEdge(j);
								if (mapping.get(edge.getVariable()) == null) {
									skipFactor = true;
									break;
								}
							}
							if (skipFactor) {
								break;
							}
						}

						factor.getTable().getFeatures(mapping)
								.addTimesInto(1.0, features);
					}
					return new ProbEvaluationResult(model.score(features),
							visitor.marginalsProduct, features, pair.first());
				}).collect(Collectors.toList());
	}

	@Override
	public void visit(LambdaNode node) {
		node.getArgument().accept(this);
		final List<Pair<LogicalExpression, Map<INode, LogicalExpression>>> variables = maxes;
		node.getBody().accept(this);
		if (maxes.isEmpty() || variables.isEmpty()
				|| variables.size() * maxes.size() > limit) {
			// Case no logical expressions to generate or too many.
			LOG.debug("Too many evaluations for: %s", node);
			maxes = Collections.emptyList();
		} else {
			final List<Pair<LogicalExpression, Map<INode, LogicalExpression>>> maxLambdas = new LinkedList<>();
			for (final Pair<LogicalExpression, Map<INode, LogicalExpression>> variable : variables) {
				for (final Pair<LogicalExpression, Map<INode, LogicalExpression>> body : maxes) {
					body.second().putAll(variable.second());
					maxLambdas.add(Pair.of(
							new Lambda((Variable) variable.first(), body
									.first()), body.second()));
				}
			}
			maxes = maxLambdas;
		}
	}

	@Override
	public void visit(LiteralNode node) {
		node.getPredicate().accept(this);
		final List<Pair<LogicalExpression, Map<INode, LogicalExpression>>> predicateMaxes = maxes;
		int numAssignments = predicateMaxes.size();
		final List<List<Pair<LogicalExpression, Map<INode, LogicalExpression>>>> argMaxes = new ArrayList<>(
				node.getArgs().size());
		for (final IBaseNode argNode : node.getArgs()) {
			argNode.accept(this);
			numAssignments *= maxes.size();
			if (numAssignments == 0 || numAssignments > limit) {
				LOG.debug("Too many evaluation for: %s", node);
				maxes = Collections.emptyList();
				return;
			}
			argMaxes.add(maxes);
		}

		// Create the max literal assignments.
		maxes = new LinkedList<>();
		for (final Pair<LogicalExpression, Map<INode, LogicalExpression>> predicate : predicateMaxes) {
			for (final List<Pair<LogicalExpression, Map<INode, LogicalExpression>>> argPairs : CollectionUtils
					.cartesianProduct(argMaxes)) {
				final Map<INode, LogicalExpression> mapping = new HashMap<>(
						predicate.second());
				final LogicalExpression[] args = new LogicalExpression[argPairs
						.size()];
				for (int i = 0; i < args.length; ++i) {
					final Pair<LogicalExpression, Map<INode, LogicalExpression>> pair = argPairs
							.get(i);
					mapping.putAll(pair.second());
					args[i] = pair.first();
				}
				maxes.add(Pair.of(new Literal(predicate.first(), args), mapping));
			}
		}
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
		maxes = ListUtils.createSingletonList(Pair.of(node.getExpression(),
				new HashMap<>()));
	}

	private void processAssignedNode(INode node) {
		final Pair<Set<LogicalExpression>, Double> argmax = node
				.getMaxAssignments();
		maxes = argmax.first().stream().map((LogicalExpression e) -> {
			final Map<INode, LogicalExpression> mapping = new HashMap<>();
			mapping.put(node, e);
			return Pair.of(e, mapping);
		}).collect(Collectors.toList());
		marginalsProduct += argmax.second();
		if (sloppy && maxes.size() > 1) {
			final Map<INode, LogicalExpression> mapping = new HashMap<>();
			mapping.put(node, null);
			maxes = ListUtils.createSingletonList(Pair.of(node.getExpression(),
					mapping));
		}
	}

}
