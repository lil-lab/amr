package edu.uw.cs.lil.amr.parser.lexicon;

import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Lexicon with underspecified logical forms creator.
 *
 * @author Yoav Artzi
 */
public class UnderspecifiedLexiconCreator implements
		IResourceObjectCreator<Lexicon<LogicalExpression>> {

	private final Lexicon.Creator<LogicalExpression>	baseCreator	= new Lexicon.Creator<>();

	@Override
	public Lexicon<LogicalExpression> create(Parameters params,
			IResourceRepository repo) {
		final Lexicon<LogicalExpression> readLexicon = baseCreator.create(
				params, repo);
		final Lexicon<LogicalExpression> underspecLexicon = new Lexicon<>();
		for (final LexicalEntry<LogicalExpression> entry : readLexicon
				.toCollection()) {
			underspecLexicon.add(AMRServices.underspecifyAndStrip(entry));
		}
		return underspecLexicon;
	}

	@Override
	public String type() {
		return "lexicon.underspec";
	}

	@Override
	public ResourceUsage usage() {
		return baseCreator.usage();
	}

}
