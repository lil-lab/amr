package edu.uw.cs.lil.amr.learn.batch.voting;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * MaxVoting voting strategy, as described in Artzi et al. 2014. This is a
 * generic implementation that can be applied to any object.
 *
 * @author Yoav Artzi
 */
public class MaxVotingFunction<ITEM, COMMON> implements
		BiFunction<Predicate<ITEM>, Map<ITEM, Double>, Set<ITEM>> {

	private final Function<ITEM, COMMON>	grouper;

	public MaxVotingFunction(Function<ITEM, COMMON> grouper) {
		this.grouper = grouper;
	}

	@Override
	public Set<ITEM> apply(Predicate<ITEM> hardFilter,
			final Map<ITEM, Double> votes) {
		// Create index of tokens to the max-voted lexical entry, if there
		// exists a single one. Ignore entries that use templates that appear
		// only in one data item (not in validTemplates). Also, ignore known
		// entries.
		final Map<COMMON, ITEM> maxEntries = new HashMap<>();
		final Map<COMMON, Double> maxVotes = new HashMap<>();
		for (final Entry<ITEM, Double> entryVote : votes.entrySet()) {
			final double vote = entryVote.getValue();
			final ITEM entry = entryVote.getKey();
			// Only process if passes the hard filter.
			if (hardFilter.test(entry)) {
				final COMMON tokens = grouper.apply(entry);
				if (maxVotes.containsKey(tokens)) {
					final double currentMax = maxVotes.get(tokens);
					if (vote > currentMax) {
						maxEntries.put(tokens, entry);
						maxVotes.put(tokens, vote);
					} else if (vote == currentMax) {
						maxEntries.put(tokens, (ITEM) null);
					}
				} else {
					maxVotes.put(tokens, vote);
					maxEntries.put(tokens, entry);
				}
			}
		}

		// The set of of entries to be added.
		final Set<ITEM> votedEntries = maxEntries.values().stream()
				.filter(e -> e != null).collect(Collectors.toSet());
		return votedEntries;
	}

}
