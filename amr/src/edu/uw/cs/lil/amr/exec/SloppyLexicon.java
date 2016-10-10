package edu.uw.cs.lil.amr.exec;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.collections.iterators.ImmutableIterator;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class SloppyLexicon implements ILexiconImmutable<LogicalExpression> {

	public static final String											ENTRY_MARKER		= "SLOPPY_LEXICAL_ENTRY";
	public static final ILogger											LOG					= LoggerFactory
																									.create(SloppyLexicon.class);
	private static final long											serialVersionUID	= 423263904555574572L;

	private final Map<TokenSeq, Set<LexicalEntry<LogicalExpression>>>	entryMapping;

	private SloppyLexicon(
			Map<TokenSeq, Set<LexicalEntry<LogicalExpression>>> entryMapping) {
		this.entryMapping = entryMapping;
	}

	public static SloppyLexicon create(
			SituatedSentence<AMRMeta> dataItem,
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			Map<String, Set<String>> vocabularyMapping) {
		final Map<TokenSeq, Set<LexicalEntry<LogicalExpression>>> entryMapping = new HashMap<>();
		final TokenSeq tokens = dataItem.getSample().getTokens();
		final int len = tokens.size();
		for (int i = 0; i < len; ++i) {
			final TokenSeq token = tokens.sub(i, i + 1);

			final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
			entryMapping.put(token, entries);

			// Check if we have entries for this token.
			if (model.getLexicon().get(token).hasNext()) {
				// Case the model has entries for this token, skip it.
				continue;
			}

			// Generalize the token.
			final Set<TokenSeq> relatedTokens = generalizeToken(token,
					vocabularyMapping);

			for (final TokenSeq relatedToken : relatedTokens) {
				final Iterator<? extends LexicalEntry<LogicalExpression>> iterator = model
						.getLexicon().get(relatedToken);
				while (iterator.hasNext()) {
					entries.add(cloneEntry(iterator.next()));
				}
			}

			// Break if we found entries.
			if (!entries.isEmpty()) {
				LOG.info(
						"Identified %d related entries via case and form modifications for the word: %s",
						entries.size(), token);
				continue;
			}

			// Try to find the lemmatized form in the lexicon for each of the
			// related words.
			for (final TokenSeq relatedToken : relatedTokens) {
				final Set<String> lemmas = AMRServices.lemmatize(relatedToken
						.toString());
				for (final String lemma : lemmas) {
					final Iterator<? extends LexicalEntry<LogicalExpression>> lemmaIterator = model
							.getLexicon().get(TokenSeq.of(lemma));
					while (lemmaIterator.hasNext()) {
						entries.add(cloneEntry(lemmaIterator.next()));
					}
				}
			}

			// Break if we found entries.
			if (!entries.isEmpty()) {
				LOG.info(
						"Identified %d related entries via lemmatization for the word: %s",
						entries.size(), token);
				continue;
			}

			// TODO May want to consider in the future to generate entries by
			// identifying a related PropBank frame or lemmatizing to create new
			// constants.
		}

		return new SloppyLexicon(entryMapping);
	}

	private static LexicalEntry<LogicalExpression> cloneEntry(
			LexicalEntry<LogicalExpression> entry) {
		final Map<String, String> properties = new HashMap<>(
				entry.getProperties());
		properties.put(ENTRY_MARKER, Boolean.TRUE.toString());
		return entry.cloneWithProperties(properties);
	}

	/**
	 * Generalize the token by modifying its case and doing some simple
	 * manipulations (verb and plurality inflection).
	 *
	 * @param token
	 * @return
	 */
	private static Set<TokenSeq> generalizeToken(TokenSeq token,
			Map<String, Set<String>> vocabularyMapping) {
		final String tokenString = token.toString();

		final Set<TokenSeq> tokens = new HashSet<>();
		tokens.add(token);

		// Lower-case the entire token.
		tokens.add(token.toLowerCase());

		// Change the case of the first character.
		tokens.add(TokenSeq.of((Character.isUpperCase(tokenString.charAt(0)) ? tokenString
				.substring(0, 1).toLowerCase() : tokenString.substring(0, 1)
				.toUpperCase())
				+ tokenString.substring(1)));

		// Find related words from the provided extended vocabulary.
		final Set<String> related = vocabularyMapping.get(tokenString
				.toLowerCase().toString());
		if (related != null) {
			// All related tokens as is.
			tokens.addAll(related.stream().map(s -> TokenSeq.of(s))
					.collect(Collectors.toSet()));
			// Upper-case the first character for each related token.
			tokens.addAll(related
					.stream()
					.map(s -> TokenSeq.of(s.substring(0, 1).toUpperCase()
							+ s.substring(1))).collect(Collectors.toSet()));

		}

		return tokens;
	}

	@Override
	public boolean contains(LexicalEntry<LogicalExpression> lex) {
		// An entry is contained only if it's present in its newly generated
		// form.
		if (!entryMapping.containsKey(lex.getTokens())) {
			return false;
		}

		return entryMapping.get(lex.getTokens()).stream()
				.anyMatch(e -> e.getCategory().equals(lex.getCategory()));
	}

	@Override
	public Iterator<? extends LexicalEntry<LogicalExpression>> get(
			TokenSeq tokens) {
		if (entryMapping.containsKey(tokens)) {
			return ImmutableIterator.of(entryMapping.get(tokens).iterator());
		} else {
			return Collections.emptyIterator();
		}
	}

	@Override
	public int size() {
		return entryMapping.values().stream()
				.collect(Collectors.summingInt(s -> s.size()));
	}

	@Override
	public Collection<LexicalEntry<LogicalExpression>> toCollection() {
		final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
		for (final Entry<TokenSeq, Set<LexicalEntry<LogicalExpression>>> tokenSet : entryMapping
				.entrySet()) {
			for (final LexicalEntry<LogicalExpression> entry : tokenSet
					.getValue()) {
				entries.add(new LexicalEntry<>(tokenSet.getKey(), entry
						.getCategory(), entry.isDynamic(), entry
						.getProperties()));
			}
		}
		return entries;
	}
}
