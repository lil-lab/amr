package edu.uw.cs.lil.amr.data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.ner.RecognizedNamedEntity;

/**
 * AMR meta data. Carries various information in addition to sentence that is
 * used during inference.
 *
 * @author Yoav Artzi
 */
public class AMRMeta implements Serializable {
	public static final ILogger LOG = LoggerFactory.create(AMRMeta.class);

	private static final long serialVersionUID = -9135034613126260802L;

	/**
	 * For each token, a set of lemmas.
	 */
	private final List<Set<String>>				lemmas;
	private final Set<RecognizedNamedEntity>	namedEntities;
	private final Sentence						sentence;

	private final TokenSeq tags;

	public AMRMeta(Sentence sentence) {
		this.sentence = sentence;
		this.tags = AMRServices.tagSentence(sentence);
		this.namedEntities = AMRServices.getNamedEntities(sentence);
		this.lemmas = sentence.getTokens().toList().parallelStream()
				.map((token) -> Collections
						.unmodifiableSet(AMRServices.lemmatize(token)))
				.collect(Collectors.toList());
	}

	public Set<String> getLemmas(int index) {
		return lemmas.get(index);
	}

	public Set<String> getLemmas(String token) {
		final int numTokens = sentence.getTokens().size();
		for (int i = 0; i < numTokens; ++i) {
			if (token.equals(sentence.getTokens().get(i))) {
				return lemmas.get(i);
			}
		}
		throw new IllegalArgumentException(
				"Token not found in sentence: " + token);
	}

	public Set<RecognizedNamedEntity> getNamedEntities() {
		return namedEntities;
	}

	public TokenSeq getTags() {
		return tags;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		final TokenSeq tokens = sentence.getTokens();
		final int len = tokens.size();

		if (namedEntities != null) {
			for (final RecognizedNamedEntity namedEntity : namedEntities) {
				sb.append(namedEntity).append("\n");
			}
		}

		for (int i = 0; i < len; ++i) {
			sb.append(tokens.get(i)).append(": ");
			sb.append("pos=").append(tags.get(i));
			sb.append(" :: lemmas=").append(lemmas.get(i));
			if (i + 1 < len) {
				sb.append("\n");
			}
		}

		return sb.toString();
	}
}
