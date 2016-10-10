/*******************************************************************************
 * Copyright (C) 2011 - 2015 Yoav Artzi, All rights reserved.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *******************************************************************************/
package edu.uw.cs.lil.amr.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.ccg.lexicon.ILexiconImmutable;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.Simplify;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphDerivation;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphParser;
import edu.cornell.cs.nlp.spf.parser.graph.IGraphParserOutput;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilter;
import edu.cornell.cs.nlp.spf.parser.joint.JointInferenceFilterUtils;
import edu.cornell.cs.nlp.spf.parser.joint.graph.IJointGraphParser;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.composites.Triplet;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.math.LogSumExp;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.parser.GraphAmrDerivation.Builder;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen.IAssignmentGeneratorFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.features.IFactorGraphFeatureSet;

public class GraphAmrParser extends
		AbstractAmrParser<IGraphDerivation<LogicalExpression>> implements
		IJointGraphParser<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression, LogicalExpression> {

	public static final ILogger													LOG					= LoggerFactory
			.create(GraphAmrParser.class);

	private static final long													serialVersionUID	= 71078477728726201L;

	private final IGraphParser<SituatedSentence<AMRMeta>, LogicalExpression>	baseParser;

	public GraphAmrParser(
			IGraphParser<SituatedSentence<AMRMeta>, LogicalExpression> baseParser,
			IAssignmentGeneratorFactory assignmentGeneratorFactory,
			Set<IFactorGraphFeatureSet> factorGraphFeatures,
			double bpConvergenceThreshold, int bpMaxIterations, int maxLimit,
			Long bpMaxTime, InferenceMethod inferenceMethod, int beamSize,
			int factorLimit) {
		super(assignmentGeneratorFactory, bpConvergenceThreshold,
				bpMaxIterations, bpMaxTime, beamSize, factorGraphFeatures,
				factorLimit, inferenceMethod, maxLimit);
		this.baseParser = baseParser;
	}

	/**
	 * Create AMR derivations.
	 *
	 * @param inferenceTriplets
	 *            Triplets of a base derivation (packing many trees), list of
	 *            evaluation results for the underspecified logical form at the
	 *            root, and a exactness boolean flag. Each base derivation
	 *            appears in at most a single triplet.
	 */
	private static Pair<List<GraphAmrDerivation>, Boolean> createDerivations(
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter,
			List<Triplet<IGraphDerivation<LogicalExpression>, List<EvaluationResult>, Boolean>> inferenceTriplets) {

		// Aggregate the results.

		// Builders to accumulate the results from the factor graph.
		final Map<LogicalExpression, GraphAmrDerivation.Builder> builders = new HashMap<>();
		boolean exact = true;

		for (final Triplet<IGraphDerivation<LogicalExpression>, List<EvaluationResult>, Boolean> triplet : inferenceTriplets) {
			final IGraphDerivation<LogicalExpression> baseDerivation = triplet
					.first();

			// If a single triplet is not exact, the output is no longer exact;
			exact &= triplet.third();

			// Sum the exponentiated score for all evaluations for this triplet.
			// These are all the evaluations for this base derivation, since
			// each base derivation appears in at most one triplet.
			final List<Double> scores = new ArrayList<>();
			for (final EvaluationResult result : triplet.second()) {
				// Only take into account results that pass the pruning filter.
				// The entire distribution is conditioned on this filter.
				if (filter.testResult(result.getResult())) {
					// The addition expects the log score, so the linear score
					// is the log score of the exponentiated score.
					scores.add(result.getScore());
				}
			}
			final double evalLogNorm = LogSumExp.of(scores);

			for (final EvaluationResult result : triplet.second()) {
				if (filter.testResult(result.getResult())) {
					if (builders.containsKey(result.getResult())) {
						builders.get(result.getResult()).addInferencePair(
								baseDerivation, result, evalLogNorm);
					} else {
						builders.put(result.getResult(),
								new GraphAmrDerivation.Builder(baseDerivation,
										result, evalLogNorm));
					}
				}
			}
		}

		final List<GraphAmrDerivation> jointDerivations = new LinkedList<GraphAmrDerivation>();
		for (final Builder builder : builders.values()) {
			jointDerivations.add(builder.build());
		}

		return Pair.of(jointDerivations, exact);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		return parse(dataItem, model, false);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean allowWordSkipping) {
		return parse(dataItem, model, allowWordSkipping, null);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean allowWordSkipping,
			ILexiconImmutable<LogicalExpression> tempLexicon) {
		return parse(dataItem, model, allowWordSkipping, tempLexicon, null);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean allowWordSkipping,
			ILexiconImmutable<LogicalExpression> tempLexicon,
			Integer beamSize) {
		return parse(dataItem, model, JointInferenceFilterUtils.stubTrue(),
				allowWordSkipping, tempLexicon, beamSize);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter) {
		return parse(dataItem, model, filter, false);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter,
			boolean allowWordSkipping) {
		return parse(dataItem, model, filter, allowWordSkipping, null);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter,
			boolean allowWordSkipping,
			ILexiconImmutable<LogicalExpression> tempLexicon) {
		return parse(dataItem, model, filter, allowWordSkipping, tempLexicon,
				null);
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter,
			boolean sloppyInference,
			ILexiconImmutable<LogicalExpression> tempLexicon,
			Integer beamSize) {

		final long startTime = System.currentTimeMillis();

		// Create the base parsing filter with the given filter.
		final Predicate<ParsingOp<LogicalExpression>> parsingFilter = amrParsingFilter
				.and(op -> filter.test(op)).and(op -> {
					assert op.getCategory().getSemantics() == null || Simplify
							.of(op.getCategory().getSemantics()) == op
									.getCategory()
									.getSemantics() : "All results generated by parsing rules must be in normalized form: "
											+ op;

					return true;
				});

		// Parse with the base parser.
		final IGraphParserOutput<LogicalExpression> baseOutput = baseParser
				.parse(dataItem, op -> parsingFilter.test(op), model,
						sloppyInference, tempLexicon, beamSize);

		// Make sure the LF is a valid AMR: especially, all entities are closed
		// with no free variables.
		final Predicate<LogicalExpression> intermediateFilter = s -> s != null
				&& IsValidAmr.of(s, false, true) && filter.testStep(s);

		// Process each derivation from the base parse (packing possibly many
		// trees).
		final long baseProcessingStart = System.currentTimeMillis();
		// graphPairs is guaranteed to have at most one pair for each base
		// derivation. Some derivations might be dropped.
		final List<Pair<IGraphDerivation<LogicalExpression>, FactorGraph>> graphPairs = new ArrayList<>(
				baseOutput.getAllDerivations().size());
		for (final IGraphDerivation<LogicalExpression> derivation : baseOutput
				.getAllDerivations()) {
			final Pair<IGraphDerivation<LogicalExpression>, FactorGraph> pair = createFactorGraph(
					derivation, dataItem.getState(), model, intermediateFilter,
					sloppyInference);
			if (pair != null) {
				graphPairs.add(pair);
			}
		}
		final long baseProcessingTime = System.currentTimeMillis()
				- baseProcessingStart;

		// Do the inference over the factor graph and create the complete
		// derivations.
		final Pair<List<GraphAmrDerivation>, Boolean> inferencePair;
		final long secondStageInferenceStart = System.currentTimeMillis();
		switch (inferenceMethod) {
			case BEAM:
				inferencePair = createDerivations(filter,
						beamSearchInference(graphPairs, sloppyInference,
								beamSize == null ? defaultBeamSize : beamSize));
				break;
			case LBP:
				inferencePair = createDerivations(filter,
						loopyBPInference(graphPairs, model, sloppyInference));
				break;
			case NONE:
				inferencePair = createDerivations(filter,
						noFactorGraphDummyInference(graphPairs));
				break;
			default:
				throw new IllegalStateException(
						"Invalid inference method: " + inferenceMethod);
		}
		final long secondStageInferenceTime = System.currentTimeMillis()
				- secondStageInferenceStart;

		// Copying theta so that the output won't be sensitive to model changes.
		// Only do this if using LBP, otherwise can get the saved viterbi scores
		// from the factor graph.
		final GraphAmrParserOutput output = new GraphAmrParserOutput(
				inferencePair.first(), baseOutput,
				System.currentTimeMillis() - startTime, graphPairs,
				inferencePair.second() && baseOutput.isExact(),
				inferenceMethod == InferenceMethod.LBP
						? HashVectorFactory.create(model.getTheta()) : null,
				inferenceMethod);

		final double perecentCky = 100 * baseOutput.getParsingTime()
				/ (double) output.getInferenceTime();
		final double percentSecond = 100 * secondStageInferenceTime
				/ (double) output.getInferenceTime();
		final double percentBaseProcess = 100 * baseProcessingTime
				/ (double) output.getInferenceTime();
		LOG.info(
				"Total AMR parsing time: %.4fsec (cky=%.2f%%, baseProcess=%.2f%%, fg=%.2f%%)",
				output.getInferenceTime() / 1000.0, perecentCky,
				percentBaseProcess, percentSecond);
		LOG.info("CKY parsing time: %.4fsec (%.2f%%)",
				baseOutput.getParsingTime() / 1000.0, perecentCky);
		LOG.info("Processed %d base parses, created %d pairs: %.4fsec (%.2f%%)",
				baseOutput.getAllDerivations().size(), graphPairs.size(),
				baseProcessingTime / 1000.0, percentBaseProcess);
		LOG.info("Second stage inference time (%d pairs): %.4fsec (%.2f%%)",
				graphPairs.size(), secondStageInferenceTime / 1000.0,
				percentSecond);

		return output;
	}

	@Override
	public GraphAmrParserOutput parse(SituatedSentence<AMRMeta> dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			IJointInferenceFilter<LogicalExpression, LogicalExpression, LogicalExpression> filter,
			Integer beamSize) {
		return parse(dataItem, model, filter, false, null, beamSize);
	}

	public static class Creator
			implements IResourceObjectCreator<GraphAmrParser> {

		private final String type;

		public Creator() {
			this("parser.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public GraphAmrParser create(Parameters params,
				IResourceRepository repo) {
			return new GraphAmrParser(
					repo.get(params.get("baseParser")), repo
							.get(params.get("assignmentFactory")),
					params.getSplit("graphFeatures").stream()
							.map((id) -> (IFactorGraphFeatureSet) repo.get(id))
							.collect(Collectors.toSet()),
					params.getAsDouble("bpThreshold", 0.001),
					params.getAsInteger("bpIter", 100),
					params.getAsInteger("maxParsesLimit", 200),
					params.contains("bpTime") ? params.getAsLong("bpTime")
							: null,
					InferenceMethod.valueOf(params.get("infer")),
					params.getAsInteger("beam", 50),
					params.getAsInteger("factorLimit", 1000));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type, GraphAmrParser.class)
					.addParam("beam", Integer.class,
							"Beam size for beam search inference (not used with LBP) (default: 50)")
					.addParam("infer", InferenceMethod.class,
							"Inference method to use (BEAM, LBP and NONE)")
					.addParam("bpTime", Long.class,
							"Maximum time for LBP in millisecond (not used with BEAM) (default: no limit)")
					.addParam("baseParser", IGraphParser.class,
							"Chart-based parser")
					.addParam("factorLimit", Integer.class,
							"Hard limit of the number of factors in a factor graph (default: 1000)")
					.addParam("graphFeatures", IFactorGraphFeatureSet.class,
							"List of factor graph feature sets")
					.addParam("bpThreshold", Double.class,
							"LBP convergence threshold (not used with BEAM) (default: 0.001)")
					.addParam("bpIter", Integer.class,
							"LBP maximum number of iterations (not used with BEAM) (default: 100)")
					.addParam("maxParsesLimit", Integer.class,
							"Max number of expressions to extract from a factor graph (default: 200)")
					.build();
		}
	}

}
