package edu.uw.cs.lil.amr.parser.genlex;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

import jregex.Pattern;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.SimpleCategory;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ISentenceLexiconGenerator;
import edu.cornell.cs.nlp.utils.collections.MapUtils;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Rule based dynamic lexical entry generator for numerals.
 *
 * @author Yoav Artzi
 */
public class NumeralGenerator implements
		ISentenceLexiconGenerator<SituatedSentence<AMRMeta>, LogicalExpression> {

	private static final Pattern	NUMBER				= new Pattern("\\d+");
	private static final long		serialVersionUID	= 2502606053289239706L;
	private final String			origin;

	public NumeralGenerator(String origin) {
		this.origin = origin;
	}

	public static Category<LogicalExpression> getInteger(String string,
			IntPredicate filter) {
		final LogicalConstant constant = getIntegerConstant(string, filter);
		if (constant == null) {
			return null;
		} else {
			return new SimpleCategory<>(AMRServices.I, constant);
		}
	}

	public static LogicalConstant getIntegerConstant(String string,
			IntPredicate filter) {
		if (NUMBER.matches(string) && filter.test(Integer.valueOf(string))) {
			return LogicalConstant.create(Integer.valueOf(string).toString(),
					LogicLanguageServices.getNumeralType(), true);
		} else {
			return null;
		}
	}

	public static Integer stringToInt(String string) {
		if (NUMBER.matches(string)) {
			return Integer.valueOf(string);
		} else {
			return null;
		}
	}

	@Override
	public Set<LexicalEntry<LogicalExpression>> generateLexicon(
			SituatedSentence<AMRMeta> sample) {
		final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
		final TokenSeq tokens = sample.getTokens();

		// 13000 :- I : 13000:i
		// 50 :- I : 50:i
		for (int i = 0; i < tokens.size(); ++i) {
			final TokenSeq token = tokens.sub(i, i + 1);
			final Category<LogicalExpression> integer = getInteger(
					token.toString(), n -> true);
			if (integer != null) {
				entries.add(new LexicalEntry<>(token, integer, true, MapUtils
						.createSingletonMap(LexicalEntry.ORIGIN_PROPERTY,
								origin)));
			}
		}

		return entries;
	}

	public static class Creator implements
			IResourceObjectCreator<NumeralGenerator> {

		private final String	type;

		public Creator() {
			this("dyngen.amr.numeral");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public NumeralGenerator create(Parameters params,
				IResourceRepository repo) {
			return new NumeralGenerator(params.get("origin", "dyn-numeral"));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, NumeralGenerator.class)
					.addParam("origin", String.class,
							"Origin label for generated entries")
					.setDescription(
							"Rule based dynamic lexical entry generator for numerals")
					.build();
		}

	}

}
