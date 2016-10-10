package edu.uw.cs.lil.amr.parser.lexicon;

import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

/**
 * Non-factored lexicon that holds factored entries (i.e., each entry is made
 * out of a lexeme and template), but doesn't generalize to unobserved entries.
 * The purpose of this class is mainly for initialization. The entries in the
 * created lexicon will trigger factored lexical features. During
 * initialization, we can use the entries to initialize the factored features,
 * while avoiding initializing features for unobserved entries, as will be the
 * case if use a simple {@link FactoredLexicon}.
 *
 * @author Yoav Artzi
 *
 */
public class PseudoFactoredLexiconCreator
		implements IResourceObjectCreator<Lexicon<LogicalExpression>> {

	private final String type;

	public PseudoFactoredLexiconCreator() {
		this("lexicon.factored.pseudo");
	}

	public PseudoFactoredLexiconCreator(String type) {
		this.type = type;
	}

	@Override
	public Lexicon<LogicalExpression> create(Parameters params,
			IResourceRepository repo) {
		final ILexiconImmutable<LogicalExpression> entries = repo
				.get(params.get("entries"));
		return new Lexicon<>(entries.toCollection().stream()
				.map(FactoringServices::factor).collect(Collectors.toSet()));
	}

	@Override
	public String type() {
		return type;
	}

	@Override
	public ResourceUsage usage() {
		return ResourceUsage.builder(type, Lexicon.class)
				.setDescription(
						"Non-factored lexicon that holds factored entries (i.e., each entry is made out of a lexeme and template), but doesn't generalize to unobserved entries")
				.addParam("entries", ILexiconImmutable.class, "Source lexicon")
				.build();
	}

}
