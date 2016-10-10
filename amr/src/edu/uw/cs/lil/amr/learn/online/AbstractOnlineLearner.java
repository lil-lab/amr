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
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.genlex.ccg.LexiconGenerationServices;
import edu.cornell.cs.nlp.spf.learn.situated.AbstractSituatedLearner;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.system.MemoryReport;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.AbstractLearner;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.IsValidAmr;

/**
 * AMR-specific version of {@link AbstractSituatedLearner} for supervised online
 * learning.
 *
 * @author Yoav Artzi
 * @param <STATE>
 *            Type of initial state.
 * @param <DI>
 *            Data item used for learning.
 */
public abstract class AbstractOnlineLearner extends AbstractLearner {
	public static final ILogger LOG = LoggerFactory
			.create(AbstractOnlineLearner.class);

	protected AbstractOnlineLearner(int numIterations,
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
		super(numIterations, trainingData, sortData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, estimator, gradientFunction,
				conditionedInferenceBeam, resumeLearning);
	}

	@Override
	public void train(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		if (resumedLearning) {
			LOG.warn("RESUMED LEARNING: skipping update procedure init");
		} else {
			updateProcedure.init();
		}

		// Epochs
		for (int epochNumber = 0; epochNumber < epochs; ++epochNumber) {
			// Training epoch, iterate over all training samples
			LOG.info("=========================");
			LOG.info("Training epoch %d", epochNumber);
			LOG.info("=========================");
			int itemCounter = -1;

			final long epochStartTime = System.currentTimeMillis();

			// Iterating over training data.
			for (final LabeledAmrSentence dataItem : trainingData) {
				// Process a single training sample.

				// Record start time.
				final long startTime = System.currentTimeMillis();

				// Log sample header.
				LOG.info("%d : ================== [%d]", ++itemCounter,
						epochNumber);
				LOG.info("Sample type: %s",
						dataItem.getClass().getSimpleName());
				LOG.info("%s", dataItem);

				// Skip sample, if over the length limit.
				if (dataItem.getSample().getSample().getTokens()
						.size() > maxSentenceLength) {
					LOG.warn("Training sample too long, skipping");
					continue;
				}

				// Skip sample of the label is invalid.
				if (!IsValidAmr.of(dataItem.getLabel(), true, true)) {
					LOG.warn("Annotated LF is an invalid AMR -- skipping");
					continue;
				}

				stats.count("Processed", epochNumber);

				// Sample data item model.
				final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
						.createJointDataItemModel(dataItem.getSample());

				// ///////////////////////////
				// Step I: Generate a large number of potential lexical entries,
				// parse to prune them and update the lexicon.
				// ///////////////////////////

				if (genlex != null) {
					updateModel(model, generateLexicalEntries(dataItem, model,
							itemCounter, epochNumber));
				}

				// ///////////////////////////
				// Step II: Update model parameters.
				// ///////////////////////////
				parameterUpdate(dataItem, dataItemModel, model, itemCounter,
						epochNumber);

				// Record statistics.
				stats.mean("Sample processing",
						(System.currentTimeMillis() - startTime) / 1000.0,
						"sec");
				LOG.info("Total sample handling time: %.4fsec [mean: %.4fsec]",
						(System.currentTimeMillis() - startTime) / 1000.0,
						stats.getMean("Sample processing"));
				LOG.info("System memory: %s", MemoryReport.generate());
			}

			// Output epoch statistics
			LOG.info("Epoch time: %.2f",
					(System.currentTimeMillis() - epochStartTime) / 1000.0);
			LOG.info("System memory: %s", MemoryReport.generate());
			LOG.info("Epoch stats:");
			LOG.info(stats);

			// Run the post-iteration job.
			postIteration.accept(epochNumber);
		}
	}

	private void updateModel(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			ILexiconImmutable<LogicalExpression> entriesToAdd) {
		// Update the model's lexicon with generated lexical
		// entries from the max scoring valid generation parses
		for (final LexicalEntry<LogicalExpression> entry : entriesToAdd
				.toCollection()) {
			if (genlex.isGenerated(entry)) {
				if (model
						.addLexEntry(LexiconGenerationServices.unmark(entry))) {
					LOG.info("Added LexicalEntry to model: [%s] %s [%s]",
							entry.getOrigin(), entry, model.getTheta()
									.printValues(model.computeFeatures(entry)));
				}
				// Lexical generators might link related lexical
				// entries, so if we add the original one, we
				// should also add all its linked ones
				for (final LexicalEntry<LogicalExpression> linkedEntry : entry
						.getLinkedEntries()) {
					if (model.addLexEntry(
							LexiconGenerationServices.unmark(linkedEntry))) {
						LOG.info("Added LexicalEntry to model: [%s] %s [%s]",
								entry.getOrigin(), linkedEntry,
								model.getTheta().printValues(
										model.computeFeatures(linkedEntry)));
					}
				}
			}
		}
	}

	/**
	 * Parameter update method.
	 */
	protected abstract void parameterUpdate(LabeledAmrSentence dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int itemCounter, int epochNumber);
}
