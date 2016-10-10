package edu.uw.cs.lil.amr.util.parseutil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.Lexicon;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.lex.SingleSentenceLex;
import edu.cornell.cs.nlp.spf.data.singlesentence.lex.SingleSentenceLexDataset;
import edu.cornell.cs.nlp.spf.data.situated.labeled.LabeledSituatedSentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.DistributedExperiment;
import edu.cornell.cs.nlp.spf.explat.resources.ResourceCreatorRepository;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.ccg.LogicalExpressionCategoryServices;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.ILogicalExpressionPrinter;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.LogicalExpressionToIndentedString;
import edu.cornell.cs.nlp.spf.parser.ccg.IWeightedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.CKYParserOutput;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IDataItemModel;
import edu.cornell.cs.nlp.spf.parser.ccg.model.Model;
import edu.cornell.cs.nlp.spf.parser.ccg.model.Model.Builder;
import edu.cornell.cs.nlp.spf.parser.joint.IJointDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.JointInferenceFilterUtils;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.composites.Triplet;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.exp.AmrResourceRepo;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.learn.filter.AMRSupervisedFilterFactory;
import edu.uw.cs.lil.amr.parser.AbstractAmrParser;

/**
 * Utility to parse an annotated sentence given a small lexicon and compute
 * various statistics (e.g., number of parses, oracle correctness, etc.).
 *
 * @author Yoav Artzi
 */
public class AMRTestParseUtil extends DistributedExperiment {

	private final ICategoryServices<LogicalExpression>	categoryServices;
	private int											numParsed					= 0;
	private int											numParsedSuccessfuly		= 0;
	private int											numParsedSuccessfulySloppy	= 0;

	private final ILogicalExpressionPrinter				prettyPrinter				= new LogicalExpressionToIndentedString.Printer(
			"  ");
	private final boolean								reportBad;
	private final boolean								verbose;

