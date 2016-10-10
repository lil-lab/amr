package edu.uw.cs.lil.amr.learn.gradient;

import java.util.List;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.situated.labeled.LabeledSituatedSentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.parser.AbstractAmrParserOutput;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.GraphAmrParserOutput;

/**
 * A gradient checking procedure.
 *
 *
 * @author Yoav Artzi
 *
 */
public class GradientChecker {

	public static final ILogger																								LOG	= LoggerFactory
			.create(GradientChecker.class);

	private final IDataCollection<LabeledAmrSentence>																		data;
	private final double																									epsilon;
	private final IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression>	filterFactory;
	private final IGradientFunction																							gradientFunction;
	private final IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression>								outputLogger;
	private final GraphAmrParser																							parser;

	public GradientChecker(GraphAmrParser parser,
			IDataCollection<LabeledAmrSentence> data,
			IGradientFunction gradientFunction,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			double epsilon,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> outputLogger) {
		this.parser = parser;
		this.data = data;
		this.gradientFunction = gradientFunction;
		this.filterFactory = filterFactory;
		this.epsilon = epsilon;
		this.outputLogger = outputLogger;
	}

	/**
	 * Verifies the correct derivation was generated.
	 */
	private static boolean verifyCorrectPresent(
			AbstractAmrParserOutput<GraphAmrDerivation, IGraphDerivation<LogicalExpression>> output,
			LabeledSituatedSentence<AMRMeta, LogicalExpression> dataItem) {
		// Verify the correct is reachable (and not as a derivation
		// generated on the fly).
		final List<GraphAmrDerivation> originalDerivations = output
				.getDerivations();
		for (final GraphAmrDerivation derivation : originalDerivations) {
			if (dataItem.getLabel().equals(derivation.getResult())) {
				return true;
			}
		}
		return false;
	}

	public void check(
			JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model) {

		int itemCounter = -1;
		for (final LabeledAmrSentence dataItem : data) {
			// Record sample processing start time.
			final long startTime = System.currentTimeMillis();

			// Log sample header.
			LOG.info("%d : ==================", ++itemCounter);
			LOG.info("Sample type: %s", dataItem.getClass().getSimpleName());
			LOG.info("%s", dataItem);

			// TODO DEBUG
			if (itemCounter != 15) {
				continue;
			}

			final IJointDataItemModel<LogicalExpression, LogicalExpression> dim = model
					.createJointDataItemModel(dataItem.getSample());

			// Do inference with current weights.
			final GraphAmrParserOutput originalOutput = parser
					.parse(dataItem.getSample(), dim);

			if (!originalOutput.isExact()) {
				LOG.info(
						"Output is not exact -- can't check gradient due unstable pruning");
				continue;
			}

			if (!verifyCorrectPresent(originalOutput, dataItem)) {
				LOG.info("Derivation not found in original output -- skipping");
				continue;
			}

			final List<GraphAmrDerivation> correctDerivations = originalOutput
					.getDerivations(dataItem.getLabel());
			if (correctDerivations.size() != 1) {
				LOG.info("Unexpected %d correct derivations -- skipping",
						correctDerivations.size());
				continue;
			}
			final double functionValue = Math
					.exp(correctDerivations.get(0).getLogInsideScore()
							- originalOutput.logNorm());

			// Compute the gradient.
			final GradientComputation gradientComputation = gradientFunction.of(
					dataItem, dim, itemCounter, 0, parser, filterFactory,
					outputLogger);
			if (gradientComputation.isPartial()
					|| !gradientComputation.isExact()) {
				LOG.info(
						"Gradient is partial or inexact -- can't verify the gradient -- skipping");
				continue;
			}
			final IHashVector gradient = gradientComputation.getGradient();
			if (gradient == null) {
				LOG.info("No gradient generated -- skipping");
				continue;
			}
			gradient.dropZeros();

			LOG.info("Starting gradient check for %d features",
					gradient.size());

			// TODO TEMP only iterate over features that are in the base
			// derivation

			for (final Pair<KeyArgs, Double> keyValue : gradient) {
				final KeyArgs key = keyValue.first();
				final double gradientValue = keyValue.second();
				final double originalWeight = model.getTheta().get(key);

				// Compute function value for weight + epsilon.
				model.getTheta().set(key, originalWeight + epsilon);
				final GraphAmrParserOutput plusOutput = parser
						.parse(dataItem.getSample(), dim);
				if (!plusOutput.isExact()) {
					LOG.info("Plus-epsilon output is not exact -- skipping");
					continue;
				}
				if (!verifyCorrectPresent(plusOutput, dataItem)) {
					LOG.info(
							"Failed to recover correct plus derivation when validating %s -- skipping",
							key);
					continue;
				}
				final List<GraphAmrDerivation> plusCorrectDerivations = plusOutput
						.getDerivations(dataItem.getLabel());
				if (plusCorrectDerivations.size() != 1) {
					LOG.info("Unexpected %d correct derivations -- skipping",
							plusCorrectDerivations.size());
					continue;
				}
				final double plusFunctionValue = Math
						.exp(plusCorrectDerivations.get(0).getLogInsideScore()
								- plusOutput.logNorm());
				LOG.info("Plus-epsilon function value: %f --> %f",
						functionValue, plusFunctionValue);

				// Computer function value for weight - epsilon.
				model.getTheta().set(key, originalWeight - epsilon);
				final GraphAmrParserOutput minusOutput = parser
						.parse(dataItem.getSample(), dim);
				if (!minusOutput.isExact()) {
					LOG.info("Minus-epsilon output is not exact -- skipping");
					continue;
				}
				if (!verifyCorrectPresent(minusOutput, dataItem)) {
					LOG.info(
							"Failed to recover correct minus derivation when validating %s -- skipping",
							key);
					continue;
				}
				final List<GraphAmrDerivation> minusCorrectDerivations = minusOutput
						.getDerivations(dataItem.getLabel());
				if (minusCorrectDerivations.size() != 1) {
					LOG.info("Unexpected %d correct derivations -- skipping",
							minusCorrectDerivations.size());
					continue;
				}
				final double minusFunctionValue = Math
						.exp(minusCorrectDerivations.get(0).getLogInsideScore()
								- minusOutput.logNorm());
				LOG.info("Minus-epsilon function value: %f --> %f",
						functionValue, minusFunctionValue);

				// Verify the gradient value.
				// TODO epsilon should be multiplied by two!
				final double estimateGradientValue = (plusFunctionValue
						- minusFunctionValue) / (2 * epsilon);
				LOG.info(
						"Gradient check for %s: gradient=%.4f, estimated=%.4f, delta=%.4f, error=%.4f",
						key, gradientValue, estimateGradientValue,
						gradientValue - estimateGradientValue,
						Math.abs(gradientValue - estimateGradientValue)
								/ Math.max(Math.abs(gradientValue),
										Math.abs(estimateGradientValue)));

				// Restore original weight.
				model.getTheta().set(key, originalWeight);
			}

			LOG.info("Sample processing time: %.2f",
					(System.currentTimeMillis() - startTime) / 1000.0);
		}

	}

	public static class Creator
			implements IResourceObjectCreator<GradientChecker> {

		private final String type;

		public Creator() {
			this("gradient.checker");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public GradientChecker create(Parameters params,
				IResourceRepository repo) {
			return new GradientChecker(repo.get(params.get("parser")),
					repo.get(params.get("data")),
					repo.get(params.get("gradient")),
					repo.get(params.get("filter")),
					params.getAsDouble("epsilon"),
					repo.get(params.get("logger")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			// TODO Auto-generated method stub
			return null;
		}

	}

}
