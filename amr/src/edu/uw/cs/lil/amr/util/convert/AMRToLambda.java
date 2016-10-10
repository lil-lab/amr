package edu.uw.cs.lil.amr.util.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jregex.Pattern;
import jregex.Replacer;
import edu.cornell.cs.nlp.spf.base.properties.Properties;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.data.sentence.ITokenizer;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.ccg.LogicalExpressionCategoryServices;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.LogicalExpressionToIndentedString;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.LabeledAmrSentenceCollection;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.jamr.alignment.AlignmentServices;
import edu.uw.cs.lil.amr.jamr.alignment.GetAlignedSubExp;
import edu.uw.cs.lil.amr.lambda.convert.AmrToLogicalExpressionConverter;

/**
 * Utility to convert sentences paired with AMRs to
 * {@link SingleSentenceCollection} and write to a file. The input AMR file must be
 * simplified where each AMR is on a single line (and not indented on multiple
 * lines). See the script txt2amr.py to simplify AMR files.
 *
 * @author Yoav Artzi
 */
public class AMRToLambda {
	public static final ILogger		LOG						= LoggerFactory
																	.create(AMRToLambda.class);

	private static final Pattern	WHITE_SPACE				= new Pattern(
																	"\\s+");
	private static Replacer			WHITE_SPACE_REPLACER	= new Replacer(
																	WHITE_SPACE,
																	" ");

	public static void main(String[] args) throws IOException {

		// //////////////////////////////////////////
		// Init logging
		// //////////////////////////////////////////
		Logger.DEFAULT_LOG = new Log(System.err);
		Logger.setSkipPrefix(true);
		LogLevel.INFO.set();

		// //////////////////////////////////////////
		// Init AMR.
		// //////////////////////////////////////////

		Init.init(/* "../resources/amr.types" */new File(args[0]), true);

		// //////////////////////////////////////////////////
		// Category services for logical expressions.
		// //////////////////////////////////////////////////

		final LogicalExpressionCategoryServices categoryServices = new LogicalExpressionCategoryServices(
				true);

		// //////////////////////////////////////////
		// Init AMR converter.
		// //////////////////////////////////////////

		final AmrToLogicalExpressionConverter converter = new AmrToLogicalExpressionConverter();

		// //////////////////////////////////////////
		// Read the input file, convert each sample and write to output.
		// //////////////////////////////////////////

		final ITokenizer tokenizer = new Tokenizer();
		int lineNumber = -1;
		try (BufferedReader reader = new BufferedReader(new FileReader(args[1]));
				PrintStream out = new PrintStream(new File(args[2]));) {
			String line;
			int counter = 0;
			while ((line = reader.readLine()) != null) {
				++lineNumber;
				if (line.trim().isEmpty()) {
					continue;
				}
				++counter;
				final String sentence = line;
				LOG.info("Processing: %s", sentence);
				final Map<String, String> properties = Properties
						.readProperties(reader.readLine());
				++lineNumber;
				// Read the AMR, potentially multiple lines. Read until an empty
				// line.
				final StringBuilder amr = new StringBuilder();
				while ((line = reader.readLine()) != null
						&& line.trim().length() > 0) {
					amr.append(WHITE_SPACE_REPLACER.replace(line));
					++lineNumber;
				}

				final String amrString = amr.toString();
				final LogicalExpression exp = converter.read(amrString);

				final TokenSeq tokenized = tokenizer.tokenize(sentence);
				if (properties.containsKey("alignments")) {
					if (properties.containsKey("tok")) {
						properties.put(
								"alignments",
								adjustAlignments(tokenized, TokenSeq
										.of(properties.get("tok").split(" ")),
										properties.get("alignments").trim(),
										tokenizer));

						final Map<Pair<Integer, Integer>, Set<LogicalExpression>> alignmentMap = new HashMap<>();
						for (final String alignmentString : properties.get(
								"alignments").split(" +")) {
							if (alignmentString.length() > 0) {
								final Pair<Pair<Integer, Integer>, String> alignmentPair = AlignmentServices
										.readAlignment(alignmentString);
								final LogicalExpression subExp = GetAlignedSubExp
										.of(exp, alignmentPair.second());
								if (subExp == null) {
									LOG.info(
											"Failed to recover sub-expression for alignment: %s",
											alignmentString);
								} else {
									LOG.debug("Alignment: %s -> %s", tokenized
											.sub(alignmentPair.first().first(),
													alignmentPair.first()
															.second()), subExp);
									if (!alignmentMap.containsKey(alignmentPair
											.first())) {
										alignmentMap.put(alignmentPair.first(),
												new HashSet<>());
									}
									alignmentMap.get(alignmentPair.first())
											.add(subExp);
								}
							}
						}

						// Create the string representation and store in the
						// properties.
						if (!alignmentMap.isEmpty()) {
							properties
									.put(AlignmentServices.STORED_ALIGNMENTS_PROPERTY,
											AlignmentServices
													.alignmentsToString(alignmentMap));
						}
					} else {
						LOG.error("Alignments present, but tokenized sentence not -- can't adjust alignments");
					}
				}

				properties.put("amr", amrString);
				properties.put("ptbtok", tokenized.toString());

				out.println(sentence.trim().isEmpty() ? "EMPTY_SENTENCE"
						: sentence);
				out.println(Properties.toString(properties));
				out.println(LogicalExpressionToIndentedString.of(exp));
				out.println();
				if (counter % 100 == 0) {
					LOG.info("Processed %d ...", counter);
				}
			}

			// Read the file for verification.
			LOG.info("Reading output file for testing...");
			final LabeledAmrSentenceCollection testRead = new LabeledAmrSentenceCollection(
					SingleSentenceCollection.read(new File(args[2]), tokenizer),
					null, categoryServices, null);
			LOG.info("Done (#sample=%d)", testRead.size());
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		} catch (final Exception e) {
			throw new RuntimeException("Exception in line: " + lineNumber, e);
		}

	}

