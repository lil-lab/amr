package edu.uw.cs.lil.amr.learn.genlex;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.concurrency.ITinyExecutor;
import edu.cornell.cs.nlp.spf.base.concurrency.Shutdownable;
import edu.cornell.cs.nlp.spf.base.concurrency.TinyExecutorService;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.ComplexSyntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Slash;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.Lexeme;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.CKYDerivation;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.CKYParserOutput;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.chart.AbstractCellFactory;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.chart.Cell;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.chart.Chart;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.genlex.MarkedCell;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.genlex.MarkedCellFactory;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.multi.SpanLock;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.AbstractCKYStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.CKYLexicalStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.CKYParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.IWeightedCKYStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.WeightedCKYLexicalStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.steps.WeightedCKYParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IDataItemModel;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelListener;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.BinaryRuleSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryReversibleParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryReversibleParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.Span;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleSet;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.graph.IJointGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.collections.MapUtils;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.cornell.cs.nlp.utils.collections.iterators.FilteredIterator;
import edu.cornell.cs.nlp.utils.collections.queue.OrderInvariantDirectAccessBoundedQueue;
import edu.cornell.cs.nlp.utils.composites.Triplet;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.log.thread.LoggingRunnable;
import edu.cornell.cs.nlp.utils.log.thread.LoggingThreadFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.StripOverload;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.GraphAmrParserOutput;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationSyntax;

