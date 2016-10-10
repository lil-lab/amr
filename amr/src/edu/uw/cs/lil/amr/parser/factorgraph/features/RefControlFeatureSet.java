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

import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.AbstractVariableNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Features to control control-like structures where an entity nested within
 * another has a relation that refers to an entity related to the parent (e.g.,
 * "I want to buy a ticket" or "I want you to buy a ticket"). The triggered
 * feature is:
 * TYPE_OF_PARENT#PARENT_RELATION_TO_REFERENCED#PARENT_RELATION_TO_CHILD#
 * CHILD_RELATION_TO_REFERENCE.
 *
 * This feature set drastically slows down inference since it creates extremely
 * large factors which are expensive to compute. Most likely, it's not used.
 *
 * @author Yoav Artzi
 */
public class RefControlFeatureSet implements IFactorGraphFeatureSet {
	public static final ILogger	LOG					= LoggerFactory
			.create(RefControlFeatureSet.class);
	private static final String	DEFAULT_TAG			= "REFCTRL";
	private static final long	serialVersionUID	= -3678766765006520422L;
	private final String		featureTag;

	public RefControlFeatureSet() {
		this(DEFAULT_TAG);
	}

	public RefControlFeatureSet(String featureTag) {
		this.featureTag = featureTag;
	}

	private static InstanceSummary summarize(LiteralNode skolemTerm) {
		// Get the skolem ID.
		if (!(skolemTerm.getArgs().get(0) instanceof SkolemIdNode)) {
			LOG.warn("Expected skolem ID node in first argument: %s",
					skolemTerm);
			return null;
		}
		// Get the body of the skolem term.
		if (!(skolemTerm.getArgs().get(1) instanceof LambdaNode)
				|| !(((LambdaNode) skolemTerm.getArgs().get(1))
						.getBody() instanceof LiteralNode)) {
			LOG.warn(
					"Expected a lambda term in second argument with a literal body: %s",
					skolemTerm);
			return null;
		}
		final LiteralNode body = (LiteralNode) ((LambdaNode) skolemTerm
				.getArgs().get(1)).getBody();

		// Iterate over the properties, in conjunction or a single literal. Get
		// the relational pairs and the typing node of the instance.
		final AbstractVariableNode[] relatedIdNodes;
		final LogicalConstantNode[] relationNodes;
		final boolean[] relatedIsRef;
		LogicalConstantNode typingNode = null;
		final int len = body.getArgs().size();
		if (body.getLiteral().getPredicate()
				.equals(LogicLanguageServices.getConjunctionPredicate())) {
			// Case conjunction of relations and a single unary typing literal.
			relationNodes = new LogicalConstantNode[len - 1];
			relatedIdNodes = new AbstractVariableNode[len - 1];
			relatedIsRef = new boolean[len - 1];
			int j = 0;
			for (int i = 0; i < len; ++i) {
				final IBaseNode arg = body.getArgs().get(i);
				if (arg instanceof LiteralNode) {
					final LiteralNode argLiteral = (LiteralNode) arg;
					if (argLiteral.getArgs().size() == 1) {
						// Typing node.
						if (argLiteral
								.getPredicate() instanceof LogicalConstantNode
								&& typingNode == null) {
							typingNode = (LogicalConstantNode) argLiteral
									.getPredicate();
						} else {
							LOG.debug("Multiple typing nodes: %s", skolemTerm);
							return null;
						}
					} else if (argLiteral.getArgs().size() == 2 && argLiteral
							.getPredicate() instanceof LogicalConstantNode) {
						// The node of the relation predicate.
						relationNodes[j] = (LogicalConstantNode) argLiteral
								.getPredicate();
						// Get the skolem ID of the related argument, can be
						// null (if simple constant).
						final IBaseNode relatedArg = argLiteral.getArgs()
								.get(1);
						if (AMRServices.isRefLiteral(relatedArg.getExpression())
								&& ((LiteralNode) relatedArg).getArgs().get(
										0) instanceof AbstractVariableNode) {
							// Case reference literal, the skolem ID node is the
							// first (and only) argument.
							relatedIdNodes[j] = (AbstractVariableNode) ((LiteralNode) relatedArg)
									.getArgs().get(0);
							relatedIsRef[j] = true;
						} else if (AMRServices
								.isSkolemTerm(relatedArg.getExpression())
								&& ((LiteralNode) relatedArg).getArgs().get(
										0) instanceof AbstractVariableNode) {
							relatedIdNodes[j] = (AbstractVariableNode) ((LiteralNode) relatedArg)
									.getArgs().get(0);
							relatedIsRef[j] = false;
						} else if (relatedArg instanceof LogicalConstantNode) {
							relatedIdNodes[j] = null;
							relatedIsRef[j] = false;
						} else {
							LOG.debug(
									"Failed to get ID of related instance: %s <- %s",
									relatedArg, skolemTerm);
							return null;
						}
						++j;
					} else {
						LOG.debug(
								"Invalid number of argument in literal: %s <- %s",
								argLiteral, skolemTerm);
						return null;
					}
				} else {
					LOG.debug("Unexpected argument: %s", skolemTerm);
					return null;
				}
			}
			if (j != len - 1) {
				LOG.debug("Unexpected number of related instances: %s",
						skolemTerm);
				return null;
			}
		} else {
			// Case single literal, must be the unary typing literal, so no
			// related instances.
			relationNodes = new LogicalConstantNode[0];
			relatedIdNodes = new SkolemIdNode[0];
			relatedIsRef = new boolean[0];
			if (len == 1
					&& body.getPredicate() instanceof LogicalConstantNode) {
				typingNode = (LogicalConstantNode) body.getPredicate();
			}
		}
		if (typingNode == null) {
			LOG.debug("Failed to recover the typing node: %s", skolemTerm);
			return null;
		}

		return new InstanceSummary(typingNode,
				(SkolemIdNode) skolemTerm.getArgs().get(0), relationNodes,
				relatedIdNodes, relatedIsRef);
	}

