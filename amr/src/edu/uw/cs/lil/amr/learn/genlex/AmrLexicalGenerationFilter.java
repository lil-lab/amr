package edu.uw.cs.lil.amr.learn.genlex;

import java.io.Serializable;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Heuristic filter to prune some generated entries.
 *
 * @author Yoav Artzi
 */
public class AmrLexicalGenerationFilter implements
		Predicate<Category<LogicalExpression>>, Serializable {

	public static final ILogger	LOG					= LoggerFactory
															.create(AmrLexicalGenerationFilter.class);
	private static final long	serialVersionUID	= 4951264862202748769L;

	@Override
	public boolean test(Category<LogicalExpression> category) {
		final Syntax syntax = category.getSyntax();

		// Ignore any generated entry with the syntax KEY.
		if (syntax.containsSubSyntax(AMRServices.KEY)) {
			LOG.debug("Pruned generated lexical category (contains KEY): %s",
					category);
			return false;
		}

		// Ignore any generated entry with the syntax PUNCT.
		if (syntax.containsSubSyntax(Syntax.PUNCT)) {
			LOG.debug("Pruned generated lexical category (contains PUNCT): %s",
					category);
			return false;
		}

		return true;
	}

	public static class Creator implements
			IResourceObjectCreator<AmrLexicalGenerationFilter> {

		private final String	type;

		public Creator() {
			this("genlex.filter.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AmrLexicalGenerationFilter create(Parameters params,
				IResourceRepository repo) {
			return new AmrLexicalGenerationFilter();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, AmrLexicalGenerationFilter.class)
					.setDescription(
							"Heuristic filter to prune some generated entries")
					.build();
		}

	}

}
