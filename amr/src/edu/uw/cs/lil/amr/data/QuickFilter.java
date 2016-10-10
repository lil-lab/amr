package edu.uw.cs.lil.amr.data;

import java.util.List;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.filter.IFilter;

/**
 * Filter to prune samples that have too many tokens or contain illegal
 * characters. This filter is used to run quick sanity experiment.
 *
 * @author Yoav Artzi
 */
public class QuickFilter implements IFilter<LabeledAmrSentence> {

	private final List<String>	illegalSubStrings;
	private final int			maxLength;

	public QuickFilter(List<String> illegalSubStrings, int maxLength) {
		this.illegalSubStrings = illegalSubStrings;
		this.maxLength = maxLength;
	}

	@Override
	public boolean test(LabeledAmrSentence e) {
		if (e.getSample().getTokens().size() > maxLength) {
			return false;
		}

		for (final String sub : illegalSubStrings) {
			if (e.getSample().getString().contains(sub)) {
				return false;
			}
		}

		return true;
	}

	public static class Creator implements IResourceObjectCreator<QuickFilter> {

		private final String type;

		public Creator() {
			this("filter.quick");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public QuickFilter create(Parameters params, IResourceRepository repo) {
			return new QuickFilter(params.getSplit("chars"),
					params.getAsInteger("len"));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, QuickFilter.class)
					.addParam("len", Integer.class, "Max number of tokens")
					.addParam("chars", String.class,
							"List of illegal characters")
					.setDescription(
							"Filter to prune according to characters and length.")
					.build();
		}

	}

}