	@SuppressWarnings("unchecked")
	private AMRTestParseUtil(File expFile, Map<String, String> envParams,
			ResourceCreatorRepository creatorRepo, List<String> files,
			File modelFile, boolean dataWithLexicons, boolean supervisedPruning)
					throws Exception {
		super(expFile, envParams, creatorRepo);
		try {

			// //////////////////////////////////////////
			// Init logging.
			// //////////////////////////////////////////
			Logger.DEFAULT_LOG = new Log(System.out);
			Logger.setSkipPrefix(true);
			LogLevel.setLogLevel(
					LogLevel.valueOf(globalParams.get("log", "INFO")));
			this.verbose = globalParams.getAsBoolean("verbose", false);
			this.reportBad = globalParams.getAsBoolean("reportBadParses");

			// //////////////////////////////////////////////////
			// Init AMR.
			// //////////////////////////////////////////////////

			Init.init(globalParams.getAsFile("types"),
					globalParams.getAsFile("specmap"),
					globalParams.getAsFile("stanfordModel"), false,
					globalParams.contains("nerConfig")
							? globalParams.getAsFile("nerConfig") : null,
					globalParams.contains("nerTranslation")
							? globalParams.getAsFile("nerTranslation") : null,
					globalParams.contains("propBank")
							? globalParams.getAsFile("propBank") : null,
					globalParams.getAsBoolean("underspecifyPropBank", false));

			// //////////////////////////////////////////////////
			// Category services for logical expressions.
			// //////////////////////////////////////////////////

			this.categoryServices = new LogicalExpressionCategoryServices(true);
			storeResource(CATEGORY_SERVICES_RESOURCE, categoryServices);

			// //////////////////////////////////////////////////
			// Read resources.
			// //////////////////////////////////////////////////

			for (final Parameters params : resourceParams) {
				final String type = params.get("type");
				final String id = params.get("id");
				if (getCreator(type) == null) {
					throw new IllegalArgumentException(
							"Invalid resource type: " + type);
				} else {
					storeResource(id, getCreator(type).create(params, this));
				}
				LOG.info("Created resources %s of type %s", id, type);
			}

			// //////////////////////////////////////////////////
			// Parse each sentence in each dataset.
			// //////////////////////////////////////////////////

			final Builder<Sentence, LogicalExpression> modelBuilder = new Model.Builder<>();
			if (hasResource("lexicon")) {
				modelBuilder.setLexicon(
						(ILexicon<LogicalExpression>) get("lexicon"));
			}
			final AbstractAmrParser<?> parser = get(PARSER_RESOURCE);
			final IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model = modelFile == null
					? new JointModel.Builder<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>()
							.build()
					: JointModel.readJointModel(modelFile);

			final AMRSupervisedFilterFactory supervisedFilterFactory = supervisedPruning
					? new AMRSupervisedFilterFactory((c) -> true) : null;

			final List<Triplet<String, Integer, Integer>> fileCorrectCounts = new ArrayList<>(
					files.size());
			for (final String file : files) {
				LOG.info("==========================");
				LOG.info("File: %s", file);
				LOG.info("==========================");
				int counter = 0;
				if (dataWithLexicons) {
					final SingleSentenceLexDataset dataset = SingleSentenceLexDataset
							.read(new File(file), categoryServices, "seed",
									new Tokenizer());
					int correctCount = 0;
					for (final SingleSentenceLex dataItem : dataset) {
						final ILexicon<LogicalExpression> lexicon = new Lexicon<>(
								dataItem.getEntries().stream()
										.map((e) -> AMRServices
												.underspecifyAndStrip(e))
										.collect(Collectors.toSet()));
						if (processSentence(dataItem, lexicon, parser, model,
								globalParams.getAsBoolean("sloppy"),
								supervisedFilterFactory, ++counter,
								dataset.size())) {
							correctCount++;
						}
					}
					fileCorrectCounts.add(
							Triplet.of(file, dataset.size(), correctCount));

				} else {
					final SingleSentenceCollection dataset = SingleSentenceCollection
							.read(new File(file), new Tokenizer());
					int correctCount = 0;
					for (final SingleSentence dataItem : dataset) {
						if (processSentence(dataItem, null, parser, model,
								globalParams.getAsBoolean("sloppy"),
								supervisedFilterFactory, ++counter,
								dataset.size())) {
							correctCount++;
						}
					}
					fileCorrectCounts.add(
							Triplet.of(file, dataset.size(), correctCount));
				}
			}
			LOG.info("==========================");
			LOG.info("Summary");
			LOG.info("==========================");
			for (final Triplet<String, Integer, Integer> triplet : fileCorrectCounts) {
				LOG.info("%s :: %d/%d", triplet.first(), triplet.third(),
						triplet.second());
			}
			LOG.info(
					"Parsed %d sentences, %d successfully, %d sucessfully with word skipping",
					numParsed, numParsedSuccessfuly,
					numParsedSuccessfulySloppy);
		} catch (final Exception e) {
			end();
			throw e;
		}
	}

	public static void logParse(LogicalExpression label,
			IJointDerivation<LogicalExpression, LogicalExpression> derivation,
			boolean verbose, IDataItemModel<LogicalExpression> dataItemModel) {
		final boolean isGold = label != null
				&& label.equals(derivation.getResult());
		LOG.info("%s[v=%.2f] %s", isGold ? "* " : "  ",
				derivation.getViterbiScore(), derivation);
		if (verbose) {
			for (final IWeightedParseStep<LogicalExpression> step : derivation
					.getMaxSteps()) {
				LOG.info("\t%s",
						step.toString(false, false, dataItemModel.getTheta()));
			}
			LOG.info("Features: %s", dataItemModel.getTheta()
					.printValues(derivation.getMeanMaxFeatures()));
		}

	}

	public static void main(String[] args) throws ParseException {
		main(args, new AmrResourceRepo());
	}

