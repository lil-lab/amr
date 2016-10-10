package edu.uw.cs.lil.amr.ccgbank;

import java.util.List;
import java.util.Set;

import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;

/**
 * Tagger to annotate a sentence with CCG super-tags.
 * 
 * @author Yoav Artzi
 */
public interface ISuperTagger {
	/**
	 * Generate super-tags for the given sentence.
	 *
	 * @return List of sets, one for each token.
	 */
	public List<Set<Syntax>> superTag(Sentence sentence);
}
