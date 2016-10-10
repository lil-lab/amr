package edu.uw.cs.lil.amr.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.data.situated.labeled.LabeledSituatedSentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.ccgbank.IBankParser;
import edu.uw.cs.lil.amr.ccgbank.ISuperTagger;
import edu.uw.cs.lil.amr.jamr.alignment.AlignmentServices;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class LabeledAmrSentence
		extends LabeledSituatedSentence<AMRMeta, LogicalExpression> {

	public static final ILogger LOG = LoggerFactory
			.create(LabeledAmrSentence.class);

	private static final long serialVersionUID = 1063135963753141096L;

	private final Map<Pair<Integer, Integer>, Set<LogicalExpression>>	alignments;
	private final Map<TokenSeq, Set<Syntax>>							spans;

	private final List<Set<Syntax>> superTags;

	public LabeledAmrSentence(SituatedSentence<AMRMeta> sentence,
			LogicalExpression label, Map<String, String> properties,
			ICategoryServices<LogicalExpression> categoryServices,
			ISuperTagger superTagger, IBankParser bankParser) {
		super(sentence, label, properties);

		this.superTags = superTagger == null ? null
				: superTagger.superTag(sentence);
		this.spans = bankParser == null ? null : bankParser.getSpans(sentence);

		if (properties
				.containsKey(AlignmentServices.STORED_ALIGNMENTS_PROPERTY)) {
			this.alignments = Collections
					.unmodifiableMap(AlignmentServices.readStoredAlignments(
							properties
									.get(AlignmentServices.STORED_ALIGNMENTS_PROPERTY),
							categoryServices));
		} else {
			this.alignments = null;
		}
	}

	public LabeledAmrSentence(SituatedSentence<AMRMeta> sentence,
			LogicalExpression label, Map<String, String> properties,
			Map<Pair<Integer, Integer>, Set<LogicalExpression>> alignments,
			List<Set<Syntax>> superTags, Map<TokenSeq, Set<Syntax>> spans) {
		super(sentence, label, properties);
		this.alignments = alignments;
		this.superTags = superTags;
		this.spans = spans;
	}

	public Set<LogicalExpression> getAlignedExpressions(int start, int end) {
		return alignments.get(Pair.of(start, end));
	}

	public Map<Pair<Integer, Integer>, Set<LogicalExpression>> getAlignments() {
		return alignments;
	}

	public Set<Syntax> getCCGBankCategories(TokenSeq tokens) {
		if (spans == null) {
			return null;
		} else if (spans.containsKey(tokens)) {
			return Collections.unmodifiableSet(spans.get(tokens));
		} else {
			return Collections.emptySet();
		}
	}

	public Set<Syntax> getSuperTags(int index) {
		return superTags == null ? null : superTags.get(index);
	}

	public boolean hasAlignments() {
		return alignments != null;
	}

	public boolean isCCGBankSpan(TokenSeq tokens) {
		return spans == null ? true : spans.containsKey(tokens);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(getSample().toString())
				.append("\n").append(AMRServices.toString(getLabel()));

		if (spans != null) {
			for (final Entry<TokenSeq, Set<Syntax>> entry : spans.entrySet()) {
				sb.append("\n");
				sb.append(entry.getKey());
				sb.append(" :- ");
				sb.append(entry.getValue().stream().map(e -> e.toString())
						.collect(Collectors.joining(", ")));

			}
		}

		return sb.toString();
	}

}
