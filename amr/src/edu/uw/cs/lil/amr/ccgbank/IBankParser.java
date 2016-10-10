package edu.uw.cs.lil.amr.ccgbank;

import java.util.Map;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;

/**
 * CCGBank parser to annotate sentences with parsing information.
 *
 * @author Yoav Artzi
 */
public interface IBankParser {

	/**
	 * Given a {@link Sentence}, returns a mapping of sub-sequences of
	 * {@link TokenSeq} from the sentence to all {@link Syntax} categories
	 * paired with them in the max-scoring CCGBank parse tree.
	 */
	public Map<TokenSeq, Set<Syntax>> getSpans(Sentence sentence);

}
