package edu.uw.cs.lil.amr.learn.genlex;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.data.ILabeledDataItem;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGeneratorPrecise;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IDataItemModel;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.BinaryRuleSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryReversibleParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IUnaryReversibleParseRule;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleSet;
import edu.cornell.cs.nlp.spf.parser.joint.IJointDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.collections.iterators.FilteredIterator;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.parser.AbstractAmrParser;

/**
 * A wrapper for {@link ILexiconGenerator} that uses a parser to do the pruning.
 *
 * @author Yoav Artzi
 * @param <SAMPLE>
 *            Type of inference sample.
 * @param <DI>
 *            Type of learning data item.
 */
public class PreciseGenlexWrapper<SAMPLE extends SituatedSentence<AMRMeta>, DI extends ILabeledDataItem<SAMPLE, LogicalExpression>>
		implements
		ILexiconGeneratorPrecise<DI, LogicalExpression, IJointModelImmutable<SAMPLE, LogicalExpression, LogicalExpression>> {

	public static final ILogger																									LOG					= LoggerFactory
			.create(PreciseGenlexWrapper.class);

	private static final long																									serialVersionUID	= 4421688763317719910L;

	private final int																											beam;

	private final IJointInferenceFilterFactory<DI, LogicalExpression, LogicalExpression, LogicalExpression>						filterFactory;
	private final ILexiconGenerator<DI, LogicalExpression, IJointModelImmutable<SAMPLE, LogicalExpression, LogicalExpression>>	genlex;
	private final Predicate<Category<LogicalExpression>>																		heuristicFilter;

	private final AbstractAmrParser<?>																							parser;

	/**
	 * Parser output logger.
	 */
	private final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>									parserOutputLogger;

	/**
	 * Skip lexical generation for sentences reachable under the current
	 * grammar. Skipping GENLEX trades in exploring the grammar space for
	 * learning speed.
	 */
	private final boolean																										skipReachable;

	public PreciseGenlexWrapper(int beam,
			IJointInferenceFilterFactory<DI, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			ILexiconGenerator<DI, LogicalExpression, IJointModelImmutable<SAMPLE, LogicalExpression, LogicalExpression>> genlex,
			Predicate<Category<LogicalExpression>> heuristicFilter,
			AbstractAmrParser<?> parser,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger,
			boolean skipReachable) {
		this.beam = beam;
		this.filterFactory = filterFactory;
		this.genlex = genlex;
		this.heuristicFilter = heuristicFilter;
		this.parser = parser;
		this.parserOutputLogger = parserOutputLogger;
		this.skipReachable = skipReachable;
	}

	public static void logParse(
			IJointDerivation<LogicalExpression, LogicalExpression> derivation,
			IDataItemModel<LogicalExpression> dataItemModel) {
		for (final IWeightedParseStep<LogicalExpression> step : derivation
				.getMaxSteps()) {
			LOG.info("\t%s",
					step.toString(false, false, dataItemModel.getTheta()));
		}

	}

	@Override
	public ILexiconImmutable<LogicalExpression> generate(DI dataItem,
			IJointModelImmutable<SAMPLE, LogicalExpression, LogicalExpression> model,
			ICategoryServices<LogicalExpression> categoryServices) {
		final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
				.createJointDataItemModel(dataItem.getSample());
		final IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter = filterFactory
				.createJointFilter(dataItem);

		// Skip if can parse this sentence.
		final List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> currentMaxDerivations = parser
				.parse(dataItem.getSample(), dataItemModel, filter)
				.getMaxDerivations(dataItem.getLabel());
		if (skipReachable && !currentMaxDerivations.isEmpty()) {
			LOG.info(
					"Skipping GENLEX -- sentence is parseable under the current grammar -- returning viterbi entries");
			final Set<LexicalEntry<LogicalExpression>> viterbiEntries = new HashSet<>();
			for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : currentMaxDerivations) {
				viterbiEntries.addAll(derivation.getMaxLexicalEntries());
			}
			return new Lexicon<>(viterbiEntries);
		}

		// Use the base GENLEX procedure and parsing to generate lexical
		// entries using bottom-up beam search.
		final long genlexStart = System.currentTimeMillis();
		final ILexiconImmutable<LogicalExpression> generatedLexicon = genlex
				.generate(dataItem, model, categoryServices);

		final int generatedLexiconSize = generatedLexicon.size();
		LOG.info("Base GENLEX generated %d entries (%.2fsec)",
				generatedLexiconSize,
				(System.currentTimeMillis() - genlexStart) / 1000.0);

		if (generatedLexiconSize == 0) {
			LOG.debug("No base GENLEX entries, skipping parsing");
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
		final IJointOutput<LogicalExpression, LogicalExpression> parserOutput = parser
				.parse(dataItem.getSample(), dataItemModel, filter, false,
						heuristicallyFiltered, beam);
		LOG.info("GENLEX parsing time: %.4fsec",
				parserOutput.getInferenceTime() / 1000.0);
		LOG.info("Output is %s",
				parserOutput.isExact() ? "exact" : "approximate");
		if (parserOutputLogger != null) {
			parserOutputLogger.log(parserOutput, dataItemModel,
					"genlex-" + System.currentTimeMillis());
		}

		final List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> correctDerivations = parserOutput
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
			for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : correctDerivations) {
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
			LOG.info("Created no GENLEX parses, returning an empty lexicon");
			return new Lexicon<>();
		}
	}

	@Override
	public void init(
			IJointModelImmutable<SAMPLE, LogicalExpression, LogicalExpression> model) {
		genlex.init(model);
	}

	@Override
	public boolean isGenerated(LexicalEntry<LogicalExpression> entry) {
		return genlex.isGenerated(entry);
	}

	public static class Creator<SAMPLE extends SituatedSentence<AMRMeta>, DI extends ILabeledDataItem<SAMPLE, LogicalExpression>>
			implements
			IResourceObjectCreator<PreciseGenlexWrapper<SAMPLE, DI>> {

		private String type;

		public Creator() {
			this("genlex.precise");
		}

		public Creator(String type) {
			this.type = type;
		}

		@SuppressWarnings("unchecked")
		@Override
		public PreciseGenlexWrapper<SAMPLE, DI> create(Parameters params,
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

			return new PreciseGenlexWrapper<>(params.getAsInteger("beam"),
					repo.get(params.get("filter")),
					repo.get(params.get("genlex")), heuristicFilter,
					repo.get(params.get("parser")), logger,
					params.getAsBoolean("skipReachable", false));

		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, PreciseGenlexWrapper.class)
					.setDescription(
							"A wrapper for ILexiconGenerator that uses a parser to do the pruning")
					.addParam("entryFilter", Predicate.class,
							"Hueristic filter to ignore generated entries (default: none)")
					.addParam("beam", Integer.class, "Beam size")
					.addParam("filter", IJointInferenceFilterFactory.class,
							"Supervised inference filter")
					.addParam("parser", AbstractAmrParser.class,
							"AMR inference procedure")
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

}
