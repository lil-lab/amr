package edu.uw.cs.lil.amr.learn.batch.voting;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

/**
 * Stub voter. Simply returns all {@link LexicalEntry}s.
 *
 * @author Yoav Artzi
 */
public class StubVoting
		implements
		BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> {

	@Override
	public Set<LexicalEntry<LogicalExpression>> apply(
			Predicate<LexicalEntry<LogicalExpression>> hardFilter,
			final Map<LexicalEntry<LogicalExpression>, Double> votes) {
		return votes.keySet();
	}

}
