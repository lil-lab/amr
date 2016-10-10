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
package edu.uw.cs.lil.amr.learn.batch;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.genlex.ccg.LexiconGenerationServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.accumulator.DoubleAccumulator;
import edu.cornell.cs.nlp.utils.system.MemoryReport;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.learn.AbstractLearner;
import edu.uw.cs.lil.amr.learn.estimators.IWeightUpdateProcedure;
import edu.uw.cs.lil.amr.learn.gradient.IGradientFunction;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * Batch abstract learner. Lexical induction and parameter estimation are done
 * in batch.
 *
 * @author Yoav Artzi
 */
public abstract class AbstractBatchLearner extends AbstractLearner {

	private final ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>	alignmentGenlex;
	private final boolean																																			pruneLexicon;

	private final BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>>		votingProcedure;

	protected final ILexiconImmutable<LogicalExpression>																											entriesNotToPrune;

	/**
	 * The indices of all data items that were reachable during the last epoch.
	 * This information is collected during pruning and is used to skip GENLEX
	 * for all reachable entries. Following GENLEX, this set is cleared.
	 */
	protected final Set<Integer>																																	reachableDuringLastEpoch	= new HashSet<>();

	public AbstractBatchLearner(int numIterations,
			IDataCollection<LabeledAmrSentence> trainingData, boolean sortData,
			int maxSentenceLength, GraphAmrParser parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			ICategoryServices<LogicalExpression> categoryServices,
			ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IntConsumer postIteration, boolean pruneLexicon,
			BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> votingProcedure,
			IWeightUpdateProcedure estimator,
			IGradientFunction gradientFunction,
			Integer conditionedInferenceBeam,
			ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> alignmentGenlex,
			boolean resumedLearning,
			ILexiconImmutable<LogicalExpression> entriesNotToPrune) {
		super(numIterations, trainingData, sortData, maxSentenceLength, parser,
				parserOutputLogger, categoryServices, genlex, filterFactory,
				postIteration, estimator, gradientFunction,
				conditionedInferenceBeam, resumedLearning);
		this.pruneLexicon = pruneLexicon;
		this.votingProcedure = votingProcedure;
		this.alignmentGenlex = alignmentGenlex;
		this.entriesNotToPrune = entriesNotToPrune;
		LOG.info("Init %s: pruneLexicon=%s", this.getClass().getSimpleName(),
				pruneLexicon);
		LOG.info("Init %s: votingProcedure=%s", this.getClass().getSimpleName(),
				votingProcedure);
		LOG.info("Init %s: alignmentGenlex=%s", this.getClass().getSimpleName(),
				alignmentGenlex);
		LOG.info("Init %s: size(entriesNotToPrune)=%d",
				this.getClass().getSimpleName(), entriesNotToPrune.size());
	}

	private static Map<LexicalEntry<LogicalExpression>, Double> aggregateVotes(
			List<ILexiconImmutable<LogicalExpression>> generatedLexicons) {
		final Map<LexicalEntry<LogicalExpression>, DoubleAccumulator> votes = new HashMap<>();

		for (final ILexiconImmutable<LogicalExpression> generatedLexicon : generatedLexicons) {

			// Index by tokens.
			final Map<TokenSeq, List<LexicalEntry<LogicalExpression>>> tokenEntries = new HashMap<>();
			for (final LexicalEntry<LogicalExpression> entry : generatedLexicon
					.toCollection()) {
				final TokenSeq tokens = entry.getTokens();
				if (tokenEntries.containsKey(tokens)) {
					tokenEntries.get(tokens).add(entry);
				} else {
					final List<LexicalEntry<LogicalExpression>> entries = new LinkedList<>();
					entries.add(entry);
					tokenEntries.put(tokens, entries);
				}
			}

			// Update votes. Each data item (generated lexicon) distributes a
			// total of 1.0 between all entries of the same token sequence.
			for (final List<LexicalEntry<LogicalExpression>> entries : tokenEntries
					.values()) {
				final int size = entries.size();
				for (final LexicalEntry<LogicalExpression> entry : entries) {
					final double vote = 1.0 / size;
					if (votes.containsKey(entry)) {
						votes.get(entry).add(vote);
					} else {
						votes.put(entry, new DoubleAccumulator(vote));
					}
				}
			}
		}

		return votes.entrySet().stream().collect(
				Collectors.toMap(e -> e.getKey(), e -> e.getValue().value()));
	}

