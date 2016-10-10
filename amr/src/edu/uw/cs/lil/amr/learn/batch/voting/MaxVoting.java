package edu.uw.cs.lil.amr.learn.batch.voting;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.Lexeme;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;

/**
 * MaxVoting for {@link LexicalEntry}s. Similar to the max voting procedure
 * described in Artzi et al. 2014, but applied to {@link LexicalEntry}s rather
 * than {@link Lexeme}s.
 *
 * @author Yoav Artzi
 * @param <MR>
 *            Meaning representation.
 */
public class MaxVoting<MR> extends
		MaxVotingFunction<LexicalEntry<MR>, TokenSeq> {

	public MaxVoting() {
		super(e -> e.getTokens());
	}

	public static class Creator<MR> implements
			IResourceObjectCreator<MaxVoting<MR>> {

		private final String	type;

		public Creator() {
			this("voter.max");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public MaxVoting<MR> create(Parameters params, IResourceRepository repo) {
			return new MaxVoting<>();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, MaxVoting.class)
					.setDescription(
							"MaxVoting for lexical entries. Similar to the max voting procedure described in Artzi et al. 2014, but applied to lexical entries rather than lexemes.")
					.build();
		}

	}

}
