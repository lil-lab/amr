package edu.uw.cs.lil.amr.util.dataprep;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.io.Files;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.lex.SingleSentenceLex;
import edu.cornell.cs.nlp.spf.data.singlesentence.lex.SingleSentenceLexDataset;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.ccg.LogicalExpressionCategoryServices;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;

/**
 * Given a directory with annotated seed examples, refresh each seed example, if
 * changed.
 *
 * @author Yoav Artzi
 */
public class RefreshSeed {
	public static final ILogger	LOG	= LoggerFactory.create(RefreshSeed.class);

	public static void main(String[] args) throws IOException, ParseException {
		final Options options = new Options();
		options.addOption("t", "types-file", true, "AMR .types file.");
		options.addOption("s", "seed-dir", true,
				"Directory containing seed .lamlex files");
		final CommandLineParser parser = new GnuParser();
		final CommandLine cmd = parser.parse(options, args);

		if (!cmd.hasOption("types-file") || !cmd.hasOption("seed-dir")
				|| cmd.getArgList().isEmpty()) {
			usage(options);
			return;
		}

		// //////////////////////////////////////////
		// Init logging
		// //////////////////////////////////////////

		Logger.DEFAULT_LOG = new Log(System.err);
		Logger.setSkipPrefix(true);
		LogLevel.INFO.set();

		// //////////////////////////////////////////
		// Init AMR.
		// //////////////////////////////////////////

		Init.init(new File(cmd.getOptionValue("types-file")), true);

		// //////////////////////////////////////////////////
		// Category services for logical expressions.
		// //////////////////////////////////////////////////

		final ICategoryServices<LogicalExpression> categoryServices = new LogicalExpressionCategoryServices(
				true);

		// //////////////////////////////////////////
		// Read current seed.
		// //////////////////////////////////////////

		// Files to create old backup for.
		final Set<File> backupFlags = new HashSet<>();

		// Mapping of ID to existing data item and filename.
		final Map<String, Pair<SingleSentenceLex, File>> seedMapping = new HashMap<>();
		for (final File file : new File(cmd.getOptionValue("seed-dir"))
				.listFiles()) {
			if (file.getName().endsWith("lamlex")) {
				for (final SingleSentenceLex dataItem : SingleSentenceLexDataset
						.read(file, categoryServices, "seed", new Tokenizer())) {
					final String id = dataItem.getProperties().get("id");
					if (seedMapping.containsKey(id)) {
						LOG.warn("Duplicate ID (%s in file: %s", id, file);
						backupFlags.add(file);
					} else {
						seedMapping.put(id, Pair.of(dataItem, file));
					}
				}
			} else {
				LOG.info("Seed, skipping: %s", file);
			}
		}

		// //////////////////////////////////////////
		// Read the data and write the new seed files.
		// //////////////////////////////////////////

		// Mapping of file name to new seed entries.
		final Map<File, List<SingleSentenceLex>> newSeeds = new HashMap<>();
		for (final String fileName : cmd.getArgs()) {
			for (final SingleSentence dataItem : SingleSentenceCollection.read(
					new File(fileName), new Tokenizer())) {
				final String id = dataItem.getProperties().get("id");
				if (seedMapping.containsKey(id)) {
					final SingleSentenceLex oldDataItem = seedMapping.get(id)
							.first();
					final File targetFile = seedMapping.get(id).second();
					final SingleSentenceLex newDataItem;
					if (!dataItem.getLabel().equals(oldDataItem.getLabel())
							|| !dataItem
									.getSample()
									.getSample()
									.equals(oldDataItem.getSample().getSample())) {
						LOG.warn(
								"Data item and/or labeled LF changed, created backup (id=%s): %s",
								id, targetFile);
						backupFlags.add(targetFile);
					}

					newDataItem = new SingleSentenceLex(dataItem.getSample(),
							dataItem.getLabel(), oldDataItem.getEntries(),
							dataItem.getProperties());
					if (!newSeeds.containsKey(targetFile)) {
						newSeeds.put(targetFile, new LinkedList<>());
					}
					newSeeds.get(targetFile).add(newDataItem);
				}
			}
		}

		// //////////////////////////////////////////
		// Write the new seed files, backup as necessary.
		// //////////////////////////////////////////

		for (final Entry<File, List<SingleSentenceLex>> seedEntry : newSeeds
				.entrySet()) {
			final File outputFile = seedEntry.getKey();
			if (backupFlags.contains(seedEntry.getKey())) {
				Files.copy(outputFile, new File(seedEntry.getKey() + ".old"));
			}
			try (PrintStream out = new PrintStream(outputFile)) {
				for (final SingleSentenceLex dataItem : seedEntry.getValue()) {
					out.println(dataItem);
					out.println();
				}
			}
		}
		LOG.info("Done, processed %d files", newSeeds.size());

	}

	private static void usage(Options options) {
		// automatically generate the help statement.
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(RefreshSeed.class.getSimpleName(), options);
	}

}
