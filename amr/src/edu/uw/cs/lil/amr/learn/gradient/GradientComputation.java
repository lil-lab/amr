package edu.uw.cs.lil.amr.learn.gradient;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

/**
 * The result of a gradient computation, including the gradient update, various
 * statistics, flags, and the viterbi lexical entries. The set of entries used
 * may be empty even though there's a gradient. This might happen when a partial
 * gradient is generated. The exact choice depends on the object generating the
 * computation.
 *
 * @author Yoav Artzi
 */
public class GradientComputation implements Serializable {

	private static final long							serialVersionUID	= -1890179219796350126L;
	private final boolean								exact;
	private final IHashVector							gradient;
	private final boolean								labelIsOptimal;
	private final boolean								partial;
	private final StatUpdates							statUpdates;
	private final Set<LexicalEntry<LogicalExpression>>	viterbiEntries;

	GradientComputation(IHashVector gradient, boolean partial,
			boolean labelIsOptimal, StatUpdates statUpdates,
			Set<LexicalEntry<LogicalExpression>> viterbiEntries,
			boolean exact) {
		this.gradient = gradient;
		this.partial = partial;
		this.labelIsOptimal = labelIsOptimal;
		this.statUpdates = statUpdates;
		this.exact = exact;
		this.viterbiEntries = Collections.unmodifiableSet(viterbiEntries);
	}

	public IHashVector getGradient() {
		return gradient;
	}

	public StatUpdates getStatUpdates() {
		return statUpdates;
	}

	public Set<LexicalEntry<LogicalExpression>> getViterbiEntries() {
		return viterbiEntries;
	}

	public boolean isExact() {
		return exact;
	}

	public boolean isLabelIsOptimal() {
		return labelIsOptimal;
	}

	public boolean isPartial() {
		return partial;
	}

}
