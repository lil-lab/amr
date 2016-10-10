package edu.uw.cs.lil.amr.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.exec.IExec;
import edu.cornell.cs.nlp.spf.exec.IExecOutput;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointModelImmutable;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.parser.AbstractAmrParser;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * {@link IExec} wrapper for {@link GraphAmrParser}.
 *
 * @author Yoav Artzi
 */
public class Exec
		implements IExec<SituatedSentence<AMRMeta>, LogicalExpression> {

	public static final ILogger																								LOG					= LoggerFactory
			.create(Exec.class);
	private static final long																								serialVersionUID	= -6363288221192489722L;
	private final boolean																									breakTies;
	private final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>	filterFactory;
	private final IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>						model;

	private final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>								outputLogger;

	private final AbstractAmrParser<?>																						parser;
	private final Map<String, Set<String>>																					vocabularyMapping;

	public Exec(AbstractAmrParser<?> parser,
			IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> outputLogger,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			Map<String, Set<String>> vocabularyMapping, boolean breakTies) {
		this.parser = parser;
		this.model = model;
		this.outputLogger = outputLogger;
		this.filterFactory = filterFactory;
		this.vocabularyMapping = vocabularyMapping;
		this.breakTies = breakTies;
	}

	/**
	 * Execute conditioned on the labeled logical form.
	 */
	public IExecOutput<LogicalExpression> conditionedExec(
			LabeledAmrSentence dataItem) {
		if (filterFactory == null) {
			throw new IllegalStateException(
					"Can't provide conditioned execution without a filter factory");
		}
		final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
				.createJointDataItemModel(dataItem.getSample());
		final IJointOutput<LogicalExpression, LogicalExpression> output = parser
				.parse(dataItem.getSample(), dataItemModel,
						filterFactory.createJointFilter(dataItem));
		if (outputLogger != null) {
			outputLogger.log(output, dataItemModel,
					"exec" + "-" + System.currentTimeMillis());
		}

		return new ExecOutput(output, dataItemModel, breakTies);
	}

	@Override
	public IExecOutput<LogicalExpression> execute(
			SituatedSentence<AMRMeta> dataItem) {
		return execute(dataItem, false);
	}

	@Override
	public IExecOutput<LogicalExpression> execute(
			SituatedSentence<AMRMeta> dataItem, boolean sloppy) {
		final IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel = model
				.createJointDataItemModel(dataItem);

		// If doing sloppy inference, create the special sloppy lexicon that
		// will hypothesize new entries based on the current lexicon.
		final SloppyLexicon sloppyLexicon;
		if (sloppy) {
			sloppyLexicon = SloppyLexicon.create(dataItem, model,
					vocabularyMapping);
			LOG.info("Created a sloppy lexicon with %d entries",
					sloppyLexicon.size());
		} else {
			sloppyLexicon = null;
		}

		final IJointOutput<LogicalExpression, LogicalExpression> output = parser
				.parse(dataItem, dataItemModel, sloppy, sloppyLexicon);
		if (outputLogger != null) {
			outputLogger.log(output, dataItemModel,
					"exec" + (sloppy ? "-sloppy" : "") + "-"
							+ System.currentTimeMillis());
		}

		// Logging in the case of multiple max-scoring derivations.
		if (output.getMaxDerivations().size() > 1) {
			LOG.info(
					"Generated multiple max-scoring derivations (logging details in comparison to first one)");
			LOG.info("%s",
					sloppy ? "Sloppy inference" : "Conventional inference");
			LOG.info("Max-scoring derivations [%d]:",
					output.getMaxDerivations().size());
			final Iterator<? extends IJointDerivation<LogicalExpression, LogicalExpression>> iterator = output
					.getMaxDerivations().iterator();
			IJointDerivation<LogicalExpression, LogicalExpression> first = null;
			while (iterator.hasNext()) {
				final IJointDerivation<LogicalExpression, LogicalExpression> derivation = iterator
						.next();
				LOG.info(derivation);
				LOG.info("Features: %s", model.getTheta()
						.printValues(derivation.getMeanMaxFeatures()));
				if (first == null) {
					first = derivation;
				} else {
					final IHashVector delta = HashVectorFactory.create();
					derivation.getMeanMaxFeatures().addTimesInto(1.0, delta);
					first.getMeanMaxFeatures().addTimesInto(-1.0, delta);
					delta.dropNoise();
					delta.dropZeros();
					LOG.info("Delta with first: %s",
							model.getTheta().printValues(delta));
				}
				LOG.info("--------------------------------------");
			}
			LOG.info("Logged %d derivations",
					output.getMaxDerivations().size());
			LOG.info("--------------------------------------");
			LOG.info("--------------------------------------");
		}

		return new ExecOutput(output, dataItemModel, breakTies);
	}

	public IJointModelImmutable<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> getModel() {
		return model;
	}

	public boolean supportsConditionedInference() {
		return filterFactory != null;
	}

	public static class Creator implements IResourceObjectCreator<Exec> {

		private final String type;

		public Creator() {
			this("exec.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public Exec create(Parameters params, IResourceRepository repo) {
			final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> logger;
			if (params.contains("logger")) {
				logger = repo.get(params.get("logger"));
			} else {
				logger = null;
			}

			final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory;
			if (params.contains("filterFactory")) {
				filterFactory = repo.get(params.get("filterFactory"));
			} else {
				filterFactory = null;
			}

			final Map<String, Set<String>> vocabularyMapping;
			if (params.contains("vocab")) {
				vocabularyMapping = new HashMap<>();
				try (BufferedReader reader = new BufferedReader(
						new FileReader(params.getAsFile("vocab")))) {
					String line;
					while ((line = reader.readLine()) != null) {
						final String[] split = line.split("\t");
						vocabularyMapping.put(split[0], SetUtils.createSet(
								Arrays.copyOfRange(split, 1, split.length)));
					}
				} catch (final IOException e) {
					LOG.error("Failed to read vocabulary file: %s",
							params.get("vocab"));
					throw new RuntimeException(e);
				}
			} else {
				vocabularyMapping = Collections.emptyMap();
			}

			return new Exec(repo.get(params.get("parser")),
					repo.get(params.get("model")), logger, filterFactory,
					Collections.unmodifiableMap(vocabularyMapping),
					params.getAsBoolean("breakTies", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, Exec.class)
					.setDescription(
							"Execution wrapper for AMR parser and a given model")
					.addParam("breakTies", Boolean.class,
							"Break ties between max-scoring derivations by taking the first one (default: false)")
					.addParam("vocab", File.class,
							"Vocabulary file for the sloppy lexicon (default: no vocabulary, use only case modificaition and lemmatizer)")
					.addParam("filterFactory",
							IJointInferenceFilterFactory.class,
							"To allow for conditioned inference a filter factory must be provided (default: null)")
					.addParam("parser", AbstractAmrParser.class,
							"AMR parser to use")
					.addParam("logger", IJointOutputLogger.class,
							"Output logger (default: none)")
					.addParam("model", IJointModelImmutable.class,
							"Joint model to use")
					.build();
		}

	}

}
