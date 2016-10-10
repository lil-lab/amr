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

import java.util.Collections;
import java.util.List;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;

/**
 * Create a single global factor to account for closure in the underspecified
 * logical form. Closure is used during sloppy inference to modify a complete
 * logical form, which is not a valid AMR, to a valid underspecified AMR.
 * Creating a truly global factor is expensive: it requires connecting to all
 * variables and enumerating all combination of options. However, this is not
 * necessary since the value of the feature is completely independent of any
 * choice in the factor graph. Instead, we simply connect this feature to the
 * top-most skolem ID (of the top most entity). Any AMR is guaranteed to have
 * one (except the empty AMR). The factor will have the same feature value for
 * each assignment to this variable.
 *
 *
 * @author Yoav Artzi
 */
public class ClosureFeature implements IFactorGraphFeatureSet {

	private static final String	FEATURE_TAG			= "CLOSURE";
	private static final long	serialVersionUID	= 5851584169176561610L;

	@Override
	public List<Runnable> createFactorJobs(FactorGraph graph, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		if (graph.isClosure()) {
			final IBaseNode root = graph.getRoot();
			if (root instanceof LiteralNode
					&& AMRServices.isSkolemTerm(root.getExpression())
					&& ((LiteralNode) root).getArgs().size() == 2
					&& ((LiteralNode) root).getArgs()
							.get(0) instanceof SkolemIdNode) {
				return ListUtils
						.createSingletonList(() -> FactorGraphFeatureServices
								.addFactor((values, nodes) -> {
									// Closure feature.
									final IHashVector features = HashVectorFactory
											.create();
									features.set(FEATURE_TAG, 1.0);
									return features;
								} , model, FEATURE_TAG,
										((LiteralNode) root).getArgs().get(0)));

			}
		}

		return Collections.emptyList();
	}

	public static class Creator
			implements IResourceObjectCreator<ClosureFeature> {

		private final String type;

		public Creator() {
			this("factor.feat.closure");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public ClosureFeature create(Parameters params,
				IResourceRepository repo) {
			return new ClosureFeature();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, ClosureFeature.class)
					.setDescription(
							"Create a closure feature if the factor graph required sloppy closure to generate a valid underspecified logical from from the base derivation.")
					.build();
		}

	}

}