	public static void main(String[] args,
			ResourceCreatorRepository creatorRepo) throws ParseException {
		final Options options = new Options();
		options.addOption("m", "model", true,
				"Stored joint model to use (default: create an empty model)");
		options.addOption("e", "explat-file", true,
				"Defintions file (required)");
		options.addOption("l", "samples-with-lexicons", false,
				"Input files include lexical entries (default: false)");
		options.addOption("p", "prune", false,
				"Use the correct logical form to prune inference (default: false)");
		final CommandLineParser parser = new GnuParser();
		final CommandLine cmd = parser.parse(options, args);

		if (!cmd.hasOption("explat-file") || cmd.getArgList().isEmpty()) {
			usage(options);
			return;
		}

		final String[] files = cmd.getArgs();

		final File expFile = new File(cmd.getOptionValue("explat-file"));

		// Set some mandatory properties.
		final HashMap<String, String> envParams = new HashMap<>();
		envParams.put("outputDir", ".");

		// Create the experiment and run it.
		try {
			final AMRTestParseUtil amrTestParseUtil = new AMRTestParseUtil(
					expFile, envParams, creatorRepo, Arrays.asList(files),
					cmd.hasOption("model")
							? new File(cmd.getOptionValue("model")) : null,
					cmd.hasOption("samples-with-lexicons"),
					cmd.hasOption("prune"));
			amrTestParseUtil.end();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void reportCorrectParses(
			List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> correctSloppyDerivations,
			LogicalExpression label,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel) {
		final Iterator<? extends IJointDerivation<LogicalExpression, LogicalExpression>> iterator = correctSloppyDerivations
				.iterator();
		LOG.info("------------------");
		while (iterator.hasNext()) {
			final IJointDerivation<LogicalExpression, LogicalExpression> derivation = iterator
					.next();

			logParse(label, derivation, true, dataItemModel);
		}
	}

	private static void usage(Options options) {
		// automatically generate the help statement.
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(AMRTestParseUtil.class.getSimpleName(), options);
	}

	private boolean processSentence(final SingleSentence baseDataItem,
			ILexicon<LogicalExpression> dataItemLexicon,
			AbstractAmrParser<?> parser,
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			boolean allowSloppy,
			AMRSupervisedFilterFactory suprvisedFilterFactory,
			int dataItemNumber, int numItems) {

		LOG.info("%d : ========================== [%d / %d]", dataItemNumber,
				dataItemNumber, numItems);

		// Create the AMR meta information for the sentence.
		final AMRMeta meta = new AMRMeta(baseDataItem.getSample());

		// Create the labeled situated data item.
		final LabeledSituatedSentence<AMRMeta, LogicalExpression> dataItem = new LabeledSituatedSentence<>(
				new SituatedSentence<>(baseDataItem.getSample(), meta),
				baseDataItem.getLabel(), baseDataItem.getProperties());

		LOG.info("%s", dataItem);
		if (dataItemLexicon != null) {
			LOG.info("Data item lexicon has %d entries",
					dataItemLexicon.size());
		}

		// Parsing counter.
		numParsed++;
		boolean parsedCorrectly = false;

		// Create data item model.
		final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
				.createJointDataItemModel(dataItem.getSample());

		// Create the filter.
		final IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> pruningFilter = suprvisedFilterFactory == null
				? JointInferenceFilterUtils.stubTrue()
				: suprvisedFilterFactory.createJointFilter(dataItem);

		// Parse sentence.
		final IJointOutput<LogicalExpression, LogicalExpression> output = parser
				.parse(dataItem.getSample(), dataItemModel, pruningFilter,
						false, dataItemLexicon);

		if (verbose
				&& output.getBaseParserOutput() instanceof CKYParserOutput) {
			LOG.info("------------------");
			LOG.info("Chart:");
			LOG.info(((CKYParserOutput<LogicalExpression>) output
					.getBaseParserOutput()).getChart());
			LOG.info("------------------");
		}

		if (suprvisedFilterFactory == null) {
			if (!output.isExact()) {
				LOG.warn("Inference is not exact.");
			}
		} else {
			LOG.info("Parse is pruned.");
		}

		LOG.info("Parse time: %fsec", output.getInferenceTime() / 1000.0);
		LOG.info("Generated %d derivations [%d max scoring]",
				output.getDerivations().size(),
				output.getMaxDerivations().size());

		// Log max-scoring parses.
		if (verbose || output.getMaxDerivations().size() == 1) {
			LOG.info("%d max-scoring derivations:",
					output.getMaxDerivations().size());
			for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : output
					.getMaxDerivations()) {
				logParse(dataItem.getLabel(), derivation, true, dataItemModel);
			}
		}

		// Log all derivations.
		if (verbose) {
			LOG.info("%d derivations:", output.getMaxDerivations().size());
			for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : output
					.getDerivations()) {
				logParse(dataItem.getLabel(), derivation, true, dataItemModel);
			}
		}

		final List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> correctDerivations = output
				.getDerivations(dataItem.getLabel());

		if (correctDerivations.isEmpty()) {
			LOG.info("No correct derivations");
		} else {
			LOG.info("Generated %d correct derivations",
					correctDerivations.size());
			reportCorrectParses(correctDerivations, dataItem.getLabel(),
					dataItemModel);
			numParsedSuccessfuly++;
			parsedCorrectly = true;
		}

		if (reportBad) {
			reportBadParse(output.getDerivations());
		}

		if (allowSloppy && correctDerivations.isEmpty()) {
			LOG.info("------------------");
			LOG.info("Sloppy parse: trying with word skipping");
			// Parse sentence.
			final IJointOutput<LogicalExpression, LogicalExpression> sloppyOutput = parser
					.parse(dataItem.getSample(), dataItemModel, pruningFilter,
							true, dataItemLexicon);

			if (verbose && sloppyOutput
					.getBaseParserOutput() instanceof CKYParserOutput) {
				LOG.info("------------------");
				LOG.info("Sloppy chart:");
				LOG.info(((CKYParserOutput<LogicalExpression>) sloppyOutput
						.getBaseParserOutput()).getChart());
				LOG.info("------------------");
			}

			if (suprvisedFilterFactory == null) {
				if (!sloppyOutput.isExact()) {
					LOG.warn("Inference is not exact.");
				}
			} else {
				LOG.info("Parse is pruned.");
			}

			LOG.info("Sloppy parse time: %fsec",
					sloppyOutput.getInferenceTime() / 1000.0);
			LOG.info("Generated %d sloppy derivations [%d max scoring]",
					sloppyOutput.getDerivations().size(),
					sloppyOutput.getMaxDerivations().size());

			// Log max-scoring parses.
			if (verbose || sloppyOutput.getMaxDerivations().size() == 1) {
				LOG.info("%d max-scoring sloppy  derivations:",
						sloppyOutput.getMaxDerivations().size());
				for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : sloppyOutput
						.getMaxDerivations()) {
					logParse(dataItem.getLabel(), derivation, true,
							dataItemModel);
				}
			}

			// Log all derivations.
			if (verbose) {
				LOG.info("%d sloppy derivations:",
						sloppyOutput.getMaxDerivations().size());
				for (final IJointDerivation<LogicalExpression, LogicalExpression> derivation : sloppyOutput
						.getDerivations()) {
					logParse(dataItem.getLabel(), derivation, true,
							dataItemModel);
				}
			}

			final List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> correctSloppyDerivations = sloppyOutput
					.getDerivations(dataItem.getLabel());

			if (correctSloppyDerivations.isEmpty()) {
				LOG.info("No sloppy correct parses");
			} else {
				LOG.info("Generated %d sloppy correct parses",
						correctSloppyDerivations.size());
				reportCorrectParses(correctSloppyDerivations,
						dataItem.getLabel(), dataItemModel);
				numParsedSuccessfulySloppy++;
				parsedCorrectly = true;
			}

			if (reportBad) {
				reportBadParse(sloppyOutput.getDerivations());
			}

		}

		return parsedCorrectly;
	}

	private void reportBadParse(
			List<? extends IJointDerivation<LogicalExpression, LogicalExpression>> list) {

		final Iterator<? extends IJointDerivation<LogicalExpression, LogicalExpression>> iterator = list
				.iterator();
		while (iterator.hasNext()) {
			LOG.info("Bad parse logical form:");
			LOG.info(prettyPrinter.toString(iterator.next().getResult()));
			if (iterator.hasNext()) {
				LOG.info("------------------");
			}
		}
	}

}
