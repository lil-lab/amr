package edu.uw.cs.lil.amr.learn.postprocessing;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelProcessor;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.learn.genlex.AlignmentGenlex;

/**
 * Generate {@link LexicalEntry}s from all alignments in the data and add them
 * to the model's lexicon.
 *
 * @author Yoav Artzi
 */
public class AddAllAlignments implements
		IJointModelProcessor<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> {

	public static final ILogger																								LOG	= LoggerFactory
			.create(AddAllAlignments.class);
	private final ICategoryServices<LogicalExpression>																		categoryServices;
	private final IDataCollection<LabeledAmrSentence>																		data;
	private final String																									entryOrigin;
	private final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>	filterFactory;

	public AddAllAlignments(String entryOrigin,
			IDataCollection<LabeledAmrSentence> data,
			ICategoryServices<LogicalExpression> categoryServices,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory) {
		this.entryOrigin = entryOrigin;
		this.data = data;
		this.categoryServices = categoryServices;
		this.filterFactory = filterFactory;
	}

	@Override
	public void process(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {

		LOG.info("Adding entries from all alignments");

		// Create alignment GENLEX.
		final AlignmentGenlex genlex = new AlignmentGenlex(entryOrigin, false);
		genlex.init(model);

		// Add all alignment to the model. This process uses the most recent set
		// of templates (assuming the GENLEX procedure registered on the model).
		final int originalSize = model.getLexicon().size();
		final Set<LexicalEntry<LogicalExpression>> entriesToAdd = new HashSet<>();
		final Set<LexicalEntry<LogicalExpression>> badEntries = new HashSet<>();
		int itemCounter = 0;
		for (final LabeledAmrSentence dataItem : data) {
			LOG.info("Processing %d / %d...", itemCounter++, data.size());
			final ILexiconImmutable<LogicalExpression> generated = genlex
					.generate(dataItem, model, categoryServices);

			// Create lexical parse operations for all spans to filter.
			final int numTokens = dataItem.getSample().getTokens().size();
			final Predicate<ParsingOp<LogicalExpression>> filter = filterFactory
					.create(dataItem);
			for (int start = 0; start < numTokens; start++) {
				for (int end = start; end < numTokens; end++) {
					final SentenceSpan span = new SentenceSpan(start, end,
							numTokens);
					final TokenSeq tokens = dataItem.getSample().getTokens()
							.sub(start, end + 1);

					// Filter the entries.
					entriesToAdd.addAll(StreamSupport
							.stream(Spliterators.spliteratorUnknownSize(
									generated.get(tokens),
									Spliterator.IMMUTABLE), true)
							.filter(entry -> {
								// Filter entries that are in the model.
								if (model.getLexicon().contains(entry)) {
									LOG.info("Skipping (exists): %s", entry);
									return false;
								}

								// Filter entries that don't pass the
								// filter.
								final ParsingOp<LogicalExpression> op = new ParsingOp<LogicalExpression>(
										entry.getCategory(), span,
										ILexicalParseStep.LEXICAL_DERIVATION_STEP_RULENAME);
								if (!filter.test(op)) {
									LOG.info("Skipping (filtered): %s", entry);
									return false;
								}

								// Filter entries where there is an
								// entry in the
								// model with the same tokens and
								// semantics (but
								// potentially different syntax).
								if (entry.getCategory()
										.getSemantics() != null) {
									final Iterator<? extends LexicalEntry<LogicalExpression>> otherIterator = model
											.getLexicon()
											.get(entry.getTokens());
									while (otherIterator.hasNext()) {
										final LexicalEntry<LogicalExpression> other = otherIterator
												.next();
										if (entry.getCategory().getSemantics()
												.equals(other.getCategory()
														.getSemantics())) {
											LOG.info(
													"Skipping (similar exists): %s",
													entry);
											return false;
										}
									}

								}
								LOG.info("Adding: %s", entry);
								return true;
							}).collect(Collectors.toSet()));

					// Collect the rest of the entries that are not in the
					// model's lexicon.
					badEntries.addAll(StreamSupport
							.stream(Spliterators.spliteratorUnknownSize(
									generated.get(tokens),
									Spliterator.IMMUTABLE), true)
							.filter(entry -> !model.getLexicon().contains(entry)
									&& !entriesToAdd.contains(entry))
							.collect(Collectors.toSet()));
				}
			}
		}

		LOG.info("Trying to add %d entries", entriesToAdd.size());
		model.addLexEntries(entriesToAdd);

		LOG.info("Added %d entries from alignment to the model",
				model.getLexicon().size() - originalSize);
	}

	public static class Creator
			implements IResourceObjectCreator<AddAllAlignments> {

		private final String type;

		public Creator() {
			this("processor.alignments");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AddAllAlignments create(Parameters params,
				IResourceRepository repo) {
			return new AddAllAlignments(params.get("origin", "all-align"),
					repo.get(params.get("data")),
					repo.get(
							ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE),
					repo.get(params.get("filterFactory")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, AddAllAlignments.class)
					.setDescription(
							"Generate {@link LexicalEntry}s from all alignments in the data and add them to the model's lexicon.")
					.addParam("data", IDataCollection.class,
							"Alignment source data")
					.addParam("origin", String.class,
							"Origin label for generated entries (default: all-align)")
					.addParam("filterFactory",
							IJointInferenceFilterFactory.class,
							"Inference filter factory to validate the generated entries")
					.build();
		}

	}

}
