package edu.uw.cs.lil.amr.learn.genlex;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.Lexeme;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.LexicalTemplate;
import edu.cornell.cs.nlp.spf.data.ILabeledDataItem;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.genlex.ccg.AbstractLexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.template.GenerationRepository;
import edu.cornell.cs.nlp.spf.genlex.ccg.template.GenerationRepositoryWithConstants;
import edu.cornell.cs.nlp.spf.genlex.ccg.template.TemplateSupervisedGenlex;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsSet;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelImmutable;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelListener;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import jregex.Pattern;
import jregex.Replacer;

/**
 * GENLEX procedure to generate {@link LexicalEntry}s for named-entities that
 * have txt-typed mentions in the logical form.
 *
 * @author Yoav Artzi
 * @param <SAMPLE>
 *            Inference sample.
 * @param <DI>
 *            Training data item.
 */
public class TextEntitiesGenlex<SAMPLE extends Sentence, DI extends ILabeledDataItem<SAMPLE, LogicalExpression>>
		extends
		AbstractLexiconGenerator<DI, LogicalExpression, IModelImmutable<Sentence, LogicalExpression>>
		implements IModelListener<LogicalExpression> {
	public static final ILogger			LOG					= LoggerFactory
			.create(TextEntitiesGenlex.class);

	private static final Replacer		NON_ALPHA_REPLACER	= new Replacer(
			new Pattern("[^a-z0-9A-Z]+"), " ");

	private static final long			serialVersionUID	= -7164033331979825717L;
	private final Set<String>			allowedAttributes;

	private final boolean				generateAmrKeyword;

	private final GenerationRepository	repository			= new GenerationRepository();

	private final Type					textType;

	public TextEntitiesGenlex(String origin, Type textType,
			boolean generateAmrKeyword, boolean mark,
			Set<String> allowedAttributes) {
		super(origin, mark);
		this.textType = textType;
		this.generateAmrKeyword = generateAmrKeyword;
		this.allowedAttributes = allowedAttributes;
	}

	@Override
	public ILexiconImmutable<LogicalExpression> generate(DI dataItem,
			IModelImmutable<Sentence, LogicalExpression> model,
			ICategoryServices<LogicalExpression> categoryServices) {
		final Set<LogicalConstant> allConstants = GetConstantsSet
				.of(dataItem.getLabel());
		// Get all txt-typed constants from the logical from.
		final List<LogicalConstant> textConstants = allConstants.stream()
				.filter((c) -> c.getType().equals(textType))
				.collect(Collectors.toList());
		final List<String> textConstantStrings = textConstants.stream()
				.map((c) -> NON_ALPHA_REPLACER.replace(c.getBaseName()).trim())
				.collect(Collectors.toList());
		final boolean[] generatedFlags = new boolean[textConstants.size()];

		final Set<Lexeme> lexemes = new HashSet<>();
		final TokenSeq tokens = dataItem.getSample().getTokens();
		final int numTokens = tokens.size();
		final GenerationRepositoryWithConstants repoWithConstants = repository
				.setConstants(allConstants);

		// Iterate over all spans.
		for (int start = 0; start < numTokens; ++start) {
			for (int end = start; end < numTokens; ++end) {
				final TokenSeq ngram = tokens.sub(start, end + 1);
				final String normalizedNgram = NON_ALPHA_REPLACER.replace(ngram
						.toList().stream().collect(Collectors.joining(" ")))
						.trim();
				int i = 0;
				for (final LogicalConstant textConstant : textConstants) {
					if (normalizedNgram
							.equalsIgnoreCase(textConstantStrings.get(i))) {
						final List<Lexeme> ngramLexemes = createLexemes(
								repoWithConstants, textConstant, ngram);
						if (!ngramLexemes.isEmpty()) {
							lexemes.addAll(ngramLexemes);
							LOG.debug(() -> {
								for (final Lexeme lexeme : ngramLexemes) {
									LOG.debug("Created lexeme: %s", lexeme);
								}
							});
							generatedFlags[i] = true;

							// Special case for countries followed by their
							// international code, e.g., Russian Federation (RU)
							// (which is tokenized by the PTB to Russian
							// Federation
							// -LRB- RU -RRB-).
							if (generateAmrKeyword && end + 3 < numTokens
									&& tokens.get(end + 1)
											.equalsIgnoreCase("-LRB-")
									&& tokens.get(end + 3)
											.equalsIgnoreCase("-RRB-")) {
								lexemes.addAll(createLexemes(repoWithConstants,
										textConstant,
										tokens.sub(start, end + 4)));
							}
						}
					}
					++i;
				}
			}
		}

		LOG.debug(() -> {
			for (final Lexeme lexeme : lexemes) {
				LOG.debug("Generated lexeme: %s", lexeme);
			}
		});

		// Log all txt-typed constants with no generated lexemes.
		LOG.info(() -> {
			for (int i = 0; i < generatedFlags.length; ++i) {
				if (!generatedFlags[i]) {
					LOG.info("No text lexemes generated for: %s (%s)",
							textConstants.get(i), textConstantStrings.get(i));
				}
			}
		});

		return new FactoredLexicon(lexemes, repoWithConstants.getTemplates());
	}

	@Override
	public void init(IModelImmutable<Sentence, LogicalExpression> model) {
		repository.init(model);
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
		for (final Type argType : template.getSignature().getTypes()) {
			if (argType.isExtending(textType)) {
				if (repository.addTemplate(template)) {
					LOG.info(
							"Text entities GENLEX: Added new template (-> %d): %s",
							repository.numTemplates(), template);
				}
			}
		}

	}

	private List<Lexeme> createLexemes(
			GenerationRepositoryWithConstants repoWithConstants,
			LogicalConstant textConstant, TokenSeq ngram) {
		return repoWithConstants.getTemplates().stream().map(template -> {
			final List<List<String>> attributes = repoWithConstants
					.getAttributeLists(template).stream()
					.filter(attrib -> allowedAttributes.containsAll(attrib))
					.collect(Collectors.toList());
			// Filter sequences that don't contain the text constant and
			// create lexemes with all attributes that match this
			// template.
			return repoWithConstants.getConstantSeqs(template).stream()
					.filter(seq -> seq.contains(textConstant))
					.map(seq -> attributes.stream()
							.map(attribSeq -> new Lexeme(ngram, seq, attribSeq,
									entryProperties))
							.collect(Collectors.toList()))
					.flatMap(Collection::stream).collect(Collectors.toList());
		}).flatMap(Collection::stream).collect(Collectors.toList());
	}

	public static class Creator<SAMPLE extends Sentence, DI extends ILabeledDataItem<SAMPLE, LogicalExpression>>
			implements IResourceObjectCreator<TextEntitiesGenlex<SAMPLE, DI>> {

		private final String type;

		public Creator() {
			this("genlex.template.text");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public TextEntitiesGenlex<SAMPLE, DI> create(Parameters params,
				IResourceRepository repo) {
			return new TextEntitiesGenlex<>(params.get("origin", "genlex-txt"),
					LogicLanguageServices.getTypeRepository()
							.getType(params.get("textType")),
					params.getAsBoolean("generateKeywords", false),
					params.getAsBoolean("mark", false),
					new HashSet<>(params.getSplit("attributes")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(),
					TemplateSupervisedGenlex.class)
							.setDescription(
									"GENLEX procedure to generate {@link LexicalEntry}s for named-entities that have txt-typed mentions in the logical form")
							.addParam("textType", Type.class,
									"Type of text-constants")
							.addParam("origin", String.class,
									"Origin of generated entries (default: genlex-txt)")
							.addParam("mark", Boolean.class,
									"Mark generated entries (default: false)")
							.addParam("generatedKeywords", Boolean.class,
									"Generate AMR-specific keyword NPs (e.g., for France FR) (default: false)")
							.addParam("attributes", String.class,
									"The set of allowed syntax attributes")
							.build();
		}

	}

}
