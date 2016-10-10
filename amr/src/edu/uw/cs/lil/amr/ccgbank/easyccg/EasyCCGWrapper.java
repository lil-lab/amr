package edu.uw.cs.lil.amr.ccgbank.easyccg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import uk.ac.ed.easyccg.main.EasyCCG.InputFormat;
import uk.ac.ed.easyccg.syntax.InputReader;
import uk.ac.ed.easyccg.syntax.ParserAStar;
import uk.ac.ed.easyccg.syntax.SyntaxTreeNode;
import uk.ac.ed.easyccg.syntax.Tagger;
import uk.ac.ed.easyccg.syntax.TaggerEmbeddings;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.ccgbank.CcgBankServices;
import edu.uw.cs.lil.amr.ccgbank.IBankParser;
import edu.uw.cs.lil.amr.ccgbank.ISuperTagger;

/**
 * Wrapper for EasyCCG. Provides various CCGBank parsing services.
 *
 * @author Yoav Artzi
 */
public class EasyCCGWrapper implements ISuperTagger, IBankParser {
	public static final ILogger	LOG	= LoggerFactory
											.create(EasyCCGWrapper.class);

	private final ParserAStar	parser;

	private final Tagger		tagger;

	public EasyCCGWrapper(Tagger tagger, ParserAStar parser) {
		this.tagger = tagger;
		this.parser = parser;
	}

	@Override
	public Map<TokenSeq, Set<Syntax>> getSpans(Sentence sentence) {
		final List<SyntaxTreeNode> nodes = parser.parseTokens(sentence
				.getTokens().toList());
		if (nodes == null) {
			return Collections.emptyMap();
		}
		final Map<TokenSeq, Set<Syntax>> spans = new HashMap<>();
		for (final SyntaxTreeNode node : nodes) {
			getSpans(node, spans);
		}
		return spans;
	}

	@Override
	public List<Set<Syntax>> superTag(Sentence sentence) {
		final List<List<SyntaxTreeNode>> easyTags = tagger
				.tag(InputReader.InputToParser.fromTokens(
						sentence.getTokens().toList()).getInputWords());

		final int len = sentence.getTokens().size();
		final List<Set<Syntax>> superTags = new ArrayList<>(len);
		for (int i = 0; i < len; ++i) {
			superTags.add(CcgBankServices.rewrite(
					easyTags.get(i).stream().map(CcgBankServices::toSyntax)
							.filter(syntax -> syntax != null)
							.collect(Collectors.toSet()), sentence.getTokens()
							.get(i), true));
		}
		return superTags;
	}

	private void getSpans(SyntaxTreeNode node, Map<TokenSeq, Set<Syntax>> spans) {
		final TokenSeq tokens = TokenSeq.of(node.getWords().stream()
				.map(w -> w.getWord()).collect(Collectors.toList()));
		final Set<Syntax> syntax = CcgBankServices.rewrite(
				CcgBankServices.toSyntax(node), false);
		if (!spans.containsKey(tokens)) {
			spans.put(tokens, new HashSet<>());
		}
		spans.get(tokens).addAll(syntax);

		for (final SyntaxTreeNode child : node.getChildren()) {
			getSpans(child, spans);
		}

	}

	public static class Creator implements
			IResourceObjectCreator<EasyCCGWrapper> {
		@Override
		public EasyCCGWrapper create(Parameters params, IResourceRepository repo) {
			final TaggerEmbeddings tagger = new TaggerEmbeddings(
					params.getAsFile("model"), params.getAsInteger("maxLength",
							1000), params.getAsDouble("beamMultiplier", 0.0001));
			try {
				final ParserAStar parser = new ParserAStar(tagger,
						params.getAsInteger("maxLength", 1000),
						params.getAsInteger("nBest", 1), params.getAsDouble(
								"nBestBeam", 0.0), InputFormat.TOKENIZED,
						ListUtils.createList("S[dcl]", "S[wq]", "S[q]",
								"S[qem]", "NP"),
						params.getAsFile("unaryRules"),
						params.getAsFile("extraCombinators"),
						params.getAsFile("seenRules"));
				return new EasyCCGWrapper(tagger, parser);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String type() {
			return "easyccg";
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type(), Tagger.class)
					.setDescription("EasyCCG wrapper (supertagger and parser)")
					.addParam("nBest", Integer.class,
							"Number of parses to return per sentence (default: 1)")
					.addParam(
							"nBestBeam",
							Double.class,
							"If nBest > 1, filter parses whose probability is lower than this fraction of the probability of the best parse (default: 0.0)")
					.addParam("maxLength", Integer.class,
							"Maximum length of input sentences (default: 1000)")
					.addParam("unaryRules", File.class,
							"EasyCCG unary rules file")
					.addParam("extraCombinators", File.class,
							"EasyCCG extra combinators file")
					.addParam("seenRules", File.class,
							"EasyCCG seen rules file")
					.addParam("model", File.class, "EasyCCG model directory")
					.addParam("beamMultiplier", Double.class,
							"EasyCCG super-tagger beam multiplier (default: 0.0001)")
					.build();
		}
	}
}
