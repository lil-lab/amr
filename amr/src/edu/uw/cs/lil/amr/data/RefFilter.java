package edu.uw.cs.lil.amr.data;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsSet;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Filter to prune samples that contain references.
 *
 * @author Yoav Artzi
 */
public class RefFilter implements IFilter<LabeledAmrSentence> {

	@Override
	public boolean test(LabeledAmrSentence e) {
		for (final LogicalConstant constant : GetConstantsSet.of(e.getLabel())) {
			if (AMRServices.isRefPredicate(constant)) {
				return false;
			}
		}
		return true;
	}

	public static class Creator implements IResourceObjectCreator<RefFilter> {

		private final String	type;

		public Creator() {
			this("filter.ref");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public RefFilter create(Parameters params, IResourceRepository repo) {
			return new RefFilter();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, RefFilter.class)
					.setDescription(
							"Filter to prune samples that contain references.")
					.build();
		}

	}

}
