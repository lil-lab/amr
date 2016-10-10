package edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Shift a named-entity to a lone keyword.
 *
 * @author Yoav Artzi
 */
public class NamedEntityStamp extends AbstractNpKeyword {

	private static final String	LABEL				= "ne_stamp";

	private static final long	serialVersionUID	= -191120797708674808L;

	private final Syntax		sourceSyntax;

	public NamedEntityStamp() {
		super(UnaryRuleName.create(LABEL));
		this.sourceSyntax = Syntax.read("NP[sg]");
	}

	private static boolean isNamedEntity(LogicalExpression semantics) {
		return semantics != null && AMRServices.isSkolemTerm(semantics)
				&& AMRServices.isNamedEntity((Literal) semantics);
	}

	@Override
	public boolean isValidArgument(Category<LogicalExpression> category,
			SentenceSpan span) {
		return span.isCompleteSentence()
				&& category.getSyntax().equals(sourceSyntax)
				&& isNamedEntity(category.getSemantics());
	}

	public static class Creator implements
			IResourceObjectCreator<NamedEntityStamp> {

		private final String	type;

		public Creator() {
			this("rule.shift.amr.nestamp");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public NamedEntityStamp create(Parameters params,
				IResourceRepository repo) {
			return new NamedEntityStamp();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, DateStamp.class)
					.setDescription("Shift a named-entity to a lone keyword.")
					.build();
		}

	}

}