public class SplittingGenlex implements
		ILexiconGeneratorPrecise<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>,
		Shutdownable, IModelListener<LogicalExpression> {

	public static final ILogger																																		LOG					= LoggerFactory
			.create(SplittingGenlex.class);
	private static final long																																		serialVersionUID	= 148723601846569303L;

	private final boolean																																			bankSpanConstraint;

	private final int																																				beam;
	private final List<IBinaryReversibleParseRule<LogicalExpression>>																								binaryRules;
	private final Syntax																																			completeSentenceSyntax;
	private final boolean																																			conservative;
	private transient ITinyExecutor																																	executor;
	private final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>											filterFactory;
	private final ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>	genlex;
	private final boolean																																			hardSuperTagConstraint;
	private final Predicate<Category<LogicalExpression>>																											heuristicFilter;
	private final Integer																																			maxMarking;

	private final Integer																																			numThreads;

	private final String																																			origin;

	private final GraphAmrParser																																	parser;

	/**
	 * Parser output logger.
	 */
	private final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>																		parserOutputLogger;
	/**
	 * Skip lexical generation for sentences reachable under the current
	 * grammar. Skipping GENLEX trades in exploring the grammar space for
	 * learning speed.
	 */
	private final boolean																																			skipReachable;

	private final Integer																																			splitArgThreshold;

	private final int																																				splitEntriesTokenLimit;

	private final boolean																																			splitLexemes;

	private final String																																			threadName;

	private final List<IUnaryReversibleParseRule<LogicalExpression>>																								unaryRules;

	public SplittingGenlex(int beam,
			List<IBinaryReversibleParseRule<LogicalExpression>> binaryRules,
			Syntax completeSentenceSyntax,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			ILexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>> genlex,
			String origin, GraphAmrParser parser,
			List<IUnaryReversibleParseRule<LogicalExpression>> unaryRules,
			int splitEntriesTokenLimit, Integer maxMarking,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			boolean skipReachable, boolean hardSuperTagConstraint,
			Integer splitArgThreshold, boolean conservative, Integer numThreads,
			String threadName,
			Predicate<Category<LogicalExpression>> heuristicFilter,
			boolean splitLexemes, boolean bankSpanConstraint) {
		this.numThreads = numThreads;
		this.threadName = threadName;
		this.heuristicFilter = heuristicFilter;
		this.splitLexemes = splitLexemes;
		this.bankSpanConstraint = bankSpanConstraint;
		this.executor = new TinyExecutorService(
				numThreads == null ? Runtime.getRuntime().availableProcessors()
						: numThreads,
				new LoggingThreadFactory(threadName),
				ITinyExecutor.DEFAULT_MONITOR_SLEEP);
		this.beam = beam;
		this.binaryRules = binaryRules;
		this.completeSentenceSyntax = completeSentenceSyntax;
		this.filterFactory = filterFactory;
		this.genlex = genlex;
		this.parser = parser;
		this.unaryRules = unaryRules;
		this.splitEntriesTokenLimit = splitEntriesTokenLimit;
		this.origin = origin;
		this.maxMarking = maxMarking;
		this.parserOutputLogger = parserOutputLogger;
		this.skipReachable = skipReachable;
		this.hardSuperTagConstraint = hardSuperTagConstraint;
		this.splitArgThreshold = splitArgThreshold;
		this.conservative = conservative;
		LOG.info("Init %s :: beam=%d, completeSentenceSyntax=%s ...",
				getClass(), beam, completeSentenceSyntax);
		LOG.info("Init %s :: ... binaryRules=%s ...", getClass(), binaryRules);
		LOG.info("Init %s :: ... maxMarking=%s ...", getClass(), maxMarking);
		LOG.info("Init %s :: ... skipReachable=%s ...", getClass(),
				skipReachable);
		LOG.info("Init %s :: ... hardSuperTagConstraint=%s ...", getClass(),
				hardSuperTagConstraint);
		LOG.info("Init %s :: ... splitArgThreshold=%d ...", getClass(),
				splitArgThreshold);
		LOG.info("Init %s :: ... conservative=%s", getClass(), conservative);
		LOG.info("Init %s :: ... numThreads=%s", getClass(), numThreads);
	}

	public static void logParse(
			IJointGraphDerivation<LogicalExpression, LogicalExpression> derivation,
			IDataItemModel<LogicalExpression> dataItemModel) {
		for (final IWeightedParseStep<LogicalExpression> step : derivation
				.getMaxSteps()) {
			LOG.info("\t%s",
					step.toString(false, false, dataItemModel.getTheta()));
		}

	}

	/**
	 * Creates a new cell given the step. Copies from the given cell its marking
	 * properties, if exist. Also performs some assertions.
	 */
	private static Cell<LogicalExpression> cloneCellWithStep(
			Cell<LogicalExpression> cell,
			IWeightedCKYStep<LogicalExpression> step,
			AbstractCellFactory<LogicalExpression> factory) {
		assert cell instanceof MarkedCell == factory instanceof MarkedCellFactory : "If we have marked cell factory, all generated cells should be marked, and vice versa";
		if (cell instanceof MarkedCell
				&& factory instanceof MarkedCellFactory) {
			return ((MarkedCellFactory<LogicalExpression>) factory).create(step,
					((MarkedCell<?>) cell).getNumMarkedLexicalEntries());
		} else {
			return factory.create(step);
		}
	}

	private static boolean ignore(Cell<LogicalExpression> cell) {
		return AMRServices.KEY.equals(cell.getCategory().getSyntax())
				|| cell.getCategory().getSyntax() instanceof CoordinationSyntax;
	}

	private static boolean isSlashNormalForm(Syntax syntax) {
		boolean forward = true;
		Syntax current = syntax;
		while (current instanceof ComplexSyntax) {
			final ComplexSyntax complex = (ComplexSyntax) current;
			if (forward && complex.getSlash().equals(Slash.FORWARD)
					|| !forward && complex.getSlash().equals(Slash.BACKWARD)) {
				current = complex.getLeft();
			} else if (forward && complex.getSlash().equals(Slash.BACKWARD)) {
				forward = false;
				current = complex.getLeft();
			} else {
				LOG.debug("Category syntax is not NF (slash ordering): %s",
						syntax);
				return false;
			}
		}
		return true;
	}

	/**
	 * Verifies that if the syntax includes variables, there at least two of
	 * them and they create an agreement constraint (otherwise, there's no point
	 * in them).
	 */
	private static boolean isValidSyntaxVariables(Syntax syntax) {
		if (syntax instanceof ComplexSyntax) {
			final ComplexSyntax complex = (ComplexSyntax) syntax;
			if (complex.getRight().hasAttributeVariable()) {
				// Either the variables must create an agreement constraint with
				// the left side, or there should be an agreement within the
				// right side.
				return complex.getLeft().hasAttributeVariable()
						|| isValidSyntaxVariables(complex.getRight());
			} else {
				return isValidSyntaxVariables(complex.getLeft());
			}
		} else {
			return !syntax.hasAttributeVariable();
		}
	}

	private static void logParse(IGraphDerivation<LogicalExpression> derivation,
			IDataItemModel<LogicalExpression> dataItemModel) {
		for (final IWeightedParseStep<LogicalExpression> step : derivation
				.getMaxSteps()) {
			LOG.info("\t%s",
					step.toString(false, false, dataItemModel.getTheta()));
		}
	}

	/**
	 * Tries to queue the generated cell and update its parent cell, which is in
	 * the queue. Assumes the both the span of the root and the generated cell
	 * are locked.
	 *
	 * @param queues
	 */
	private static boolean updateQueues(GeneratedSplit split,
			OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][] queues) {
		final OrderInvariantDirectAccessBoundedQueue<CellWrapper> queue = queues[split.newCell
				.getStart()][split.newCell.getEnd()];
		final CellWrapper wrapper = new CellWrapper(split.newCell, split.step,
				split.siblingCell, split.rootCellWrapper);
		LOG.debug("Trying to queue: %s", wrapper);
		LOG.debug("Root: %s", split.rootCellWithStep);
		LOG.debug("Cell wrapper: outside score=%.4f, cell viterbi score=%.4f",
				wrapper.viterbiOutsideScore, wrapper.viterbiInsideScore);
		boolean queued = false;
		if (queue.contains(wrapper)) {
			LOG.debug("Adding to existing queued object");
			queued = true;
			final CellWrapper existing = queue.get(wrapper);
			final double preAddScore = existing.score();
			existing.addCell(split.newCell, split.step, split.siblingCell,
					split.rootCellWrapper);
			if (preAddScore != existing.score()) {
				assert existing.score() > preAddScore;
				LOG.debug("Score increased: %f -> %f", preAddScore,
						existing.score());
				queue.remove(existing);
				if (!queue.offer(existing)) {
					throw new IllegalStateException(
							"Update is monotonic, so offer mustn't fail");
				}
			} else {
				assert preAddScore >= wrapper.score();
				LOG.debug("Score didn't change: %f >= %f", preAddScore,
						wrapper.score());
			}
		} else {
			LOG.debug("Offering a new object to the queue");
			queued = queue.offer(wrapper);
		}

		if (queued) {
			LOG.debug("Cell queued");
			// Update the root in the parent span queue.
			final CellWrapper queuedRoot = queues[split.rootCellWithStep
					.getStart()][split.rootCellWithStep.getEnd()]
							.get(split.rootCellWrapper);
			// The root must be in the queue. Else, there's a bug. Also, this
			// step must be new to the root, otherwise the procedure is not
			// configured correctly and we have repetitions.
			assert queuedRoot != null;
			assert !SetUtils.isIntersecting(queuedRoot.getSteps(),
					split.rootCellWithStep.getSteps());
			// This changes the score of the CellWrapper of the root. But it
			// basically only increases it and it the queue is static, so we
			// don't update the wrapper's score. If we would, we would have to
			// propagate it up the queues and that would make this process
			// intractable.
			queuedRoot.steps.addAll(split.rootCellWithStep.getSteps());
			LOG.debug("Updated queued root: %s", queuedRoot);
			return true;
		} else {
			LOG.debug("Failed to queue, queue size=%d, queue has %sthreashold",
					queue.size(), queue.hasThreshold() ? "" : "no ");
			return false;
		}
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
		return executor.awaitTermination(timeout, unit);
	}

	@Override
	public ILexiconImmutable<LogicalExpression> generate(
			LabeledAmrSentence dataItem,
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			ICategoryServices<LogicalExpression> categoryServices) {

		final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
				.createJointDataItemModel(dataItem.getSample());
		final IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter = filterFactory
				.createJointFilter(dataItem);

		// Skip if can parse this sentence.
		if (skipReachable) {
			final List<GraphAmrDerivation> currentMaxDerivations = parser
					.parse(dataItem.getSample(), dataItemModel, filter, beam)
					.getMaxDerivations(dataItem.getLabel());
			if (!currentMaxDerivations.isEmpty()) {
				LOG.info(
						"Skipping GENLEX -- sentence is parseable under the current grammar");
				final Set<LexicalEntry<LogicalExpression>> viterbiEntries = new HashSet<>();
				for (final GraphAmrDerivation derivation : currentMaxDerivations) {
					viterbiEntries.addAll(derivation.getMaxLexicalEntries());
				}
				return new Lexicon<>(viterbiEntries);
			}
		}

		// Use the base GENLEX procedure and parsing to generate lexical
		// entries using bottom-up beam search.
		final long genlexStart = System.currentTimeMillis();
		final ILexiconImmutable<LogicalExpression> generatedLexicon = genlex
				.generate(dataItem, model, categoryServices);

		final int generatedLexiconSize = generatedLexicon.size();

		LOG.info("Splitting base GENLEX generated %d entries (%.2fsec)",
				generatedLexiconSize,
				(System.currentTimeMillis() - genlexStart) / 1000.0);

		if (generatedLexiconSize == 0) {
			LOG.info("No base GENLEX entries, skipping splitting GENLEX");
			return new Lexicon<>();
		}

		// Wrap the lexicon to use the heuristic filter when providing entries.
		final ILexiconImmutable<LogicalExpression> heuristicallyFiltered = new ILexiconImmutable<LogicalExpression>() {

			private static final long serialVersionUID = 3150940578433056294L;

			@Override
			public boolean contains(LexicalEntry<LogicalExpression> lex) {
				return generatedLexicon.contains(lex);
			}

			@Override
			public Iterator<? extends LexicalEntry<LogicalExpression>> get(
					TokenSeq tokens) {
				final Iterator<? extends LexicalEntry<LogicalExpression>> base = generatedLexicon
						.get(tokens);
				return new FilteredIterator<LexicalEntry<LogicalExpression>>(
						e -> heuristicFilter.test(e.getCategory()), base);
			}

			@Override
			public int size() {
				return generatedLexiconSize;
			}

			@Override
			public Collection<LexicalEntry<LogicalExpression>> toCollection() {
				return generatedLexicon.toCollection();
			}
		};

		// Parse with generated lexicon.
		final GraphAmrParserOutput parserOutput = parser.parse(
				dataItem.getSample(), dataItemModel, filter, false,
				heuristicallyFiltered, beam);
		LOG.info("GENLEX parsing time: %.4fsec",
				parserOutput.getInferenceTime() / 1000.0);
		LOG.info("Output is %s",
				parserOutput.isExact() ? "exact" : "approximate");
		if (parserOutputLogger != null) {
			parserOutputLogger.log(parserOutput, dataItemModel,
					"genlex-" + System.currentTimeMillis());
		}

		final List<GraphAmrDerivation> correctDerivations = parserOutput
				.getDerivations(dataItem.getLabel());
		if (!correctDerivations.isEmpty()) {
			LOG.info("Created %d GENLEX parses for training sample",
					correctDerivations.size());
			// Case bottom-up base GENLEX and bottom-up search was able to
			// generate a correct parse. Get the max-scoring lexical entries
			// from all correct max-scoring parses.
			final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
			// Collect max scoring correct generation parses.
			double currentMaxModelScore = -Double.MAX_VALUE;
			for (final GraphAmrDerivation derivation : correctDerivations) {
				logParse(derivation, dataItemModel);
				if (derivation.getViterbiScore() > currentMaxModelScore) {
					currentMaxModelScore = derivation.getViterbiScore();
					entries.clear();
					entries.addAll(derivation.getMaxLexicalEntries());
				} else if (derivation
						.getViterbiScore() == currentMaxModelScore) {
					entries.addAll(derivation.getMaxLexicalEntries());
				}
			}
			return new Lexicon<>(entries);
		} else {
			LOG.info(
					"Created no GENLEX parses, continuing with top-bottom splitting");
		}

		// If the base GENLEX procedure failed, we try to bridge the gap with
		// top-down recursive splitting process.

		// Get the chart. This is not type-safe, but will do for now. If it
		// throws an exception, you are using an unsupported parser and didn't
		// configure your system correctly.
		final Chart<LogicalExpression> chart = ((CKYParserOutput<LogicalExpression>) parserOutput
				.getBaseParserOutput()).getChart();

		// The semantics and category of the initial root.
		final LogicalExpression initSemantics = AMRServices
				.underspecifyAndStrip(dataItem.getLabel());
		final Category<LogicalExpression> initCategory = Category
				.create(completeSentenceSyntax, initSemantics);

		// Create the splitting chart. CAUTION: the result chart is
		// inconsistent. See createSplittingChart() javadoc and code for
		// details.
		final Chart<LogicalExpression> splittingChart = createSplittingChart(
				chart, dataItemModel, initCategory, dataItem);
		if (splittingChart == null) {
			// Splitting failed, return empty lexicon.
			LOG.info("Splitting failed");
			return new Lexicon<>();
		}

		// Collect all entries from top complete parses.
		final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
		double maxScore = -Double.MAX_VALUE;
		final List<CKYDerivation<LogicalExpression>> splittingDerivations = splittingChart
				.getParseResults();
		LOG.info("Generated %d splitting derivations:",
				splittingDerivations.size());
		for (final CKYDerivation<LogicalExpression> derivation : splittingDerivations) {
			logParse(derivation, dataItemModel);
			if (derivation.getCategory().equals(initCategory)) {
				if (derivation.getScore() > maxScore) {
					entries.clear();
					maxScore = derivation.getScore();
					entries.addAll(derivation.getMaxLexicalEntries());
				} else if (derivation.getScore() == maxScore) {
					entries.addAll(derivation.getMaxLexicalEntries());
				}
				// Some assertion to make sure nothing bad happened.
				assert derivation.getAllLexicalEntries().stream()
						.filter((e) -> origin.equals(e.getOrigin()))
						.collect(Collectors
								.counting()) != 0 : "Derivation without splitting entries";

				assert derivation.getMaxLexicalEntries().stream()
						.filter((e) -> origin.equals(e.getOrigin()))
						.collect(Collectors
								.counting()) != 0 : "Derivation without splitting max entries";
			}
		}

		LOG.debug(() -> {
			if (!entries.isEmpty()) {
				LOG.debug("Recursive splitting (%s) entries:", origin);
				for (final LexicalEntry<LogicalExpression> entry : entries) {
					LOG.debug("%s {%s}", entry, entry.getOrigin());
				}
			}
		});

		if (conservative) {
			// If the set of entries includes more than one splitting entry, be
			// conservative and return an empty set.
			final Set<LexicalEntry<LogicalExpression>> splittingEntries = entries
					.stream()
					.filter(e -> origin.equals(e.getOrigin())
							&& !model.getLexicon().contains(e))
					.collect(Collectors.toSet());

			if (splittingEntries.size() > 1) {
				LOG.info(
						"Too many recursive splitting entries [%d], skipping generation:",
						splittingEntries.size());
				for (final LexicalEntry<LogicalExpression> entry : splittingEntries) {
					LOG.info(entry);
				}
				return new Lexicon<>();
			}
		}

		return new Lexicon<>(entries);
	}

	@Override
	public void init(
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		genlex.init(model);
	}

	@Override
	public boolean isGenerated(LexicalEntry<LogicalExpression> entry) {
		return origin.equals(entry.getOrigin()) || genlex.isGenerated(entry);
	}

	@Override
	public boolean isShutdown() {
		return executor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return executor.isTerminated();
	}

	@Override
	public void lexicalEntriesAdded(
			Collection<LexicalEntry<LogicalExpression>> entries) {
		for (final LexicalEntry<LogicalExpression> entry : entries) {
			lexicalEntryAdded(entry);
		}
	}

	@Override
	public void lexicalEntriesAdded(ILexicon<LogicalExpression> entries) {
		lexicalEntriesAdded(entries.toCollection());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void lexicalEntryAdded(LexicalEntry<LogicalExpression> entry) {
		if (genlex instanceof IModelListener) {
			((IModelListener<LogicalExpression>) genlex)
					.lexicalEntryAdded(entry);
		}
	}

	@Override
	public void shutdown() {
		executor.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return executor.shutdownNow();
	}

	/**
	 * @return Pair of a root with the new step, and the new cell.
	 */
	private GeneratedSplit createCellAndParent(
			Category<LogicalExpression> category,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			Chart<LogicalExpression> chart, Cell<LogicalExpression> otherCell,
			boolean isLeft, Split split, CellWrapper rootWrapper,
			UnaryRuleName rootUnaryRule, RuleName rule,
			LabeledAmrSentence dataItem, Predicate<Lexeme> lexemeFilter) {

		final int start = isLeft ? split.span.getStart() : split.split + 1;
		final int end = isLeft ? split.split : split.span.getEnd();
		final AbstractCellFactory<LogicalExpression> cellFactory = chart
				.getCellFactory();

		// Compute the marking of the new cell.
		final Integer newCellMarking;
		if (cellFactory instanceof MarkedCellFactory
				&& rootWrapper.cell instanceof MarkedCell
				&& otherCell instanceof MarkedCell) {
			final int rootMarking = ((MarkedCell<LogicalExpression>) rootWrapper.cell)
					.getNumMarkedLexicalEntries();
			newCellMarking = rootMarking
					- ((MarkedCell<LogicalExpression>) otherCell)
							.getNumMarkedLexicalEntries();

			assert newCellMarking <= rootMarking : "Child marking exceeds parents marking -- bug";

			// Verify the marking of the new cell.
			// If the cell spans a single token, its marking must be one
			// (using an unexpected syntax super-tag "costs" one marking).
			// Otherwise, if the new cell spans a single token and its super-tag
			// is predicted by the super-tagger, its marking must be zero.
			if (start == end) {
				final Set<Syntax> superTags = dataItem.getSuperTags(start);
				boolean found = false;
				if (superTags != null) {
					for (final Syntax superTag : superTags) {
						if (category.getSyntax().unify(superTag) != null) {
							found = true;
							break;
						}
					}
				} else {
					found = true;
				}

				// Case super tags are used as hard constraints. If no matching
				// super tag was found, skip this category.
				if (hardSuperTagConstraint && !found) {
					LOG.debug(
							"Skip %s category, no matching super-tag found (hard constraint): %s",
							isLeft ? "left" : "right", category);
					return null;
				}

				if ((!found || newCellMarking != 0)
						&& (found || newCellMarking != 1)) {
					LOG.debug(
							"Unexpected cell marking (mark=%d, %s by super-tagger) -- ignoring: %s",
							newCellMarking,
							found ? "predicted" : "not predicted", category);
					return null;
				}
			}
		} else {
			newCellMarking = null;
		}

		final FactoredLexicalEntry factoredEntry = FactoringServices.factor(
				new LexicalEntry<>(chart.getTokens().sub(start, end + 1),
						category, false, MapUtils.createSingletonMap(
								LexicalEntry.ORIGIN_PROPERTY, origin)));

		// If the new entry spans a single token and its marking is zero, verify
		// that its lexeme is already known. If we only allow learning new
		// templates during splitting and require a known lexeme, the filter
		// captures this constraint. The marking constraint means that the rest
		// of the derivation doesn't include any marked (i.e., of the more
		// speculative type) entries, so if we are able to confidently cover the
		// entire tree, we allow a new lexeme in addition to a new template.
		if ((newCellMarking == null || newCellMarking == 0)
				&& factoredEntry.getTokens().size() <= 1
				&& !lexemeFilter.test(factoredEntry.getLexeme())) {
			return null;
		}

		// TODO Hard constraint to avoid learning junk templates that slow down
		// GENLEX. Need to consider if this is the right thing to do.

		// Only allow new lexemes (and templates) that include at most one
		// attribute. This is a hard constraint meant to avoid learning overly
		// complex templates, which are mostly wrong (given the simplified set
		// of syntactic attributes that we use) and slow down GENLEX
		// drastically. This constraint won't hold if we use a more complete set
		// of syntactic attributes.
		if (factoredEntry.getTokens().size() <= 1 && factoredEntry.getLexeme()
				.getSignature().getNumAttributes() > 1) {
			LOG.info("Skipping entry with multiple syntactic attributes: %s",
					factoredEntry);
			return null;
		}

		final WeightedCKYLexicalStep<LogicalExpression> newCellStep = new WeightedCKYLexicalStep<>(
				new CKYLexicalStep<>(factoredEntry, false, start, end),
				dataItemModel);
		final Cell<LogicalExpression> newCell = newCellMarking == null
				? cellFactory.create(newCellStep)
				: ((MarkedCellFactory<LogicalExpression>) cellFactory)
						.create(newCellStep, newCellMarking);
		LOG.debug("Generated %s cell: %s", isLeft ? "left" : "right",
				newCell.toString(false, null, true, dataItemModel.getTheta()));

		if (chart.contains(newCell)) {
			// Skip the cell if it exited in the original chart. This basically
			// enforces the constraint that at least one of the children must be
			// a new cell.
			LOG.debug("Skip adding %s cell, already exists",
					isLeft ? "left" : "right");
			return null;
		}

		final WeightedCKYParseStep<LogicalExpression> newStep = new WeightedCKYParseStep<>(
				new CKYParseStep<>(rootWrapper.cell.getCategory(),
						isLeft ? newCell : otherCell,
						isLeft ? otherCell : newCell,
						rootWrapper.cell.isFullParse(),
						rootUnaryRule == null ? rule
								: rule.overload(rootUnaryRule),
						rootWrapper.cell.getStart(), rootWrapper.cell.getEnd()),
				dataItemModel);
		final Cell<LogicalExpression> rootCellWithStep = cellFactory
				.create(newStep);
		LOG.debug("Root cell with step: %s", rootCellWithStep.toString(false,
				null, true, dataItemModel.getTheta()));
		return new GeneratedSplit(newCell, newStep, rootWrapper, otherCell,
				rootCellWithStep);
	}

	/**
	 * Creates a chart the compactly packs all parses generated by recursive
	 * splits. The chart is not completely consistent and it points to cells
	 * that are not stored in it. Therefore, it can't be used to compute various
	 * methods that rely on the dynamic programming structure. The chart is
	 * mostly used to compute the inside scores, which allow us to take the max
	 * parses.
	 *
	 * @return Inconsistent chart that compactly packs splitting parses.
	 */
	private Chart<LogicalExpression> createSplittingChart(
			Chart<LogicalExpression> chart,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			Category<LogicalExpression> initCategory,
			LabeledAmrSentence dataItem) {
		final AbstractCellFactory<LogicalExpression> cellFactory = chart
				.getCellFactory();

		final TokenSeq tokens = chart.getTokens();
		final int numTokens = tokens.size();

		// Create lexeme filter. The generator may be configured to only allow
		// learning new templates and only re-use existing lexemes, rather than
		// induce new ones. If this option is turned off, the filter will always
		// return true. Otherwise, the filter will validate that the lexeme
		// exists.
		final Predicate<Lexeme> lexemeFilter;
		if (splitLexemes) {
			lexemeFilter = (l) -> true;
		} else {
			// Construct the lexeme filter using all the known lexemes
			// applicable to this sentence.
			final Set<Lexeme> knownLexemes = new HashSet<>();
			for (int start = 0; start < numTokens; start++) {
				for (int end = start; end < numTokens; end++) {
					final TokenSeq lexemeTokens = chart.getTokens().sub(start,
							end + 1);
					final Iterator<? extends LexicalEntry<LogicalExpression>> iterator = dataItemModel
							.getLexicon().get(lexemeTokens);
					while (iterator.hasNext()) {
						final LexicalEntry<LogicalExpression> entry = iterator
								.next();
						knownLexemes.add(
								FactoringServices.factor(entry).getLexeme());
					}
				}
			}
			lexemeFilter = knownLexemes::contains;
		}

		// Create a separate queue for each span.
		@SuppressWarnings("unchecked")
		final OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][] queues = (OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][]) Array
				.newInstance(OrderInvariantDirectAccessBoundedQueue.class,
						numTokens, numTokens);
		final Comparator<? super CellWrapper> comparator = (o1, o2) -> {
			final int scoreComparison = Double.compare(o1.score(), o2.score());
			if (scoreComparison == 0) {
				// Break ties by prioritizing less complex
				// categories.
				return Integer.compare(
						o2.cell.getCategory().getSyntax().numSlashes(),
						o1.cell.getCategory().getSyntax().numSlashes());
			} else {
				return scoreComparison;
			}

		};
		for (int i = 0; i < numTokens; i++) {
			for (int j = i; j < numTokens; j++) {
				queues[i][j] = new OrderInvariantDirectAccessBoundedQueue<>(
						beam, comparator);
			}
		}

		// Initialize the process by adding a cell with the category
		// representing the complete parse to the chart.
		final WeightedCKYLexicalStep<LogicalExpression> initStep = new WeightedCKYLexicalStep<>(
				new CKYLexicalStep<>(
						FactoringServices.factor(new LexicalEntry<>(tokens,
								initCategory, false,
								MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY, origin))),
						true, 0, numTokens - 1),
				dataItemModel);

		// If we have a max marking value and the cell factory is of the right
		// type, create the initial cell with each possible marking value.
		final int numInitCells;
		if (maxMarking == null) {
			final Cell<LogicalExpression> initCell = cellFactory
					.create(initStep);
			// Add the initial cell to its queue.
			queues[0][numTokens - 1]
					.offer(new CellWrapper(initCell, null, null, null));
			numInitCells = 1;
		} else {
			for (int i = 0; i <= maxMarking; ++i) {
				final Cell<LogicalExpression> initCell = ((MarkedCellFactory<LogicalExpression>) cellFactory)
						.create(initStep, i);
				queues[0][numTokens - 1]
						.offer(new CellWrapper(initCell, null, null, null));
			}
			numInitCells = maxMarking + 1;
		}

		// Prepare a mapping of each span to all the splits it participates in.
		final Map<Span, Set<Split>> spans = new HashMap<>();
		// Create the set.
		for (int start = 0; start < numTokens; ++start) {
			for (int end = start; end < numTokens; ++end) {
				spans.put(new Span(start, end), new HashSet<>());
			}
		}
		// Populate the sets.
		for (int len = numTokens; len > 0; --len) {
			for (int start = 0; start <= numTokens - len; ++start) {
				final int end = start + len - 1;
				for (int split = start; split < end; ++split) {
					// Each split includes two spans.
					final Split splitObject = new Split(start, end, split);
					spans.get(splitObject.getLeft()).add(splitObject);
					spans.get(splitObject.getRight()).add(splitObject);
				}
			}
		}

		// Lock and listener.
		final SpanLock lock = new SpanLock(numTokens);
		final Listener listener = new Listener(chart, dataItemModel, lock,
				queues, spans, dataItem, lexemeFilter);

		// Only queue jobs and wait for them if there are actual splits.
		if (numTokens > 1) {
			try {
				synchronized (spans) {
					// Create all split jobs for the full span.
					for (int split = 0; split < numTokens - 1; ++split) {
						executor.execute(new SplitJob(chart, dataItemModel,
								listener, lock, queues,
								new Split(0, numTokens - 1, split), dataItem,
								lexemeFilter));
					}

					// Wait on the spans map.
					spans.wait();
				}
			} catch (final InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}

		// +1 for the initial root cell above.
		LOG.info("Created %d cells, %d were queued",
				listener.newCellsCreated + numInitCells,
				listener.cellsQueued + numInitCells);

		// Create a new chart from the queues. The chart is created bottom-up,
		// so the inside scores are computed appropriately. We also prune
		// lexical entries that are too long.
		final Chart<LogicalExpression> newChart = new Chart<>(chart.getTokens(),
				beam, cellFactory, false, false);
		for (int len = 0; len < numTokens; len++) {
			for (int start = 0; start < numTokens - len; start++) {
				final OrderInvariantDirectAccessBoundedQueue<CellWrapper> currentQueue = queues[start][start
						+ len];
				LOG.debug(
						"Processing queue (%d,%d) with %d items, queue has %sthreashold:",
						start, start + len, currentQueue.size(),
						currentQueue.hasThreshold() ? "" : "no ");
				LOG.debug(() -> {
					for (final CellWrapper entry : currentQueue) {
						LOG.debug(entry);
					}
				});
				for (final CellWrapper cellWrapper : currentQueue) {

					// If this cell is a marked cell and it has a value bigger
					// than one, it may allow a new lexeme. Basically, this
					// means that the rest of the derivation relies on known
					// lexemes, so we allow inventing both the lexeme and
					// template.
					final boolean cellHasCredit = cellWrapper.cell instanceof MarkedCell
							&& ((MarkedCell<LogicalExpression>) cellWrapper.cell)
									.getNumMarkedLexicalEntries() > 0;

					for (final IWeightedCKYStep<LogicalExpression> step : cellWrapper
							.getSteps()) {

						if (step instanceof ILexicalParseStep) {
							@SuppressWarnings("unchecked")
							final ILexicalParseStep<LogicalExpression> lexicalStep = (ILexicalParseStep<LogicalExpression>) step;
							assert lexicalStep
									.getLexicalEntry() instanceof FactoredLexicalEntry : "Splitting should only generate factored entries";
							final FactoredLexicalEntry entry = (FactoredLexicalEntry) lexicalStep
									.getLexicalEntry();
							// If the lexical entry was constructed from a new
							// lexeme, don't add it to the pruned chart. We only
							// allow learning new templates, so the lexeme must
							// be known.

							if (entry.getTokens()
									.size() <= splitEntriesTokenLimit
									&& (cellHasCredit || lexemeFilter
											.test(entry.getLexeme()))) {
								// Within token limit, add to pruned chart.
								final Cell<LogicalExpression> newCell = cloneCellWithStep(
										cellWrapper.cell, step, cellFactory);
								newChart.add(newCell);
								assert newChart.contains(newCell);
							}
						} else {
							boolean prune = false;
							for (final Cell<LogicalExpression> child : step) {
								if (!chart.contains(child)
										&& !newChart.contains(child)) {
									// We prune this cell if any of its children
									// is not available. This may happen if the
									// child was actively pruned, or doesn't
									// exist in the original chart or the new
									// chart (due to pruning in the queues).
									// Since we are doing this process
									// bottom-up, testing if it appears in the
									// newChart or the original chart is
									// sufficient.
									prune = true;
									break;
								}
							}
							if (!prune) {
								// The child cell can come either from the newly
								// constructed chart, or from the old original
								// chart. This is what makes this chart
								// inconsistent.
								final Cell<LogicalExpression> leftChild;
								if (step.numChildren() > 0) {
									final Cell<LogicalExpression> originalChild = step
											.getChildCell(0);
									leftChild = newChart.contains(originalChild)
											? newChart.getCell(originalChild)
											: chart.getCell(originalChild);
									assert leftChild != null;
								} else {
									leftChild = null;
								}
								final Cell<LogicalExpression> rightChild;
								if (step.numChildren() > 1) {
									final Cell<LogicalExpression> originalChild = step
											.getChildCell(1);
									rightChild = newChart
											.contains(originalChild) ? newChart
													.getCell(originalChild)
													: chart.getCell(
															originalChild);
									assert rightChild != null;
								} else {
									rightChild = null;
								}
								final Cell<LogicalExpression> newCell = cloneCellWithStep(
										cellWrapper.cell,
										new WeightedCKYParseStep<>(
												new CKYParseStep<>(
														step.getRoot(),
														leftChild, rightChild,
														step.isFullParse(),
														step.getRuleName(),
														step.getStart(),
														step.getEnd()),
												dataItemModel),
										cellFactory);
								newChart.add(newCell);
								assert newChart.contains(newCell);
							}
						}
					}
				}
			}
		}

		// Create a dummy parser output and log it.
		if (parserOutputLogger != null) {
			parserOutputLogger.log(new CKYParserOutput<>(newChart, 0),
					dataItemModel,
					"genlex-split-" + System.currentTimeMillis());
		}

		// Careful, this chart is inconsistent.
		return newChart;
	}

	private boolean isValidGeneratedCategory(
			Category<LogicalExpression> generatedCategory,
			LabeledAmrSentence dataItem, Span span) {
		// Heuristic filtering of the generated category.
		if (!heuristicFilter.test(generatedCategory)) {
			return false;
		}

		final Syntax syntax = generatedCategory.getSyntax();

		if (bankSpanConstraint) {
			// Validate against the spans of the CCGBank derivation.
			final TokenSeq tokens = dataItem.getSample().getTokens()
					.sub(span.getStart(), span.getEnd() + 1);
			final Set<Syntax> ccgBankCategories = dataItem
					.getCCGBankCategories(tokens);
			if (ccgBankCategories != null) {
				boolean found = false;
				if (!ccgBankCategories.isEmpty()) {
					for (final Syntax bankSyntax : ccgBankCategories) {
						if (syntax.stripAttributes()
								.equals(bankSyntax.stripAttributes())) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					LOG.debug("CCG Bank skipping: %s :- %s", tokens,
							generatedCategory);
					return false;
				}
			}
		}

		final int numArgs = syntax.numArguments();
		if (splitArgThreshold != null && numArgs > splitArgThreshold) {
			LOG.debug(
					"Rejected category -- number of arguments is over the threshold: %s -> %d > %d",
					syntax, numArgs, splitArgThreshold);
			return false;
		}

		// Allow categories that includes references only in two cases: (1) the
		// reference is the upper most element, or (2) the reference is in a
		// relation a conjunction which contains the unary typing literal -->
		// this translates to the following constraint on generated categories:
		// a literal with the predicate ref:<id,e> may only appear as part of
		// a conjunction with a the unary typing literal or as the complete
		// logical form.
		if (!TestReferenceConstraint.of(generatedCategory.getSemantics())) {
			LOG.debug("Rejected category -- violates reference constraint: %s",
					generatedCategory);
			return false;
		}

		return isSlashNormalForm(syntax) && isValidSyntaxVariables(syntax);
	}

	/**
	 * @param ois
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	private void readObject(ObjectInputStream ois)
			throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
		this.executor = new TinyExecutorService(
				numThreads == null ? Runtime.getRuntime().availableProcessors()
						: numThreads,
				new LoggingThreadFactory(threadName),
				ITinyExecutor.DEFAULT_MONITOR_SLEEP);
	}

	public static class Creator
			implements IResourceObjectCreator<SplittingGenlex> {

		private final String type;

		public Creator() {
			this("genlex.split");
		}

		public Creator(String type) {
			this.type = type;
		}

		@SuppressWarnings("unchecked")
		@Override
		public SplittingGenlex create(Parameters params,
				IResourceRepository repo) {

			final List<IBinaryReversibleParseRule<LogicalExpression>> binaryRules = new LinkedList<>();
			final List<IUnaryReversibleParseRule<LogicalExpression>> unaryRules = new LinkedList<>();
			for (final String ruleId : params.getSplit("rules")) {
				final Object rule = repo.get(ruleId);
				if (rule instanceof BinaryRuleSet) {
					for (final IBinaryParseRule<LogicalExpression> singleRule : (BinaryRuleSet<LogicalExpression>) rule) {
						addRule(singleRule, binaryRules, unaryRules);
					}
				} else if (rule instanceof UnaryRuleSet) {
					for (final IUnaryParseRule<LogicalExpression> singleRule : (UnaryRuleSet<LogicalExpression>) rule) {
						addRule(singleRule, binaryRules, unaryRules);
					}
				} else {
					addRule(rule, binaryRules, unaryRules);
				}

			}

			final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> logger = params
					.contains("logger") ? repo.get(params.get("logger")) : null;

			final Predicate<Category<LogicalExpression>> heuristicFilter;
			if (params.contains("entryFilter")) {
				heuristicFilter = repo.get(params.get("entryFilter"));
			} else {
				heuristicFilter = (Serializable & Predicate<Category<LogicalExpression>>) c -> true;
			}

			return new SplittingGenlex(params.getAsInteger("beam"), binaryRules,
					Syntax.read(params.get("sentenceSyntax")),
					repo.get(params.get("filter")),
					repo.get(params.get("genlex")),
					params.get("origin", "recsplit"),
					repo.get(params.get("parser")), unaryRules,
					params.getAsInteger("tokenLimit", 1),
					params.contains("maxMarking")
							? params.getAsInteger("maxMarking") : null,
					logger, params.getAsBoolean("skipReachable", false),
					params.getAsBoolean("hardSuperTagConstraint", false),
					params.contains("splitArgThreshold")
							? params.getAsInteger("splitArgThreshold") : null,
					params.getAsBoolean("conservative", true),
					params.contains("threads") ? params.getAsInteger("threads")
							: null,
					params.get("threadName", "GENSPLIT"), heuristicFilter,
					params.getAsBoolean("splitLexemes", false),
					params.getAsBoolean("bankspan", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, SplittingGenlex.class)
					.addParam("splitLexemes", Boolean.class,
							"Learn new lexemes during splitting (default: false)")
					.addParam("bankspan", Boolean.class,
							"Use spans from CCGBank parse to constrain splitting (defualt: false)")
					.setDescription(
							"Recursive splitting GENLEX with bottom-up-bottom beam search")
					.addParam("conservative", Boolean.class,
							"Be conservative and prefer not to generate any entries if getting multiple splitting entries (default: true)")
					.addParam("entryFilter", Predicate.class,
							"Hueristic filter to ignore generated entries (default: none)")
					.addParam("splitArgThreshold", Integer.class,
							"Maximum number of arguments allowed for a category generated from splitting (default: no limit)")
					.addParam("hardSuperTagConstraint", Boolean.class,
							"Require that single-token entries generated from splitting have a syntactic category that is covered by the super tagger (default: false)")
					.addParam("beam", Integer.class, "Beam size")
					.addParam("sentenceSyntax", Syntax.class,
							"Syntactic type of a complete sentence")
					.addParam("filter", IJointInferenceFilterFactory.class,
							"Supervised inference filter")
					.addParam("origin", String.class,
							"Origin of lexical entries generated via splitting (default: recsplit)")
					.addParam("parser", GraphAmrParser.class,
							"AMR inference procedure")
					.addParam("tokenLimit", Integer.class,
							"The maxmimum number of tokens in a lexical entry generated via splitting (default: 1)")
					.addParam("threads", Integer.class,
							"The number of threads to use (default: number of cores)")
					.addParam("threadName", String.class,
							"Working thread name prefix (default: GENSPLIT)")
					.addParam("maxMarking", Integer.class,
							"If parsing with marked cells, the max marking value (e.g., the maximum number of marked lexical entries) (default: null)")
					.addParam("genlex", ILexiconGenerator.class,
							"The GENLEX procedure to initialize the bottom-up portion of the search procedure")
					.addParam("skipReachable", Boolean.class,
							"Skip lexical generation for sentences reachable under the current grammar. Skipping GENLEX trades in exploring the grammar space for learning speed. (default: false)")
					.build();
		}

		@SuppressWarnings("unchecked")
		protected void addRule(Object rule,
				List<IBinaryReversibleParseRule<LogicalExpression>> binaryRules,
				List<IUnaryReversibleParseRule<LogicalExpression>> unaryRules) {
			if (rule instanceof IBinaryReversibleParseRule) {
				binaryRules.add(
						(IBinaryReversibleParseRule<LogicalExpression>) rule);
			} else if (rule instanceof IUnaryReversibleParseRule) {
				unaryRules.add(
						(IUnaryReversibleParseRule<LogicalExpression>) rule);
			} else {
				throw new IllegalArgumentException(
						"Invalid rule class: " + rule);
			}
		}

	}

	/**
	 * A literal with the predicate ref:<id,e> may only appear as part of a
	 * conjunction with a the unary typing literal or as the complete logical
	 * form. This means that references should never be introduced by a
	 * relation, but always from an explicit reference or from an entity.
	 *
	 * @author Yoav Artzi
	 */
	public static class TestReferenceConstraint
			implements ILogicalExpressionVisitor {

		private boolean isValid = true;

		public static boolean of(LogicalExpression exp) {
			if (exp == null) {
				return true;
			}

			if (AMRServices.isRefLiteral(exp)) {
				return true;
			}

			final TestReferenceConstraint visitor = new TestReferenceConstraint();
			visitor.visit(exp);
			return visitor.isValid;
		}

		@Override
		public void visit(Lambda lambda) {
			lambda.getBody().accept(this);
		}

		@Override
		public void visit(Literal literal) {
			final int len = literal.numArgs();
			if (LogicLanguageServices.getConjunctionPredicate()
					.equals(literal.getPredicate())) {
				// Case conjunction.

				// Search for the unary typing literal.
				boolean typingLiteralFound = false;
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					typingLiteralFound = arg instanceof Literal
							&& ((Literal) arg).numArgs() == 1
							&& ((Literal) arg)
									.getPredicate() instanceof LogicalConstant
							&& ((Literal) arg).getArg(0) instanceof Variable;
					if (typingLiteralFound) {
						break;
					}
				}

				// If no unary typing literal found, verify that no argument is
				// a binary literal with a reference literal as its second
				// argument.
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					// Flag to indicate the current argument is a valid relation
					// to a reference.
					final boolean relationToRef = arg instanceof Literal
							&& ((Literal) arg).numArgs() == 2
							&& ((Literal) arg).getArg(0) instanceof Variable
							&& ((Literal) arg)
									.getPredicate() instanceof LogicalConstant
							&& AMRServices
									.isRefLiteral(((Literal) arg).getArg(1));
					// If we have a valid relation to reference, but the typing
					// literal is not present in this conjunction, the
					// constraint doesn't hold.
					if (relationToRef && !typingLiteralFound) {
						isValid = false;
						return;
					}
					// Only visit if it's not a valid relation to reference.
					// This way, if we encounter any reference otherwise, we
					// consider the constraint to be violated and return false.
					if (!relationToRef) {
						arg.accept(this);
						if (!isValid) {
							return;
						}
					}
				}
			} else {
				// Not a conjunction, visit the sub-expressions.
				literal.getPredicate().accept(this);
				if (!isValid) {
					return;
				}
				for (int i = 0; i < len; ++i) {
					literal.getArg(i).accept(this);
					if (!isValid) {
						return;
					}
				}
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			// If we encounter a reference at this point, the constraint is
			// invalid. See the handling of in visit(Literal) for details.
			isValid = !AMRServices.isRefPredicate(logicalConstant);
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do.
		}

	}

	private static class CellWrapper {
		private final Cell<LogicalExpression>					cell;

		/**
		 * The steps are stored separately from the cell to avoid adding them
		 * directly to the cell. Adding them to the cell would create an illegal
		 * state, since the viterbi score might change but is not propagated up.
		 * These steps don't influence the score. Non-lexical steps are only
		 * added after the span of this cell finished processing.
		 */
		private final Set<IWeightedCKYStep<LogicalExpression>>	steps				= new HashSet<>();

		private double											viterbiInsideScore	= -Double.MAX_VALUE;

		/**
		 * The score of a cell in the queues approximates the outside score of
		 * this cell, as much as possible. The score is a sum of the cell's
		 * viterbi score, and the viterbi "outside" score. The "outside" score
		 * is computed by summing the score of the parse step that this cell was
		 * generated for, the viterbi score of the sibling cell (the other cell
		 * participating in the step) and the viterbi "outside" score of parent
		 * (root in the step). See
		 * {@link #computeOutsideScore(AbstractCKYStep, Cell, CellWrapper)} .
		 */
		private double											viterbiOutsideScore;

		public CellWrapper(Cell<LogicalExpression> cell,
				IWeightedCKYStep<LogicalExpression> step,
				Cell<LogicalExpression> siblingCell,
				CellWrapper parentWrapper) {
			// See the comment on addCell().
			assert cell != null && cell.getSteps().size() == 1;
			this.viterbiOutsideScore = computeOutsideScore(step, siblingCell,
					parentWrapper);
			this.cell = cell;
			this.viterbiInsideScore = cell.getViterbiScore();
			this.steps.addAll(cell.getSteps());
		}

		private static double computeOutsideScore(
				IWeightedCKYStep<LogicalExpression> step,
				Cell<LogicalExpression> siblingCell,
				CellWrapper parentWrapper) {
			return (step == null ? 0.0 : step.getStepScore())
					+ (siblingCell == null ? 0.0
							: siblingCell.getViterbiScore())
					+ (parentWrapper == null ? 0.0
							: parentWrapper.viterbiOutsideScore);
		}

		public void addCell(Cell<LogicalExpression> newCell,
				IWeightedCKYStep<LogicalExpression> step,
				Cell<LogicalExpression> siblingCell,
				CellWrapper parentWrapper) {
			// All cells are generated with a single lexical step. This
			// assertion verifies this. The step of the new cell is mostly
			// likely identical to the one in the original cell, but this is not
			// guaranteed. For example, when it was created by a different
			// lexeme-template pair than the original. Therefore, we add it to
			// the set of steps.
			assert newCell != null && cell.equals(newCell)
					&& newCell.getSteps().size() == 1;
			steps.addAll(newCell.getSteps());
			// Update the scores.
			viterbiInsideScore = Math.max(viterbiInsideScore,
					cell.getViterbiScore());
			viterbiOutsideScore = Math.max(viterbiOutsideScore,
					computeOutsideScore(step, siblingCell, parentWrapper));
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final CellWrapper other = (CellWrapper) obj;
			if (cell == null) {
				if (other.cell != null) {
					return false;
				}
			} else if (!cell.equals(other.cell)) {
				return false;
			}
			return true;
		}

		public Set<IWeightedCKYStep<LogicalExpression>> getSteps() {
			return Collections.unmodifiableSet(steps);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (cell == null ? 0 : cell.hashCode());
			return result;
		}

		public double score() {
			return viterbiOutsideScore + viterbiInsideScore;
		}

		@Override
		public String toString() {
			return String.format("[score=%f, steps=%d, hash=%d] %s <= %s",
					score(), steps.size(), cell.hashCode(), cell.getCategory(),
					steps.stream().map(s -> s.toString(true, false))
							.collect(Collectors.joining(", ")));
		}

	}

	private static class GeneratedSplit {
		private final Cell<LogicalExpression>				newCell;
		private final Cell<LogicalExpression>				rootCellWithStep;
		private final CellWrapper							rootCellWrapper;
		private final Cell<LogicalExpression>				siblingCell;
		private final IWeightedCKYStep<LogicalExpression>	step;

		public GeneratedSplit(Cell<LogicalExpression> newCell,
				IWeightedCKYStep<LogicalExpression> step,
				CellWrapper rootCellWrapper,
				Cell<LogicalExpression> siblingCell,
				Cell<LogicalExpression> rootCellWithStep) {
			assert newCell != null;
			assert step != null;
			assert rootCellWrapper != null;
			assert siblingCell != null;
			this.rootCellWithStep = rootCellWithStep;
			this.newCell = newCell;
			this.step = step;
			this.rootCellWrapper = rootCellWrapper;
			this.siblingCell = siblingCell;
		}
	}

	private class Listener {
		private int																cellsQueued		= 0;
		private final Chart<LogicalExpression>									chart;
		private final LabeledAmrSentence										dataItem;
		private final IJointDataItemModel<LogicalExpression, LogicalExpression>	dataItemModel;
		private final Predicate<Lexeme>											lexemeFilter;
		private final SpanLock													lock;
		private int																newCellsCreated	= 0;
		private final OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][]	queues;
		private final Map<Span, Set<Split>>										spans;

		public Listener(Chart<LogicalExpression> chart,
				IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
				SpanLock lock,
				OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][] queues,
				Map<Span, Set<Split>> spans, LabeledAmrSentence dataItem,
				Predicate<Lexeme> lexemeFilter) {
			this.chart = chart;
			this.dataItemModel = dataItemModel;
			this.lock = lock;
			this.queues = queues;
			this.spans = spans;
			this.dataItem = dataItem;
			this.lexemeFilter = lexemeFilter;
		}

		public void jobComplete(SplitJob job) {
			final Span completedLeftSpan;
			final Span completedRightSpan;
			synchronized (spans) {
				// Update stats.
				newCellsCreated += job.newCellsCreated;
				cellsQueued += job.cellsQueued;

				// First, remove the span from the spans map. Its corresponding
				// set is already empty. It might have already been removed by
				// an different split of this span.
				final Set<Split> spanSet = spans.remove(job.split.span);
				assert spanSet == null || spanSet.isEmpty();

				// Remove the completed split from split sets. It should appear
				// in two sets, so need to remove it from both.

				// Left span.
				final Span leftSpan = job.split.getLeft();
				final Set<Split> leftSet = spans.get(leftSpan);
				if (leftSet != null && leftSet.remove(job.split)
						&& leftSet.isEmpty()) {
					completedLeftSpan = leftSpan;
				} else {
					completedLeftSpan = null;
				}

				// Right span.
				final Span rightSpan = job.split.getRight();
				final Set<Split> rightSet = spans.get(rightSpan);
				if (rightSet != null && rightSet.remove(job.split)
						&& rightSet.isEmpty()) {
					completedRightSpan = rightSpan;
				} else {
					completedRightSpan = null;
				}
			}

			boolean removeLeft = false;
			if (completedLeftSpan != null) {
				LOG.debug("Span complete: %s", completedLeftSpan);
				if (completedLeftSpan.length() == 0) {
					removeLeft = true;
				} else {
					LOG.debug("Queuing split jobs for %s", completedLeftSpan);
					queueSpanJobs(completedLeftSpan);
				}
			}

			boolean removeRight = false;
			if (completedRightSpan != null) {
				LOG.debug("Span complete: %s", completedRightSpan);
				if (completedRightSpan.length() == 0) {
					removeRight = true;
				} else {
					LOG.debug("Queuing split jobs for %s", completedRightSpan);
					queueSpanJobs(completedRightSpan);
				}
			}

			synchronized (spans) {
				if (removeLeft) {
					final Set<Split> removedSet = spans
							.remove(completedLeftSpan);
					assert removedSet.isEmpty();
				}

				if (removeRight) {
					final Set<Split> removedSet = spans
							.remove(completedRightSpan);
					assert removedSet.isEmpty();
				}

				// If the spans map is empty, we are done.
				if (spans.isEmpty()) {
					LOG.debug("All spans complete -- notifying parser");
					spans.notifyAll();
				}
			}

		}

		private void queueSpanJobs(Span span) {
			final int start = span.getStart();
			final int end = span.getEnd();
			for (int split = start; split < end; ++split) {
				executor.execute(new SplitJob(chart, dataItemModel, this, lock,
						queues, new Split(start, end, split), dataItem,
						lexemeFilter));
			}
		}
	}

	private static class Split {
		private final Span	span;
		private final int	split;

		public Split(int start, int end, int split) {
			assert end > start;
			this.span = new Span(start, end);
			this.split = split;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final Split other = (Split) obj;
			if (!span.equals(other.span)) {
				return false;
			}
			if (split != other.split) {
				return false;
			}
			return true;
		}

		public Span getLeft() {
			return new Span(span.getStart(), split);
		}

		public Span getRight() {
			return new Span(split + 1, span.getEnd());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (span == null ? 0 : span.hashCode());
			result = prime * result + split;
			return result;
		}

		@Override
		public String toString() {
			return String.format("(%d-%d), (%d-%d)", span.getStart(), split,
					split + 1, span.getEnd());
		}

	}

	private class SplitJob extends LoggingRunnable {
		private int																cellsQueued		= 0;
		private final Chart<LogicalExpression>									chart;
		private final LabeledAmrSentence										dataItem;
		private final IJointDataItemModel<LogicalExpression, LogicalExpression>	dataItemModel;
		private final Predicate<Lexeme>											lexemeFilter;
		private final Listener													listener;
		private final SpanLock													lock;
		private int																newCellsCreated	= 0;
		private final OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][]	queues;
		private final Split														split;

		public SplitJob(Chart<LogicalExpression> chart,
				IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
				SplittingGenlex.Listener listener, SpanLock lock,
				OrderInvariantDirectAccessBoundedQueue<CellWrapper>[][] queues,
				Split split, LabeledAmrSentence dataItem,
				Predicate<Lexeme> lexemeFilter) {
			this.chart = chart;
			this.dataItemModel = dataItemModel;
			this.listener = listener;
			this.lock = lock;
			this.queues = queues;
			this.split = split;
			this.dataItem = dataItem;
			this.lexemeFilter = lexemeFilter;
		}

		@Override
		public void loggedRun() {
			LOG.debug("Split: (%d,%d)-(%d,%d)", split.span.getStart(),
					split.split, split.split + 1, split.span.getEnd());
			LOG.debug("Queue size=%d, queue has %sthreashold",
					queues[split.span.getStart()][split.span.getEnd()].size(),
					queues[split.span.getStart()][split.span.getEnd()]
							.hasThreshold() ? "" : "no ");

			// Span info for rules.
			final SentenceSpan span = new SentenceSpan(split.span.getStart(),
					split.span.getEnd(), chart.getTokens().size());
			final Span leftSpan = new Span(split.span.getStart(), split.split);
			final Span rightSpan = new Span(split.split + 1,
					split.span.getEnd());

			// Process the cells to create all root categories. Ignore cells
			// that are not in the chart and apply all unary rules. We don't
			// need to lock the root span, since it was already processed.
			final Set<Triplet<CellWrapper, UnaryRuleName, Category<LogicalExpression>>> rootTriplets = new HashSet<>();
			for (final CellWrapper rootWrapper : queues[split.span
					.getStart()][split.span.getEnd()]) {
				final Cell<LogicalExpression> rootCell = rootWrapper.cell;
				LOG.debug("Pre-processing root cell:  %s [queue score=%f]",
						rootCell, rootWrapper.score());
				final Category<LogicalExpression> rootCategory = rootCell
						.getCategory();
				// Create a list of potential root categories and the unary
				// rule that leads to them. Add a pair with null unary rule
				// for the case where no unary is applied.
				rootTriplets.add(Triplet.of(rootWrapper, null, rootCategory));
				for (final IUnaryReversibleParseRule<LogicalExpression> unaryRule : unaryRules) {
					for (final Category<LogicalExpression> input : unaryRule
							.reverseApply(rootCategory, span)) {
						LOG.debug("Pre-processing with unary: %s -> %s",
								unaryRule, input);
						rootTriplets.add(Triplet.of(rootWrapper,
								unaryRule.getName(), input));
					}
				}
			}

			// Generate right cells, given left cell and root.
			LOG.debug("Generating right cells");
			final List<GeneratedSplit> rightGeneratedSplits = new LinkedList<>();
			for (final Triplet<CellWrapper, UnaryRuleName, Category<LogicalExpression>> rootTriplet : rootTriplets) {
				final CellWrapper rootWrapper = rootTriplet.first();
				final UnaryRuleName rootUnaryRule = rootTriplet.second();
				final Category<LogicalExpression> rootCategory = rootTriplet
						.third();
				LOG.debug("Root category: [%s] %s", rootUnaryRule,
						rootCategory);

				for (final Cell<LogicalExpression> leftCell : chart
						.getSpanIterable(split.span.getStart(), split.split)) {
					LOG.debug("Left cell: %s", leftCell);

					if (ignore(leftCell)) {
						LOG.debug("Cell ignored");
						continue;
					}

					// If the cells are marked, make sure this combination a
					// left cell and a root is possible. Meaning, the root's
					// marking is >= the left cell marking. The marking must be
					// monotonously increasing.
					if (rootWrapper.cell instanceof MarkedCell
							&& leftCell instanceof MarkedCell
							&& ((MarkedCell<LogicalExpression>) rootWrapper.cell)
									.getNumMarkedLexicalEntries() < ((MarkedCell<LogicalExpression>) leftCell)
											.getNumMarkedLexicalEntries()) {
						LOG.debug(
								"Skipping left cell, marking is inconsistent: rootMarking=%d, leftCellMarking=%d",
								((MarkedCell<LogicalExpression>) rootWrapper.cell)
										.getNumMarkedLexicalEntries(),
								((MarkedCell<LogicalExpression>) leftCell)
										.getNumMarkedLexicalEntries());
						continue;
					}

					for (final IBinaryReversibleParseRule<LogicalExpression> rule : binaryRules) {
						LOG.debug("Reverse applying (left=%d, root=%d): %s",
								leftCell.hashCode(),
								rootWrapper.cell.hashCode(), rule);
						for (final Category<LogicalExpression> rightInput : rule
								.reverseApplyLeft(
										StripOverload
												.of(leftCell.getCategory()),
										rootCategory, span)) {

							if (!isValidGeneratedCategory(rightInput, dataItem,
									rightSpan)) {
								LOG.debug("Split rejected: (%d,%d)-(%d,%d)",
										split.span.getStart(), split.split,
										split.split + 1, split.span.getEnd());
								LOG.debug(
										"Rejected category split: %s -> %s , %s",
										rootCategory, leftCell.getCategory(),
										rightInput);
								continue;
							}

							LOG.debug(
									"Generated right category (left=%d, root=%d): %s",
									leftCell.hashCode(),
									rootWrapper.cell.hashCode(), rightInput);
							LOG.debug("Generated from left: %s",
									leftCell.toString(false, null, true,
											dataItemModel.getTheta()));
							LOG.debug("Generated from root: %s",
									rootWrapper.cell.toString(false, null, true,
											dataItemModel.getTheta()));

							// Create the new cell and the root with the new
							// step.
							final GeneratedSplit generatedPair = createCellAndParent(
									rightInput, dataItemModel, chart, leftCell,
									false, split, rootWrapper, rootUnaryRule,
									rule.getName(), dataItem, lexemeFilter);
							if (generatedPair != null) {
								rightGeneratedSplits.add(generatedPair);
							}
						}
					}
				}
			}

			// Add the right generated cells to the queues and chart. Take the
			// relevant span lock.
			if (!rightGeneratedSplits.isEmpty()) {
				lock.lock(split.span.getStart(), split.span.getEnd());
				lock.lock(split.split + 1, split.span.getEnd());
				LOG.debug("Updating %d into (%d,%d) queue",
						rightGeneratedSplits.size(), split.split + 1,
						split.span.getEnd());
				for (final GeneratedSplit generatedSplit : rightGeneratedSplits) {
					newCellsCreated++;
					if (updateQueues(generatedSplit, queues)) {
						cellsQueued++;
					}
				}
				lock.unlock(split.split + 1, split.span.getEnd());
				lock.unlock(split.span.getStart(), split.span.getEnd());
			}

			// Generate left cells, given a right cell and root.
			LOG.debug("Generating left cells");
			final List<GeneratedSplit> leftGeneratedSplits = new LinkedList<>();
			for (final Triplet<CellWrapper, UnaryRuleName, Category<LogicalExpression>> rootTriplet : rootTriplets) {
				final CellWrapper rootWrapper = rootTriplet.first();
				final UnaryRuleName rootUnaryRule = rootTriplet.second();
				final Category<LogicalExpression> rootCategory = rootTriplet
						.third();
				LOG.debug("Root category: [%s] %s", rootUnaryRule,
						rootCategory);

				for (final Cell<LogicalExpression> rightCell : chart
						.getSpanIterable(split.split + 1,
								split.span.getEnd())) {
					LOG.debug("Right cell: %s", rightCell);

					if (ignore(rightCell)) {
						LOG.debug("Cell ignored");
						continue;
					}

					// If the cells are marked, make sure this combination a
					// left cell and a root is possible. Meaning, the root's
					// marking is >= the left cell marking. The marking must be
					// monotonously increasing.
					if (rootWrapper.cell instanceof MarkedCell
							&& rightCell instanceof MarkedCell
							&& ((MarkedCell<LogicalExpression>) rootWrapper.cell)
									.getNumMarkedLexicalEntries() < ((MarkedCell<LogicalExpression>) rightCell)
											.getNumMarkedLexicalEntries()) {
						LOG.debug(
								"Skipping right cell, marking is inconsistent: rootMarking=%d, leftCellMarking=%d",
								((MarkedCell<LogicalExpression>) rootWrapper.cell)
										.getNumMarkedLexicalEntries(),
								((MarkedCell<LogicalExpression>) rightCell)
										.getNumMarkedLexicalEntries());
						continue;
					}

					for (final IBinaryReversibleParseRule<LogicalExpression> rule : binaryRules) {
						LOG.debug("Reverse applying (right=%d, root=%d): %s",
								rightCell.hashCode(),
								rootWrapper.cell.hashCode(), rule);
						for (final Category<LogicalExpression> leftInput : rule
								.reverseApplyRight(
										StripOverload
												.of(rightCell.getCategory()),
										rootCategory, span)) {

							if (!isValidGeneratedCategory(leftInput, dataItem,
									leftSpan)) {
								LOG.debug("Split rejected: (%d,%d)-(%d,%d)",
										split.span.getStart(), split.split,
										split.split + 1, split.span.getEnd());
								LOG.debug(
										"Rejected category split: %s -> %s , %s",
										rootCategory, leftInput,
										rightCell.getCategory());
								continue;
							}

							LOG.debug(
									"Generated left category (right=%d, root=%d): %s",
									rightCell.hashCode(),
									rootWrapper.cell.hashCode(), leftInput);
							LOG.debug("Generated from right: %s",
									rightCell.toString(false, null, true,
											dataItemModel.getTheta()));
							LOG.debug("Generated from root: %s",
									rootWrapper.cell.toString(false, null, true,
											dataItemModel.getTheta()));

							// Create the new cell and the root with the new
							// step.
							final GeneratedSplit generatedPair = createCellAndParent(
									leftInput, dataItemModel, chart, rightCell,
									true, split, rootWrapper, rootUnaryRule,
									rule.getName(), dataItem, lexemeFilter);
							if (generatedPair != null) {
								leftGeneratedSplits.add(generatedPair);
							}
						}
					}
				}
			}

			// Add the left generated cells to the queues and chart. Take the
			// relevant span lock.
			if (!leftGeneratedSplits.isEmpty()) {
				lock.lock(split.span.getStart(), split.span.getEnd());
				lock.lock(split.span.getStart(), split.split);
				LOG.debug("Updating %d into (%d,%d) queue",
						leftGeneratedSplits.size(), split.span.getStart(),
						split.split);
				for (final GeneratedSplit generatedSplit : leftGeneratedSplits) {
					newCellsCreated++;
					if (updateQueues(generatedSplit, queues)) {
						cellsQueued++;
					}
				}
				lock.unlock(split.span.getStart(), split.split);
				lock.unlock(split.span.getStart(), split.span.getEnd());
			}

			// Notify listener.
			listener.jobComplete(this);
		}

	}
}