	private static String adjustAlignments(TokenSeq ptbTokens,
			TokenSeq originalTokens, String alignments, ITokenizer tokenizer) {
		if (alignments.length() == 0) {
			return alignments;
		}

		final String[] split = alignments.split(" +");
		final List<String> newAlignmentStrings = new ArrayList<>(split.length);
		for (final String alignment : split) {
			final Pair<Pair<Integer, Integer>, String> alignmentPair = AlignmentServices
					.readAlignment(alignment);
			final int originalStart = alignmentPair.first().first();
			final int originalEnd = alignmentPair.first().second();
			// First, try to re-tokenize using the tokenizer and identify the
			// span of tokens in the given ptbTokens. If this fails, we try a
			// more aggressive approach. TODO
			final TokenSeq convertedTokens = tokenizer.tokenize(originalTokens
					.sub(originalStart, originalEnd).toString(" "));
			int newStart = -1;
			int newEnd = -1;
			for (int start = 0; start < ptbTokens.size(); ++start) {
				for (int end = start + 1; end <= ptbTokens.size(); ++end) {
					if (ptbTokens.sub(start, end).equals(convertedTokens)) {
						newStart = start;
						newEnd = end;
						break;
					}
				}
				if (newStart >= 0) {
					break;
				}
			}

			if (newStart >= 0) {
				newAlignmentStrings.add(String.format("%d-%d|%s", newStart,
						newEnd, alignmentPair.second()));
			} else if (originalEnd < ptbTokens.size()
					&& ptbTokens.get(originalEnd - 1).equals("-")) {
				LOG.error(
						"Failed to adjust alignment due to dash for \"%s\" (%s) in: %s",
						originalTokens.sub(originalStart, originalEnd),
						alignment, ptbTokens);
			} else {
				LOG.error("Failed to adjust alignment for \"%s\" (%s) in: %s",
						originalTokens.sub(originalStart, originalEnd),
						alignment, ptbTokens);
			}

		}
		return newAlignmentStrings.stream().collect(Collectors.joining(" "));
	}
}
