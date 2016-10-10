package edu.uw.cs.lil.amr.parser;

import java.io.Serializable;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Filter to prune invalid AMR structures and enforce syntactic super-tagger
 * constraints.
 *
 * @author Yoav Artzi
 */
public class AmrParsingFilter implements IFilter<ParsingOp<LogicalExpression>>,
		Predicate<ParsingOp<LogicalExpression>>, Serializable {

	public static final ILogger	LOG					= LoggerFactory
			.create(AmrParsingFilter.class);
	private static final long	serialVersionUID	= 5613889979789771171L;

	@Override
	public boolean test(ParsingOp<LogicalExpression> op) {
		if (op.getCategory().getSemantics() != null && !IsValidAmr
				.of(op.getCategory().getSemantics(), false, false)) {
			return false;
		}

		return true;
	}
}
