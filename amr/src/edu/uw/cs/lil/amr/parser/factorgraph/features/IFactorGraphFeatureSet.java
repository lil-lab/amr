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

import java.io.Serializable;
import java.util.List;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;

/**
 * Feature set for a {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public interface IFactorGraphFeatureSet extends Serializable {

	/**
	 * Create and score factors for the features in this set. This method
	 * doesn't update the graph but instead creates jobs that once executed will
	 * update the graph. These jobs are thread safe and can be executed in
	 * parallel. This allows to distribute graph construction.
	 *
	 * @param graph
	 *            Factor graph to populate with factors.
	 * @param meta
	 *            The sentence meta data.
	 * @param model
	 *            Model with parameters.
	 * @return List of {@link Runnable}s, each adds a factor to the graph. The
	 *         generated jobs are thread-safe.
	 */
	List<Runnable> createFactorJobs(FactorGraph graph, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model);

}
