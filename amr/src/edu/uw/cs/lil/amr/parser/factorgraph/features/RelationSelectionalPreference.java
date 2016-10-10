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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetHeadString;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.AbstractVariableNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.VariableNode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Relational selectional preference features. The features triggers on a binary
 * relation, and pairs the instance type of the first argument, the instance
 * type of the second argument and the name of the relation. If the second
 * argument is a reference, the type of the referred instance is used. If the
 * second argument is a constant, its type is used.
 *
 * @author Yoav Artzi
 */
public class RelationSelectionalPreference implements IFactorGraphFeatureSet {
	public static ILogger		LOG					= LoggerFactory
			.create(RelationSelectionalPreference.class);
	private static final String	FEATURE_TAG			= "RELPREF";
	private static final long	serialVersionUID	= 6705348696794469458L;

	@Override
	public List<Runnable> createFactorJobs(FactorGraph graph, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		final SetFeatures visitor = new SetFeatures(model,
				FactorGraphFeatureServices.getInstancePairs(graph));
		visitor.visit(graph.getRoot());
		return visitor.jobs;
	}

	public static class Creator
			implements IResourceObjectCreator<RelationSelectionalPreference> {

		private final String type;

		public Creator() {
			this("factor.feat.relpref");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public RelationSelectionalPreference create(Parameters params,
				IResourceRepository repo) {
			return new RelationSelectionalPreference();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, RelationSelectionalPreference.class)
					.setDescription(
							"Relation selectional preference feature set")
					.build();
		}

	}

	private class SetFeatures implements IFactorGraphVisitor {
		private int																counter					= 0;
		private final List<Pair<IBaseNode, SkolemIdNode>>						instancePairs;
		private final List<Runnable>											jobs					= new LinkedList<>();
		private final IJointDataItemModel<LogicalExpression, LogicalExpression>	model;
		private final Map<Variable, LiteralNode>								variableToTypingNode	= new HashMap<>();

		public SetFeatures(
				IJointDataItemModel<LogicalExpression, LogicalExpression> model,
				List<Pair<IBaseNode, SkolemIdNode>> instancePairs) {
			this.model = model;
			this.instancePairs = instancePairs;
		}

