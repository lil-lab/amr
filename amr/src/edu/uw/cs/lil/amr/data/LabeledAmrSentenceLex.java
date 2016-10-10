package edu.uw.cs.lil.amr.data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.ccgbank.IBankParser;
import edu.uw.cs.lil.amr.ccgbank.ISuperTagger;

/**
 * Labeled AMR sentence with a set of lexical entries.
 *
 * @author Yoav Artzi
 */
public class LabeledAmrSentenceLex extends LabeledAmrSentence {

	private static final long							serialVersionUID	= -7236837926337363845L;
	private final Set<LexicalEntry<LogicalExpression>>	entries;

	public LabeledAmrSentenceLex(SituatedSentence<AMRMeta> sentence,
			LogicalExpression label, Map<String, String> properties,
			Set<LexicalEntry<LogicalExpression>> entries,
			ICategoryServices<LogicalExpression> categoryServices,
			ISuperTagger superTagger, IBankParser bankParser) {
		super(sentence, label, properties, categoryServices, superTagger,
				bankParser);
		this.entries = entries;
	}

	public LabeledAmrSentenceLex(SituatedSentence<AMRMeta> sentence,
			LogicalExpression label,
			Set<LexicalEntry<LogicalExpression>> entries,
			ICategoryServices<LogicalExpression> categoryServices,
			ISuperTagger superTagger, IBankParser bankParser) {
		this(sentence, label, Collections.emptyMap(), entries, categoryServices,
				superTagger, bankParser);
	}

	public Set<LexicalEntry<LogicalExpression>> getEntries() {
		return entries;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(super.toString());
		for (final LexicalEntry<LogicalExpression> entry : entries) {
			sb.append("\n").append(entry);
		}
		return sb.toString();
	}

}
