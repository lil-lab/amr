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
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Create unary factors (that connect to a single variable) and will provide a
 * bias about it assignment. This factor is not created for factor nodes, but
 * only for logical constants.
 *
 * @author Yoav Artzi
 */
public class UnaryBiasFeatures implements IFactorGraphFeatureSet {

	private static final String	FEATURE_TAG			= "UNARY";
	private static final long	serialVersionUID	= 1051428769489657239L;

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
		final Visitor visitor = new Visitor(model);
		visitor.visit(graph.getRoot());
		return visitor.jobs;
	}

	public static class Creator
			implements IResourceObjectCreator<UnaryBiasFeatures> {

		private final String type;

		public Creator() {
			this("factor.feat.unary");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public UnaryBiasFeatures create(Parameters params,
				IResourceRepository repo) {
			return new UnaryBiasFeatures();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, UnaryBiasFeatures.class)
					.setDescription(
							"Create unary factors (that connect to a single variable) and will provide a bias about it assignment.")
					.build();
		}

	}

	private static class Visitor implements IFactorGraphVisitor {

		private int																counter	= 0;
		private final List<Runnable>											jobs	= new LinkedList<>();
		private final IJointDataItemModel<LogicalExpression, LogicalExpression>	model;

		public Visitor(
				IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
			this.model = model;
		}

		@Override
		public void visit(LogicalConstantNode node) {
			// Don't trigger this feature for skolem IDs.
			if (!AMRServices.getSpecMapping().getAssignments(node.getConstant())
					.isEmpty()) {

				// Create the job.
				final int factorNumber = ++counter;
				jobs.add(() -> {
					FactorGraphFeatureServices.addFactor((values, nodes) -> {
						final IHashVector features = HashVectorFactory.create();

						// Feature that crosses the potentially
						// overloaded base name of the underspecified
						// constant and the target constant.
						final LogicalExpression exp = nodes[0].getExpression();
						features.set(FEATURE_TAG, toFeatureString(exp),
								toFeatureString(values[0]), 1.0);

						return features;
					} , model, FEATURE_TAG + factorNumber, node);
				});

			}
		}
	}

}