		@Override
		public void visit(LiteralNode node) {
			if (AMRServices.isSkolemTerm(node.getLiteral())
					&& node.getArgs().size() == 2) {
				// If indefinite quantifier, remember the mapping of the
				// variable of the wrapped lambda term to the instance predicate
				// node.

				// Get the typing node.
				final LiteralNode typingNode = FactorGraphFeatureServices
						.getTypingNode(node);

				if (typingNode == null) {
					LOG.error("Missing typing node: %s", node);
				} else {
					// Store a mapping of the variable to it.
					final IBaseNode typingNodeArg = typingNode.getArgs().get(0);
					if (typingNodeArg instanceof VariableNode) {
						variableToTypingNode.put(
								((VariableNode) typingNodeArg).getVariable(),
								typingNode);
					} else {
						LOG.error(
								"Skolem node with typing node with non-variable argument: %s",
								node);
					}
				}
			} else if (node.getArgs().size() == 2
					&& !LogicLanguageServices.isCoordinationPredicate(
							node.getPredicate().getExpression())) {
				// Case binary literal: create the factor between the predicate
				// and the two arguments.

				if (node.getArgs().get(0) instanceof VariableNode) {

					// Use the variable mapping to get the typing
					// predicate for the second argument.
					final LiteralNode firstArgTypingNode = variableToTypingNode
							.get(((VariableNode) node.getArgs().get(0))
									.getVariable());
					final IBaseNode firstArgTypingPredicate = firstArgTypingNode == null
							? null : firstArgTypingNode.getPredicate();

					if (node.getArgs().get(1) instanceof LiteralNode) {
						final LiteralNode arg2 = (LiteralNode) node.getArgs()
								.get(1);
						if (AMRServices.isRefPredicate(
								arg2.getPredicate().getExpression())
								&& arg2.getArgs().size() == 1
								&& arg2.getArgs().get(
										0) instanceof AbstractVariableNode) {
							// If the second argument is a reference, create a
							// 4-way factor to include the reference and the
							// typing predicate of the referred instance.

							final AbstractVariableNode referringNode = (AbstractVariableNode) arg2
									.getArgs().get(0);
							for (final Pair<IBaseNode, SkolemIdNode> instancePair : instancePairs) {
								// Create a job.
								final int factorNumber = ++counter;
								jobs.add(() -> {
									FactorGraphFeatureServices
											.addFactor((expValues, nodes) -> {
										final IHashVector features = HashVectorFactory
												.create();
										// expValues[1] is the
										// ID of the reference,
										// expValues[3] is the
										// ID of the instance,
										// so they must be
										// assigned the same ID.
										if (expValues[1].equals(expValues[3])) {
											if (AMRServices.isPassivePredicate(
													expValues[4])) {
												// Case passive
												// predicate,
												// the feature
												// must be
												// recorded in
												// active form.

												// Trigram
												// arg1-relation-arg2
												// feature.
												features.set(FEATURE_TAG,
														expValues[2] == null
																? "null"
																: GetHeadString
																		.of(expValues[2],
																				true),
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(AMRServices
																				.makeRelationActive(
																						(LogicalConstant) expValues[4],
																						false),
																				true),
														expValues[0] == null
																? "null"
																: GetHeadString
																		.of(expValues[0],
																				true),
														1.0);

												// Bigram
												// arg1-relation
												// feature.
												features.set(FEATURE_TAG,
														expValues[2] == null
																? "null"
																: GetHeadString
																		.of(expValues[2],
																				true),
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(AMRServices
																				.makeRelationActive(
																						(LogicalConstant) expValues[4],
																						false),
																				true),
														1.0);

												// Bigram
												// relation-arg2
												// feature.
												features.set(FEATURE_TAG,
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(AMRServices
																				.makeRelationActive(
																						(LogicalConstant) expValues[4],
																						false),
																				true),
														expValues[0] == null
																? "null"
																: GetHeadString
																		.of(expValues[0],
																				true),
														1.0);

											} else {
												// Trigram
												// arg1-relation-arg2
												// feature.
												features.set(FEATURE_TAG,
														expValues[0] == null
																? "null"
																: GetHeadString
																		.of(expValues[0],
																				true),
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(expValues[4],
																				true),
														expValues[2] == null
																? "null"
																: GetHeadString
																		.of(expValues[2],
																				true),
														1.0);

												// Bigram
												// arg1-relation
												// feature.
												features.set(FEATURE_TAG,
														expValues[0] == null
																? "null"
																: GetHeadString
																		.of(expValues[0],
																				true),
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(expValues[4],
																				true),
														1.0);

												// Bigram
												// relation-arg2
												// feature.
												features.set(FEATURE_TAG,
														expValues[4] == null
																? "null"
																: GetHeadString
																		.of(expValues[4],
																				true),
														expValues[2] == null
																? "null"
																: GetHeadString
																		.of(expValues[2],
																				true),
														1.0);
											}
										}
										return features;
									} , model, FEATURE_TAG + factorNumber,
													firstArgTypingPredicate,
													referringNode,
													instancePair.first(),
													instancePair.second(),
													node.getPredicate());
								});
							}
						} else if (AMRServices.isSkolemTerm(arg2.getLiteral())
								&& arg2.getArgs().size() == 2) {
							// If the second argument is a indefinitely
							// quantifier instance, get the instance type
							// predicate node and create the factor.

							// Create the job.
							final int factorNumber = ++counter;
							jobs.add(() -> {

								// Get the predicate typing node for the second
								// argument.
								final LiteralNode secondArgTypingNode = FactorGraphFeatureServices
										.getTypingNode(arg2);
								final IBaseNode secondArgTypingPredicate = secondArgTypingNode == null
										? null
										: secondArgTypingNode.getPredicate();

								FactorGraphFeatureServices
										.addFactor((expValues, nodes) -> {
									final IHashVector features = HashVectorFactory
											.create();
									if (AMRServices
											.isPassivePredicate(expValues[1])) {
										// Case passive
										// predicate, the
										// feature must be
										// recorded in active
										// form.

										// Trigram
										// arg1-relation-arg2
										// feature.
										features.set(FEATURE_TAG,
												expValues[2] == null ? "null"
														: GetHeadString.of(
																expValues[2],
																true),
												expValues[1] == null ? "null"
														: GetHeadString.of(
																AMRServices
																		.makeRelationActive(
																				(LogicalConstant) expValues[1],
																				false),
																true),
												expValues[0] == null ? "null"
														: GetHeadString.of(
																expValues[0],
																true),
												1.0);

										// Bigram arg1-relation
										// feature.
										features.set(FEATURE_TAG,
												expValues[2] == null ? "null"
														: GetHeadString.of(
																expValues[2],
																true),
												expValues[1] == null ? "null"
														: GetHeadString.of(
																AMRServices
																		.makeRelationActive(
																				(LogicalConstant) expValues[1],
																				false),
																true),
												1.0);

										// Bigram relation-arg2
										// feature.
										features.set(FEATURE_TAG,
												expValues[1] == null ? "null"
														: GetHeadString.of(
																AMRServices
																		.makeRelationActive(
																				(LogicalConstant) expValues[1],
																				false),
																true),
												expValues[0] == null ? "null"
														: GetHeadString.of(
																expValues[0],
																true),
												1.0);
									} else {
										// Trigram
										// arg1-relation-arg2
										// feature.
										features.set(FEATURE_TAG,
												expValues[0] == null ? "null"
														: GetHeadString.of(
																expValues[0],
																true),
												expValues[1] == null ? "null"
														: GetHeadString.of(
																expValues[1],
																true),
												expValues[2] == null ? "null"
														: GetHeadString.of(
																expValues[2],
																true),
												1.0);

										// Bigram arg1-relation
										// feature.
										features.set(FEATURE_TAG,
												expValues[0] == null ? "null"
														: GetHeadString.of(
																expValues[0],
																true),
												expValues[1] == null ? "null"
														: GetHeadString.of(
																expValues[1],
																true),
												1.0);

										// Bigram relation-arg2
										// feature.
										features.set(FEATURE_TAG,
												expValues[1] == null ? "null"
														: GetHeadString.of(
																expValues[1],
																true),
												expValues[2] == null ? "null"
														: GetHeadString.of(
																expValues[2],
																true),
												1.0);
									}
									return features;
								} , model, FEATURE_TAG + factorNumber,
												firstArgTypingPredicate,
												node.getPredicate(),
												secondArgTypingPredicate);
							});
						} else {
							LOG.error(
									"Unexpected literal second argument in literal: %s",
									node);
						}
					} else if (node.getArgs()
							.get(1) instanceof LogicalConstantNode) {
						// Case a relation between a variable and a logical
						// constant. Create a feature that uses the constant
						// type instead of the typing predicate base name.

						// Create the job.
						final int factorNumber = ++counter;
						jobs.add(() -> {
							FactorGraphFeatureServices
									.addFactor((expValues, nodes) -> {
								final IHashVector features = HashVectorFactory
										.create();
								if (AMRServices
										.isPassivePredicate(expValues[1])) {
									// Case passive predicate,
									// the feature must be
									// recorded in active form.

									// Trigram arg1-relation-arg2
									// feature.
									features.set(FEATURE_TAG,
											expValues[2] == null ? "null"
													: expValues[2].getType()
															.toString(),
											expValues[1] == null ? "null"
													: GetHeadString.of(
															AMRServices
																	.makeRelationActive(
																			(LogicalConstant) expValues[1],
																			false),
															true),
											expValues[0] == null ? "null"
													: GetHeadString.of(
															expValues[0], true),
											1.0);

									// Bigram arg1-relation feature.
									features.set(FEATURE_TAG,
											expValues[2] == null ? "null"
													: expValues[2].getType()
															.toString(),
											expValues[1] == null ? "null"
													: GetHeadString.of(
															AMRServices
																	.makeRelationActive(
																			(LogicalConstant) expValues[1],
																			false),
															true),
											1.0);

									// Bigram relation-arg2 feature.
									features.set(FEATURE_TAG,
											expValues[1] == null ? "null"
													: GetHeadString.of(
															AMRServices
																	.makeRelationActive(
																			(LogicalConstant) expValues[1],
																			false),
															true),
											expValues[0] == null ? "null"
													: GetHeadString.of(
															expValues[0], true),
											1.0);
								} else {
									// Trigram arg1-relation-arg2
									// feature.
									features.set(FEATURE_TAG,
											expValues[0] == null ? "null"
													: GetHeadString.of(
															expValues[0], true),
											expValues[1] == null ? "null"
													: GetHeadString.of(
															expValues[1], true),
											expValues[2] == null ? "null"
													: expValues[2].getType()
															.toString(),
											1.0);

									// Bigram arg1-relation feature.
									features.set(FEATURE_TAG,
											expValues[0] == null ? "null"
													: GetHeadString.of(
															expValues[0], true),
											expValues[1] == null ? "null"
													: GetHeadString.of(
															expValues[1], true),
											1.0);

									// Bigram relation-arg2 feature.
									features.set(FEATURE_TAG,
											expValues[1] == null ? "null"
													: GetHeadString.of(
															expValues[1], true),
											expValues[2] == null ? "null"
													: expValues[2].getType()
															.toString(),
											1.0);

								}
								return features;
							} , model, FEATURE_TAG + factorNumber,
											firstArgTypingPredicate,
											node.getPredicate(),
											node.getArgs().get(1));
						});
					} else {
						LOG.error(
								"Unexpected second argument in a binary literal: %s",
								node);
					}
				} else {
					LOG.error(
							"Unexpected first argument in a binary literal: %s",
							node);
				}
			}

			IFactorGraphVisitor.super.visit(node);

		}
	}

}
