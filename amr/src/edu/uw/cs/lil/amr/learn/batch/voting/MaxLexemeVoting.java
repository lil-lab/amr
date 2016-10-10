package edu.uw.cs.lil.amr.learn.batch.voting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.Lexeme;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * MaxVoting for {@link Lexeme}s. Similar to the max voting procedure described
 * in Artzi et al. 2014. Although it's applied to {@link LexicalEntry}s, it
 * actually does the voting over {@link Lexeme}s.
 *
 * @author Yoav Artzi
 */
public class MaxLexemeVoting
		implements
		BiFunction<Predicate<LexicalEntry<LogicalExpression>>, Map<LexicalEntry<LogicalExpression>, Double>, Set<LexicalEntry<LogicalExpression>>> {

	public static final ILogger							LOG			= LoggerFactory
																			.create(MaxLexemeVoting.Creator.class);

	private final MaxVotingFunction<Lexeme, TokenSeq>	lexemeVoter	= new MaxVotingFunction<>(
																			l -> l.getTokens());

	@Override
	public Set<LexicalEntry<LogicalExpression>> apply(
			Predicate<LexicalEntry<LogicalExpression>> hardFilter,
			final Map<LexicalEntry<LogicalExpression>, Double> votes) {
		// Create a voting dictionary for lexemes, aggregated over the lexical
		// entry votes.
		final Map<Lexeme, Double> lexemeVotes = new HashMap<>();
		for (final Entry<LexicalEntry<LogicalExpression>, Double> vote : votes
				.entrySet()) {
			if (hardFilter.test(vote.getKey())) {
				final Lexeme lexeme = FactoringServices.factor(vote.getKey())
						.getLexeme();
				final Double value = vote.getValue();
				if (lexemeVotes.containsKey(lexeme)) {
					lexemeVotes.put(lexeme, lexemeVotes.get(lexeme) + value);
				} else {
					lexemeVotes.put(lexeme, value);
				}
			}
		}

		// Vote over lexemes.
		final Set<Lexeme> votedLexemes = lexemeVoter.apply(l -> true,
				lexemeVotes);

		// Get all entries that pass the hard filter and their lexeme was voted.
		final Set<LexicalEntry<LogicalExpression>> votedEntries = new HashSet<>();
		for (final Entry<LexicalEntry<LogicalExpression>, Double> vote : votes
				.entrySet()) {
			if (hardFilter.test(vote.getKey())) {
				final Lexeme lexeme = FactoringServices.factor(vote.getKey())
						.getLexeme();
				if (votedLexemes.contains(lexeme)) {
					votedEntries.add(vote.getKey());
				}
			}
		}

		return votedEntries;
	}

	public static class Creator implements
			IResourceObjectCreator<MaxLexemeVoting> {

		private final String	type;

		public Creator() {
			this("voter.max.lexeme");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public MaxLexemeVoting create(Parameters params,
				IResourceRepository repo) {
			return new MaxLexemeVoting();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, MaxLexemeVoting.class)
					.setDescription("MaxVoting for lexemes.").build();
		}

	}

}
