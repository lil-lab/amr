package edu.uw.cs.lil.amr.ner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jregex.MatchIterator;
import jregex.MatchResult;
import jregex.Pattern;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.NETagPlain;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.Parameters;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * @author Yoav Artzi
 */
public class IllinoisNERWrapper {

	private static final Pattern					TAGGING_PATTERN	= new Pattern(
																			"\\[({tag}[A-Z]+) ({text}.+?)\\]");
	private final Map<String, Set<LogicalConstant>>	translationTable;

	public IllinoisNERWrapper(File configFile, File translationFile)
			throws Exception {
		Parameters.readConfigAndLoadExternalData(configFile.getAbsolutePath(),
				false);
		this.translationTable = readTranslationFile(translationFile);
	}

	private static Map<String, Set<LogicalConstant>> readTranslationFile(
			File translationFile) throws FileNotFoundException, IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(
				translationFile))) {
			String line;
			final Map<String, Set<LogicalConstant>> table = new HashMap<>();
			while ((line = reader.readLine()) != null) {
				final String[] split = line.split("\t");
				final String rawTag = split[0];
				final Set<LogicalConstant> constants = new HashSet<>();
				final int len = split.length;
				for (int i = 1; i < len; ++i) {
					constants.add(LogicalConstant.read(split[i]));
				}
				table.put(rawTag, Collections.unmodifiableSet(constants));
			}
			return table;
		}
	}

	public Set<RecognizedNamedEntity> getNamedEntities(TokenSeq tokens) {
		try {
			final String tagged = NETagPlain.tagLine(tokens.toString(" "));

			// Extract the recognized named entities using a regular expression.
			final MatchIterator iterator = TAGGING_PATTERN.matcher(tagged)
					.findAll();
			final Set<RecognizedNamedEntity> entities = new HashSet<>();
			while (iterator.hasMore()) {
				final MatchResult next = iterator.nextMatch();
				final String rawTag = next.group("tag");
				final TokenSeq text = TokenSeq
						.of(next.group("text").split(" "));
				final LogicalConstant textConstant = AMRServices
						.createTextConstant(text);
				// Get the translated type.
				entities.add(new RecognizedNamedEntity(textConstant,
						translationTable.get(rawTag), rawTag, text));
			}

			return entities;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
