/*******************************************************************************
 * UW SPF - The University of Washington Semantic Parsing Framework
 * <p>
 * Copyright (C) 2013 Yoav Artzi
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
 ******************************************************************************/
package edu.uw.cs.lil.amr.learn.online;

import java.util.function.IntConsumer;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.GradientComputation;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * Abstract gradient-based supervised learner.
 *
 * @author Yoav Artzi
 * @param <SAMPLE>
 *            Inference sample.
 * @param <DI>
 *            Learning data item.
 */
public abstract class AbstractGradLearner extends AbstractOnlineLearner {
	public static final ILogger LOG = LoggerFactory
			.create(AbstractGradLearner.class);

	protected AbstractGradLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean sortData,
			IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam, boolean resumeLearning) {
		super(numIterations, trainingData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, sortData, estimator, gradientFunction,
				conditionedInferenceBeam, resumeLearning);
	}

	@Override
	protected void parameterUpdate(final LabeledAmrSentence dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int dataItemNumber, int epochNumber) {

		final GradientComputation gradientComputation = gradientFunction.of(
				dataItem, dataItemModel, dataItemNumber, epochNumber, parser,
				filterFactory, parserOutputLogger);

		// Apply updates to the learning statistics.
		gradientComputation.getStatUpdates().accept(stats);

		if (gradientComputation.getGradient() == null) {
			return;
		}

		// Apply the update.

		// Apply the update.
		if (updateProcedure.applyUpdate(gradientComputation.getGradient(),
				model.getTheta())) {
			// Apply the update.
			stats.count("Update", epochNumber);
		} else {
			LOG.info("No update%s", gradientComputation.isLabelIsOptimal() ? ""
					: ", although best is not the correct result");
		}
	}

}