	@Override
	public void train(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {

		if (resumedLearning) {
			LOG.warn("RESUMED LEARNING: skipping update procedure init");
		} else {
			updateProcedure.init();
		}

		// Init GENLEX.
		LOG.info("Initializing GENLEX ...");
		if (genlex != null) {
			genlex.init(model);
			if (alignmentGenlex != null) {
				alignmentGenlex.init(model);
			}
		}

		// Add high-precision heuristic alignment entries to the model. This is
		// usually done at the end of each epoch, but we also do it once before
		// learning starts.
		addAlignmentEntries(-1, model);

		// Epochs.
		for (int epochNumber = 0; epochNumber < epochs; ++epochNumber) {
			// Training epoch, iterate over all training samples in batch.
			final long epochStartTime = System.currentTimeMillis();

			// Batch lexical induction.
			lexiconInduction(epochNumber, model);

			// Batch parameter update.
			final Set<LexicalEntry<LogicalExpression>> viterbiEntries = parameterEstimation(
					epochNumber, model);

			// Prune the lexicon using the entries from parameter estimation
			pruneLexicon(epochNumber, model, viterbiEntries);

			// Add high-precision heuristic alignment entries to the model.
			addAlignmentEntries(epochNumber, model);

			// Output epoch statistics.
			LOG.info("Epoch time: %.2fsec",
					(System.currentTimeMillis() - epochStartTime) / 1000.0);
			LOG.info("Epoch stats:");
			LOG.info(stats);

			// Run the post-iteration job.
			postIteration.accept(epochNumber);
		}

	}

	private void lexiconInduction(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		LOG.info("=========================");
		LOG.info("Training epoch %d: Lexicon Induction", epochNumber);
		LOG.info("=========================");

		if (genlex == null) {
			LOG.info("No GENLEX procedure -- skipping lexical induction");
			return;
		}

		// Init GENLEX procedure. We do it every time. Pruning may have removed
		// some templates from the model's lexicon. If this is the case, we must
		// update the GENLEX procedures to ignore this
		// template during generation. We re-initialize the GENLEX procedure
		// with the updated model. In a nut shell, this will remove all
		// templates and re-add all known templates from the lexicon.
		LOG.info("Re-init GENLEX procedure ...");
		genlex.init(model);
		if (alignmentGenlex != null) {
			alignmentGenlex.init(model);
		}

		final long batchStartTime = System.currentTimeMillis();

		doLexiconInduction(epochNumber, model);

		// Output batch statistics.
		LOG.info("Lexical induction time: %.2f",
				(System.currentTimeMillis() - batchStartTime) / 1000.0);
		LOG.info("System memory: %s", MemoryReport.generate());

		// Clear the set of reachable items that was used to skip GENLEX.
		reachableDuringLastEpoch.clear();
	}

	private Set<LexicalEntry<LogicalExpression>> parameterEstimation(
			int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		LOG.info("=========================");
		LOG.info("Training epoch %d: Parameter Estimation", epochNumber);
		LOG.info("=========================");

		final long batchStartTime = System.currentTimeMillis();

		final Set<LexicalEntry<LogicalExpression>> viterbiEntries = doParameterEstimation(
				model, epochNumber);

		// Output batch statistics.
		LOG.info("Parameter estimation time: %.2f",
				(System.currentTimeMillis() - batchStartTime) / 1000.0);
		LOG.info("System memory: %s", MemoryReport.generate());

		return viterbiEntries;
	}

	private Set<LexicalEntry<LogicalExpression>> vote(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			List<ILexiconImmutable<LogicalExpression>> generatedLexicons) {
		// For each lexical entry, get the list of data items that proposed it.
		// This is used for logging only.
		int i = 0;
		final Map<LexicalEntry<LogicalExpression>, List<Integer>> genertingDataItems = new HashMap<>();
		for (final ILexiconImmutable<LogicalExpression> lexicon : generatedLexicons) {
			for (final LexicalEntry<LogicalExpression> entry : lexicon
					.toCollection()) {
				if (genertingDataItems.containsKey(entry)) {
					genertingDataItems.get(entry).add(i);
				} else {
					final List<Integer> dataItemsIndices = new LinkedList<>();
					dataItemsIndices.add(i);
					genertingDataItems.put(entry, dataItemsIndices);
				}
			}
			++i;
		}

		// Get the votes.
		final Map<LexicalEntry<LogicalExpression>, Double> votes = aggregateVotes(
				generatedLexicons);

		// Apply the voting strategy.
		final Predicate<LexicalEntry<LogicalExpression>> hardFilter = entry -> !model
				.getLexicon().contains(entry) && genlex.isGenerated(entry)
				&& !entry.isDynamic();
		final Set<LexicalEntry<LogicalExpression>> votedEntries = votingProcedure
				.apply(hardFilter, votes);

		// Make sure that the set of votedEntries doesn't contain any entries
		// the hard filter should prune.
		final Set<LexicalEntry<LogicalExpression>> toAdd = votedEntries.stream()
				.filter(hardFilter).collect(Collectors.toSet());

		// Sort the generated entries according to word and vote for logging.
		final List<Entry<LexicalEntry<LogicalExpression>, Double>> sorted = votes
				.entrySet().stream().sorted((e1, e2) -> {
					final int stringComparison = e1.getKey().getTokens()
							.toString().compareToIgnoreCase(
									e2.getKey().getTokens().toString());
					return stringComparison == 0
							? Double.compare(e2.getValue(), e1.getValue())
							: stringComparison;
				}).collect(Collectors.toList());

		// Log all generated entries and mark voted ones.
		int skipped = 0;
		int adding = 0;
		LOG.info("Entries generated [%d]:", sorted.size());
		for (final Entry<LexicalEntry<LogicalExpression>, Double> entryVote : sorted) {
			final double vote = entryVote.getValue();
			final LexicalEntry<LogicalExpression> entry = entryVote.getKey();
			final String generatingItemsString = genertingDataItems.get(entry)
					.stream().map(index -> index.toString())
					.collect(Collectors.joining(", "));
			final boolean voted = toAdd.contains(entry);
			final boolean known = model.getLexicon().contains(entry);
			if (voted) {
				++adding;
			} else if (!known) {
				++skipped;
			}
			if (known) {
				// Existing entry.
				LOG.info("[%f - exists] {%s} %s <- {%s}", vote,
						entry.getOrigin(), entry, generatingItemsString);
			} else if (genlex.isGenerated(entry)) {
				LOG.info("[%f%s] {%s} %s <- {%s}", vote, voted ? " - ADD" : "",
						entry.getOrigin(), entry, generatingItemsString);
			} else {
				// Dynamically generated entry.
				LOG.info("[%f - dynamic] {%s} %s <- {%s}", vote,
						entry.getOrigin(), entry, generatingItemsString);
			}

		}
		LOG.info("Adding %d lexical entries", adding);
		LOG.info("Skipped adding %d entries", skipped);
		return toAdd;
	}

	protected boolean addAlignmentEntries(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		if (genlex != null && alignmentGenlex != null) {
			LOG.info("=========================");
			LOG.info("Training epoch %d: Adding Alignments", epochNumber);
			LOG.info("=========================");

			boolean modelChanged = false;
			// Only add new entries if there's a GENLEX procedure.
			LOG.info("Adding alignment entries to model...");
			final int originalSize = model.getLexicon().size();
			for (final LabeledAmrSentence dataItem : trainingData) {
				// Update the model.
				final Collection<LexicalEntry<LogicalExpression>> entries = alignmentGenlex
						.generate(dataItem, model, categoryServices)
						.toCollection();
				for (final LexicalEntry<LogicalExpression> entry : entries) {
					if (model.addLexEntry(entry)) {
						LOG.info("Added alignment entry: [%s / %.2f] %s [%s]",
								entry.getOrigin(), model.score(entry), entry,
								model.getTheta().printValues(
										model.computeFeatures(entry)));
						modelChanged = true;
					}
				}
			}
			LOG.info(
					"Added %d alignment entries to the model (lexicon size: %d -> %d)",
					model.getLexicon().size() - originalSize, originalSize,
					model.getLexicon().size());
			return modelChanged;
		} else {
			return false;
		}
	}

	abstract protected void doLexiconInduction(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model);

	/**
	 * Parameter update method.
	 */
	protected abstract Set<LexicalEntry<LogicalExpression>> doParameterEstimation(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			int epochNumber);

	protected boolean pruneLexicon(int epochNumber,
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			Set<LexicalEntry<LogicalExpression>> viterbiEntries) {
		LOG.info("=========================");
		LOG.info("Training epoch %d: Pruning", epochNumber);
		LOG.info("=========================");

		if (genlex == null) {
			LOG.info(
					"No GENLEX procedure for lexical learning -- skipping pruning");
			return false;
		}

		if (!pruneLexicon) {
			LOG.info("Pruning disabled -- skipping");
			return false;
		}

		final long startTime = System.currentTimeMillis();

		final Set<LexicalEntry<LogicalExpression>> entriesToRetain = new HashSet<>(
				entriesNotToPrune.toCollection());
		entriesToRetain.addAll(viterbiEntries);

		// In addition to retaining all viterbi entries and seed entries
		// (entriesNotToPrune) we also need to retain all lexemes generated from
		// alignments. However, if we simply add them to the set of entries to
		// retain, it will make pruning templates nearly impossible. Templates
		// are pruned only when all the lexemes using them are pruned.
		// Therefore, we prune them, but then re-add them. This happens at the
		// end of each training epoch in the main train() loop.

		final int originalLexiconSize = model.getLexicon().size();

		LOG.info("Pruning the lexicon ...");
		model.getLexicon().retainAll(entriesToRetain);

		final int newLexiconSize = model.getLexicon().size();
		LOG.info("Pruned the lexicon: %d -> %d", originalLexiconSize,
				newLexiconSize);

		// Output batch statistics.
		LOG.info("Pruning time: %.2fsec",
				(System.currentTimeMillis() - startTime) / 1000.0);
		LOG.info("System memory: %s", MemoryReport.generate());

		return originalLexiconSize != newLexiconSize;
	}

	protected boolean updateModel(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			List<ILexiconImmutable<LogicalExpression>> generatedLexicons) {

		final Set<LexicalEntry<LogicalExpression>> votedEntries = vote(model,
				generatedLexicons);

		// Update the model's lexicon with generated lexical
		// entries from the max scoring valid generation parses.
		boolean modelChanged = false;
		for (final LexicalEntry<LogicalExpression> entry : votedEntries) {

			// TODO Having this constraint here is super ugly, should be removed
			// to an external filter.

			// Entries that are simple references (semantics == ref:id) tend to
			// be extremely noisy, so we don't learn with a hard constraint.
			// However, doing this here allows us to still get a parse and maybe
			// other entries from the sentence.
			if (entry.getCategory().getSemantics() != null && AMRServices
					.isRefLiteral(entry.getCategory().getSemantics())) {
				LOG.info("Skipping pure reference entry: %s", entry);
				continue;
			}

			if (model.addLexEntry(LexiconGenerationServices.unmark(entry))) {
				LOG.info("Added lexical entry to model: [%s] %s [%s]",
						entry.getOrigin(), entry, model.getTheta()
								.printValues(model.computeFeatures(entry)));
				modelChanged = true;
			}
			// Lexical generators might link related lexical
			// entries, so if we add the original one, we
			// should also add all its linked ones
			for (final LexicalEntry<LogicalExpression> linkedEntry : entry
					.getLinkedEntries()) {
				if (model.addLexEntry(
						LexiconGenerationServices.unmark(linkedEntry))) {
					LOG.info(
							"Added linked lexical entry to model: [%s] %s [%s]",
							entry.getOrigin(), linkedEntry,
							model.getTheta().printValues(
									model.computeFeatures(linkedEntry)));
					modelChanged = true;
				}
			}
		}
		return modelChanged;
	}

}