	private static String toFeatureString(LogicalExpression exp) {
		if (exp instanceof LogicalConstant) {
			return ((LogicalConstant) exp).getBaseName();
		} else if (exp != null) {
			return exp.toString();
		} else {
			return "null";
		}
	}

	@Override
	public List<Runnable> createFactorJobs(FactorGraph graph, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		final CreateFactors visitor = new CreateFactors(model);
		visitor.visit(graph.getRoot());
		return visitor.jobs;
	}

	public static class Creator
			implements IResourceObjectCreator<RefControlFeatureSet> {

		private final String type;

		public Creator() {
			this("factor.feat.control");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public RefControlFeatureSet create(Parameters params,
				IResourceRepository repo) {
			return new RefControlFeatureSet(params.get("tag", DEFAULT_TAG));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, RefControlFeatureSet.class)
					.addParam("tag", String.class,
							"Feature tag (default: " + DEFAULT_TAG + ")")
					.setDescription(
							"Reference features inspired by linguistic control of verbs")
					.build();
		}

	}

	private class CreateFactors implements IFactorGraphVisitor {

		private int																counter			= 0;
		private InstanceSummary													currentSummary	= null;
		private final List<Runnable>											jobs			= new LinkedList<>();
		private final IJointDataItemModel<LogicalExpression, LogicalExpression>	model;

		public CreateFactors(
				IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
			this.model = model;
		}

		@Override
		public void visit(LiteralNode node) {
			if (AMRServices.isSkolemTerm(node.getExpression())) {
				final InstanceSummary parent = currentSummary;
				final InstanceSummary summary = summarize(node);

				if (summary == null) {
					throw new IllegalStateException(
							"Failed to summarize skolem term node: " + node);
				}

				if (parent != null) {
					final int currentLen = summary.relatedIdNodes.length;
					final int parentLen = parent.relatedIdNodes.length;
					for (int currentIndex = 0; currentIndex < currentLen; ++currentIndex) {
						if (summary.relatedIsRef[currentIndex]) {

							// From the summary of the parent, find the relation
							// to current.
							LogicalConstantNode relation = null;
							for (int parentIndex = 0; parentIndex < parentLen; ++parentIndex) {
								if (parent.relatedIdNodes[parentIndex] == summary.instanceId) {
									relation = parent.relationNodes[parentIndex];
								}
							}
							if (relation == null) {
								throw new RuntimeException(
										"Failed to find the relation between parent and child -- indication of a bug");
							}
							final LogicalConstantNode parentCurrentRelation = relation;

							final int jobCurrentIndex = currentIndex;
							for (int parentIndex = 0; parentIndex < parentLen; ++parentIndex) {
								final int jobParentIndex = parentIndex;
								// Create the jobs. Some variable above are made
								// final to allow this.
								final int factorNumber = counter++;
								jobs.add(() -> {
									FactorGraphFeatureServices
											.addFactor((values, nodes) -> {
										if (values[2] == values[5]) {
											final IHashVector features = HashVectorFactory
													.create();
											// 4-gram feature
											// that includes all
											// the information.
											features.add(featureTag,
													toFeatureString(values[0]),
													toFeatureString(values[1]),
													toFeatureString(values[3]),
													toFeatureString(values[4]),
													1.0);

											// Control binary
											// feature.
											features.add(featureTag, 1.0);

											return features;
										} else {
											return HashVectorFactory.empty();
										}
									} , model, featureTag + factorNumber,
													parent.typingNode,
													parent.relationNodes[jobParentIndex],
													parent.relatedIdNodes[jobParentIndex],
													parentCurrentRelation,
													summary.relationNodes[jobCurrentIndex],
													summary.relatedIdNodes[jobCurrentIndex]);
								});
							}
						}
					}
				}

				currentSummary = summary;
				IFactorGraphVisitor.super.visit(node);

				currentSummary = parent;
			} else {
				IFactorGraphVisitor.super.visit(node);
			}
		}

	}

	private static class InstanceSummary {
		/**
		 * The node of the Skolem ID of the summarized instance.
		 */
		private final SkolemIdNode instanceId;

		/**
		 * The IDs of the instances related to this instance. May be IDs of
		 * Skolem terms or unresolved reference nodes.
		 */
		private final AbstractVariableNode[] relatedIdNodes;

		/**
		 * Flag to indicate if the related ID is a reference.
		 */
		private final boolean[] relatedIsRef;

		/**
		 * The relation to the related instance.
		 */
		private final LogicalConstantNode[] relationNodes;

		/**
		 * The typing node of the instance.
		 */
		private final LogicalConstantNode typingNode;

		public InstanceSummary(LogicalConstantNode typingNode,
				SkolemIdNode instanceId, LogicalConstantNode[] relationNodes,
				AbstractVariableNode[] relatedSkolemIdNodes,
				boolean[] relatedIsRef) {
			this.typingNode = typingNode;
			this.relationNodes = relationNodes;
			this.instanceId = instanceId;
			this.relatedIdNodes = relatedSkolemIdNodes;
			this.relatedIsRef = relatedIsRef;
		}

	}

}
