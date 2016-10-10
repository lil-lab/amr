package edu.uw.cs.lil.amr.parser.genlex;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ReplaceConstants;
import edu.cornell.cs.nlp.spf.parser.ISentenceLexiconGenerator;
import edu.cornell.cs.nlp.utils.collections.MapUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.ner.RecognizedNamedEntity;

/**
 * Dynamic lexical generator that use a named entity recognizer to generate
 * lexical entries on the fly.
 *
 * @author Yoav Artzi
 */
public class NamedEntityGenerator implements
		ISentenceLexiconGenerator<SituatedSentence<AMRMeta>, LogicalExpression> {

	public static final ILogger		LOG					= LoggerFactory
																.create(NamedEntityGenerator.class);
	private static final long		serialVersionUID	= 9093249533930996072L;
	private final String			baseLabel;
	private final LogicalExpression	helperSemantics;
	private final LogicalConstant	namePlceholder;
	private final Syntax			singularNounSyntax;
	private final Syntax			singularNPSyntax;
	private final LogicalConstant	typePlaceholder;

	public NamedEntityGenerator(
			ICategoryServices<LogicalExpression> categoryServices,
			String baseLabel) {
		this.baseLabel = baseLabel;
		this.helperSemantics = categoryServices
				.readSemantics("(lambda $0:e (and:<t*,t> (TYPE:<e,t> $0) (c_name:<e,<e,t>> $0 "
						+ "(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (name:<e,t> $1) (c_op:<e,<txt,t>> $1 NAME:txt)))))))");
		this.typePlaceholder = LogicalConstant.read("TYPE:<e,t>");
		this.namePlceholder = LogicalConstant.read("NAME:txt");
		this.singularNounSyntax = Syntax.read("N[sg]");
		this.singularNPSyntax = Syntax.read("NP[sg]");
	}

	@Override
	public Set<LexicalEntry<LogicalExpression>> generateLexicon(
			SituatedSentence<AMRMeta> sample) {
		final Set<RecognizedNamedEntity> namedEntities = sample.getState()
				.getNamedEntities();
		if (namedEntities == null) {
			return Collections.emptySet();
		} else {
			final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
			for (final RecognizedNamedEntity namedEntity : namedEntities) {
				final LogicalConstant textConstant = namedEntity
						.getTextConstant();
				for (final LogicalConstant typingConstant : namedEntity
						.getTypingConstants()) {
					// Create replacement map and prepare the internal
					// semantics.
					final Map<LogicalConstant, LogicalExpression> replacements = new HashMap<>();
					replacements.put(typePlaceholder, typingConstant);
					replacements.put(namePlceholder, textConstant);
					final LogicalExpression setSemantics = AMRServices
							.underspecifyAndStrip(ReplaceConstants.of(
									helperSemantics, replacements));
					assert setSemantics != helperSemantics : "Replacement failed";

					// Create the origin base label. This origin can be used to
					// create features to control this process.
					final String baseOrigin = new StringBuilder(baseLabel)
							.append("-").append(namedEntity.getRawTag())
							.append("-").append(typingConstant).append("-")
							.toString();

					// Create a N[sg] category and lexical entry.
					entries.add(new LexicalEntry<>(namedEntity.getTokens(),
							Category.create(singularNounSyntax, setSemantics),
							true, MapUtils.createSingletonMap(
									LexicalEntry.ORIGIN_PROPERTY, baseOrigin
											+ "N[sg]")));

					// Create a NP[sg] category and lexical entry.
					entries.add(new LexicalEntry<>(namedEntity.getTokens(),
							Category.create(singularNPSyntax,
									AMRServices.skolemize(setSemantics)), true,
							MapUtils.createSingletonMap(
									LexicalEntry.ORIGIN_PROPERTY, baseOrigin
											+ "NP[sg]")));

				}
			}
			return entries;
		}
	}

	public static class Creator implements
			IResourceObjectCreator<NamedEntityGenerator> {

		private final String	type;

		public Creator() {
			this("dyngen.amr.ner");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public NamedEntityGenerator create(Parameters params,
				IResourceRepository repo) {
			return new NamedEntityGenerator(
					repo.get(ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE),
					params.get("label", "dyn-ner"));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, NamedEntityGenerator.class)
					.setDescription(
							"Dynamic lexical generator using a named entity recognizer")
					.addParam("label", String.class,
							"Base origin label (default: dyn-ner)").build();
		}

	}
}
