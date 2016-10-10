package edu.uw.cs.lil.amr.learn.genlex;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Collections2;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.Lexeme;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.LexicalTemplate;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.AbstractLexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.ILexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.template.GenerationRepository;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsMultiSet;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsSet;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelListener;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * GENLEX procedure that uses the alignment information available in the meta
 * data of each {@link LabeledAmrSentence}.
 *
 * @author Yoav Artzi
 * @param <DI>
 *            Data item type.
 */
public class AlignmentGenlex extends
		AbstractLexiconGenerator<LabeledAmrSentence, LogicalExpression, IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>>
		implements IModelListener<LogicalExpression> {

	public static final ILogger			LOG					= LoggerFactory
			.create(AlignmentGenlex.class);

	private static final long			serialVersionUID	= 4143729306537776828L;

	private final GenerationRepository	repo				= new GenerationRepository();

	public AlignmentGenlex(String origin, boolean mark) {
		super(origin, mark);
		LOG.info("Init %s: #templates=%d", getClass().getSimpleName(),
				repo.getTemplates().size());
	}

	@Override
	public ILexiconImmutable<LogicalExpression> generate(
			LabeledAmrSentence dataItem,
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			ICategoryServices<LogicalExpression> categoryServices) {
		if (!dataItem.hasAlignments()) {
			LOG.debug(
					"Data item has no alignment information -- returning no entries");
			return new Lexicon<>();
		}

		// Get all factorable constants from the labeled logical form. We use
		// these to track which constants are not covered and issue a log
		// message appropriately.
		final Set<LogicalConstant> allConstants = GetConstantsSet
				.of(AMRServices.underspecifyAndStrip(dataItem.getLabel()))
				.stream().filter(FactoringServices::isFactorable)
				.collect(Collectors.toSet());

		final long startTime = System.currentTimeMillis();

		final TokenSeq tokens = dataItem.getSample().getTokens();
		final int numTokens = tokens.size();
		final Set<Lexeme> lexemes = new HashSet<>();
		for (int start = 0; start < numTokens; ++start) {
			for (int end = start + 1; end <= numTokens; ++end) {
				final TokenSeq seq = tokens.sub(start, end);
				final Set<LogicalExpression> aligned = dataItem
						.getAlignedExpressions(start, end);
				if (aligned != null) {
					for (final LogicalExpression exp : aligned) {
						LOG.debug("Alignment: %s -> %s", seq, exp);
						// Track this for logging.
						final int priorNumLexemes = lexemes.size();
						final List<LogicalConstant> constants = GetConstantsMultiSet
								.of(AMRServices.underspecifyAndStrip(exp))
								.stream()
								.filter(FactoringServices::isFactorable)
								.collect(Collectors.toList());
						// Remove the constants from the set we maintain for
						// logging.
						allConstants.removeAll(constants);

						for (final LexicalTemplate template : repo
								.getTemplates()) {
							for (final List<String> attributes : repo
									.getAttributeLists(template)) {
								for (final List<LogicalConstant> permutation : Collections2
										.permutations(constants)) {
									final Lexeme lexeme = new Lexeme(seq,
											permutation, attributes,
											entryProperties);
									if (!lexemes.contains(lexeme)
											&& template.isValid(lexeme)) {
										LOG.debug("Generated: %s", lexeme);
										lexemes.add(lexeme);
									}
								}
							}
						}
						LOG.debug("Generated %d new lexemes from alignment",
								lexemes.size() - priorNumLexemes);
					}
				}
			}
		}

		LOG.debug("Alignment GENLEX created %d lexemes (%.3fsec)",
				lexemes.size(),
				(System.currentTimeMillis() - startTime) / 1000.0);

		if (!allConstants.isEmpty()) {
			LOG.debug("Constants not covered by generator: %s", allConstants);
		}

		return new FactoredLexicon(lexemes, repo.getTemplates());
	}

	@Override
	public void init(
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {
		repo.init(model);
	}

	@Override
	public boolean isGenerated(LexicalEntry<LogicalExpression> entry) {
		return origin.equals(entry.getOrigin());
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

	@Override
	public void lexicalEntryAdded(LexicalEntry<LogicalExpression> entry) {
		final LexicalTemplate template = FactoringServices.factor(entry)
				.getTemplate();
		if (repo.addTemplate(template)) {
			LOG.info("Alignment GENLEX: added a new template (->%d): %s",
					repo.numTemplates(), template);
		}
	}

	public static class Creator
			implements IResourceObjectCreator<AlignmentGenlex> {

		private final String type;

		public Creator() {
			this("genlex.template.align");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AlignmentGenlex create(Parameters params,
				IResourceRepository repo) {
			return new AlignmentGenlex(
					params.get("origin",
							ILexiconGenerator.GENLEX_LEXICAL_ORIGIN),
					params.getAsBoolean("mark", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(), AlignmentGenlex.class)
					.addParam("origin", String.class,
							"Origin of generated entries (default: "
									+ ILexiconGenerator.GENLEX_LEXICAL_ORIGIN
									+ ")")
					.addParam("mark", Boolean.class,
							"Mark generated entries (default: false)")
					.build();
		}

	}

}
