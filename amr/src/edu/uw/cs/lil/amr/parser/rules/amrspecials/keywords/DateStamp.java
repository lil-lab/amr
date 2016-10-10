package edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Shift a date stamp to a complete sentence.
 *
 * @author Yoav Artzi
 */
public class DateStamp extends AbstractNpKeyword {

	public static final ILogger		LOG					= LoggerFactory
																.create(DateStamp.class);
	private static final String		LABEL				= "date_stamp";
	private static final long		serialVersionUID	= 2603685556859233957L;

	private final LogicalConstant	dateInstanceType;

	private final Syntax			sourceSyntax;

	public DateStamp() {
		super(UnaryRuleName.create(LABEL));
		this.sourceSyntax = Syntax.read("NP[sg]");
		this.dateInstanceType = LogicalConstant.read("date-entity:<e,t>");
	}

	@Override
	public boolean isValidArgument(Category<LogicalExpression> category,
			SentenceSpan span) {
		return span.isCompleteSentence()
				&& category.getSyntax().equals(sourceSyntax)
				&& isDateSemantics(category.getSemantics());
	}

	private boolean isDateSemantics(LogicalExpression semantics) {
		if (semantics == null || !AMRServices.isSkolemTerm(semantics)) {
			return false;
		}

		final LogicalConstant typingPredicate = AMRServices
				.getTypingPredicate((Literal) semantics);
		if (typingPredicate == null) {
			LOG.error("Null typing predicate: %s", semantics);
			return false;
		}

		return typingPredicate.equals(dateInstanceType);
	}

	public static class Creator implements IResourceObjectCreator<DateStamp> {

		private final String	type;

		public Creator() {
			this("rule.shift.amr.datestamp");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public DateStamp create(Parameters params, IResourceRepository repo) {
			return new DateStamp();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, DateStamp.class)
					.setDescription(
							"Shift a date stamp to a complete sentence.")
					.build();
		}

	}

}
