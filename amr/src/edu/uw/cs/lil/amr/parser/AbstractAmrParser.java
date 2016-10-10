package edu.uw.cs.lil.amr.parser;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.parser.IDerivation;
import edu.cornell.cs.nlp.spf.parser.joint.IJointParser;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.composites.Triplet;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.IsUnderspecifiedAndStripped;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen.IAssignmentGeneratorFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.features.IFactorGraphFeatureSet;
import edu.uw.cs.lil.amr.parser.factorgraph.inference.BeamSearch;
import edu.uw.cs.lil.amr.parser.factorgraph.inference.LoopyBP;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.CreateFactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetFactors;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetMapping;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetMaxEvaluations;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetVariables;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.MarginalsProduct;

public abstract class AbstractAmrParser<DERIV extends IDerivation<LogicalExpression>>
		implements
		IJointParser<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression, LogicalExpression> {

	public static final ILogger					LOG					= LoggerFactory
			.create(AbstractAmrParser.class);

	private static final long					serialVersionUID	= 2940768166479485877L;

	protected final AmrParsingFilter			amrParsingFilter	= new AmrParsingFilter();

	protected final IAssignmentGeneratorFactory	assignmentGeneratorFactory;
	protected final double						bpConvergenceThreshold;
	protected final int							bpMaxIterations;
	protected final Long						bpMaxTime;
	protected final int							defaultBeamSize;
	protected final Set<IFactorGraphFeatureSet>	factorGraphFeatures;
	protected final int							factorLimit;
	protected final InferenceMethod				inferenceMethod;
	protected final int							maxLimit;

	public AbstractAmrParser(
			IAssignmentGeneratorFactory assignmentGeneratorFactory,
			double bpConvergenceThreshold, int bpMaxIterations, Long bpMaxTime,
			int defaultBeamSize,
			Set<IFactorGraphFeatureSet> factorGraphFeatures, int factorLimit,
			InferenceMethod inferenceMethod, int maxLimit) {
		this.assignmentGeneratorFactory = assignmentGeneratorFactory;
		this.bpConvergenceThreshold = bpConvergenceThreshold;
		this.bpMaxIterations = bpMaxIterations;
		this.bpMaxTime = bpMaxTime;
		this.defaultBeamSize = defaultBeamSize;
		this.factorGraphFeatures = factorGraphFeatures;
		this.factorLimit = factorLimit;
		this.inferenceMethod = inferenceMethod;
		this.maxLimit = maxLimit;
	}

	private static Pair<List<EvaluationResult>, Boolean> doBeamSearchInference(
			FactorGraph graph, boolean sloppyInference, int beamSize) {
		final long startTime = System.currentTimeMillis();
		final Pair<List<EvaluationResult>, Boolean> inferencePair = BeamSearch
				.of(graph, beamSize, sloppyInference);
		LOG.debug(
				"Beam search stats: time=%.4f, variables=%d, factors=%d, beam=%d, #results=%d",
				(System.currentTimeMillis() - startTime) / 1000.0,
				GetVariables.of(graph).size(), GetFactors.of(graph).size(),
				beamSize, inferencePair.first().size());
		return inferencePair;
	}

	private static Pair<List<EvaluationResult>, Boolean> doFactorGraphDummyInference(
			FactorGraph graph) {
		// Get the logical expression at the base of the graph as
		// the evaluation result. This will include the Skolem IDs
		// and any closure that was applied.
		return Pair.of(ListUtils.createSingletonList(new EvaluationResult(0.0,
				HashVectorFactory.create(), graph.getRoot().getExpression())),
				true);
	}

	/**
	 * Conditioned second stage inference. Given the final logical form, will
	 * generate the evaluation result leading to it, including most of the
	 * features (for example, features that rely on the surface form are not
	 * available).
	 */
	public EvaluationResult conditionedSecondStageInference(
			LogicalExpression exp, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			LogicalExpression label) {
		// Make sure the logical expression is a valid underspecified AMR
		// logical expression.
		if (!IsValidAmr.of(exp, false, false)
				|| !IsUnderspecifiedAndStripped.of(exp)) {
			LOG.error(
					"Second stage inference required a valid underspecified and stripped AMR: %s",
					exp);
			return null;
		}

		// Create the factor graph.
		final FactorGraph graph = createFactorGraph(exp, meta, model, false);

		// Get the mapping from the factor graph that will give the
		// result, if such exists.
		final Map<INode, LogicalExpression> mapping = GetMapping.of(graph,
				label);

		if (mapping != null) {
			// Collect the features using the mapping.
			final IHashVector features = HashVectorFactory.create();
			for (final IFactor factor : GetFactors.of(graph)) {
				factor.getTable().getFeatures(mapping).addTimesInto(1.0,
						features);
			}

			// Create the evaluation object and the joint derivation.
			final EvaluationResult evaluation = graph.hasMarginals()
					? new ProbEvaluationResult(
							model.getTheta().dotProduct(features),
							MarginalsProduct.of(graph, mapping), features,
							label)
					: new EvaluationResult(
							model.getTheta().dotProduct(features), features,
							label);

			return evaluation;
		} else {
			LOG.info(
					"Failed to generate mapping for second stage conditioned inference");
			return null;
		}
	}

	public Pair<List<EvaluationResult>, Boolean> secondStageInference(
			LogicalExpression exp, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model) {
		return secondStageInference(exp, meta, model, null);
	}

	public Pair<List<EvaluationResult>, Boolean> secondStageInference(
			LogicalExpression exp, AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			Integer beamSize) {

		// Make sure the logical expression is a valid underspecified AMR
		// logical expression.
		if (!IsValidAmr.of(exp, false, false)
				|| !IsUnderspecifiedAndStripped.of(exp)) {
			LOG.error(
					"Second stage inference required a valid underspecified and stripped AMR: %s",
					exp);
			return null;
		}

		// Create the factor graph.
		final FactorGraph graph = createFactorGraph(exp, meta, model, false);

		// Do the inference over the factor graph and create the complete
		// derivations.
		final Pair<List<EvaluationResult>, Boolean> inferencePair;
		final long start = System.currentTimeMillis();
		switch (inferenceMethod) {
			case BEAM:
				inferencePair = doBeamSearchInference(graph, false,
						beamSize == null ? defaultBeamSize : beamSize);
				break;
			case LBP:
				inferencePair = doLoopyBPInference(graph, model, false);
				break;
			case NONE:
				inferencePair = doFactorGraphDummyInference(graph);
				break;
			default:
				throw new IllegalStateException(
						"Invalid inference method: " + inferenceMethod);
		}

		LOG.debug("Second stage inference: %.3f",
				(System.currentTimeMillis() - start) / 1000.0);

		return inferencePair;

	}

	private FactorGraph createFactorGraph(LogicalExpression semantics,
			AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean sloppyClosure) {
		// Create IDs in the logical form and merge identical named entities.
		final SetIds setIdsVisitor = new SetIds();
		setIdsVisitor.visit(semantics);
		final LogicalExpression underspecified = MergeNamedEntities
				.of(setIdsVisitor.tempReturn);

		LOG.debug("Processing underspecified: %s", underspecified);

		// Create factor graph form the logical form.
		final long factorsStartTime = System.currentTimeMillis();
		final FactorGraph graph = CreateFactorGraph.of(underspecified,
				assignmentGeneratorFactory.create(underspecified),
				sloppyClosure);

		// Populate factor graph with features (i.e., factor nodes). First
		// create the jobs, then execute them in parallel.
		final List<Runnable> factorRunnables = new LinkedList<>();
		for (final IFactorGraphFeatureSet featureSet : factorGraphFeatures) {
			factorRunnables
					.addAll(featureSet.createFactorJobs(graph, meta, model));
		}

		if (factorRunnables.size() > factorLimit) {
			LOG.debug(
					"Created %d factor jobs (over the limit of %d), pruning intermediate",
					factorRunnables.size(), factorLimit);
			return null;
		}

		LOG.debug("Created %d factor jobs with %d variables, running...",
				factorRunnables.size(), GetVariables.of(graph).size());

		StreamSupport.stream(Spliterators.spliterator(factorRunnables,
				Spliterator.IMMUTABLE), true).forEach(r -> {
					LOG.debug("Running factor job: %s", r);
					r.run();
				});

		LOG.debug(
				"Created a factor graph with %d factors and %d variables (%.4fsec)",
				GetFactors.of(graph).size(), GetVariables.of(graph).size(),
				(System.currentTimeMillis() - factorsStartTime) / 1000.0);
		return graph;
	}

	/**
	 * Do LBP inference for the factor graph and extract the
	 * {@link EvaluationResult}s.
	 *
	 *
	 * @return Pair with list of {@link EvaluationResult} and an inference flag
	 *         (if the flag is 'true', there were too many max-scoring
	 *         evaluations and none were returned).
	 */
	private Pair<List<EvaluationResult>, Boolean> doLoopyBPInference(
			FactorGraph graph,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean sloppyInference) {
		// Loopy BP inference
		LoopyBP.of(graph, bpConvergenceThreshold, bpMaxIterations, bpMaxTime);

		// Get the max configurations.
		final List<EvaluationResult> argmax = new LinkedList<>(
				GetMaxEvaluations.of(graph, maxLimit, model, sloppyInference));
		return Pair.of(argmax, !argmax.isEmpty());
	}

	protected List<Triplet<DERIV, List<EvaluationResult>, Boolean>> beamSearchInference(
			List<Pair<DERIV, FactorGraph>> graphPairs, boolean sloppyInference,
			int beamSize) {

		// Factor graph inference (streaming to distribute computation).
		final List<Triplet<DERIV, List<EvaluationResult>, Boolean>> inferenceTriplets = StreamSupport
				.stream(Spliterators.spliterator(graphPairs,
						Spliterator.IMMUTABLE), true)
				.map(pair -> {
					final Pair<List<EvaluationResult>, Boolean> inferencePair = doBeamSearchInference(
							pair.second(), sloppyInference, beamSize);
					return Triplet.of(pair.first(), inferencePair.first(),
							inferencePair.second());
				}).collect(Collectors.toList());

		return inferenceTriplets;
	}

	protected Pair<DERIV, FactorGraph> createFactorGraph(DERIV derivation,
			AMRMeta meta,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			Predicate<LogicalExpression> intermediateFilter,
			boolean sloppyInference) {

		// Sanity check. All generated LFs should be valid AMR, even if not a
		// complete one.
		assert derivation.getSemantics() == null
				|| IsValidAmr.of(derivation.getSemantics(), false,
						false) : "LF is not a valid AMR: " + derivation;

		final Category<LogicalExpression> rootCategory = derivation
				.getCategory();

		final LogicalExpression semantics;
		final boolean sloppy;
		if (rootCategory.getSyntax()
				.equals(AMRServices.getCompleteSentenceSyntax())) {
			semantics = rootCategory.getSemantics();
			sloppy = false;
		} else if (sloppyInference && rootCategory.getSemantics() != null) {
			// If doing sloppy inference, try to sloppily make this into a valid
			// complete AMR.
			semantics = SloppyAmrClosure.of(rootCategory.getSemantics());
			sloppy = true;
			LOG.debug("Sloppy AMR closure: %s -> %s", rootCategory, semantics);
		} else {
			semantics = null;
			sloppy = false;
		}

		if (semantics == null || !intermediateFilter.test(semantics)) {
			LOG.debug("Pruned intermediate: %s", rootCategory);
			return null;
		}

		// Create the actual factor graph for the processed logical form.
		final FactorGraph graph = createFactorGraph(semantics, meta, model,
				sloppy);

		return Pair.of(derivation, graph);
	}

	/**
	 * LBP inference for the second stage.
	 *
	 * @return Triplet that includes the base derivation, list of
	 *         {@link EvaluationResult}, and the inference exactness flag (if
	 *         the flag is 'true', there were too many max-scoring evaluations
	 *         and none were returned).
	 */
	protected List<Triplet<DERIV, List<EvaluationResult>, Boolean>> loopyBPInference(
			List<Pair<DERIV, FactorGraph>> graphPairs,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean sloppyInference) {
		final List<Triplet<DERIV, List<EvaluationResult>, Boolean>> inferenceTriplets = new LinkedList<>();

		// Mapping of final results to derivation builders.
		for (final Pair<DERIV, FactorGraph> resultPair : graphPairs) {
			final Pair<List<EvaluationResult>, Boolean> inferencePair = doLoopyBPInference(
					resultPair.second(), model, sloppyInference);
			inferenceTriplets.add(Triplet.of(resultPair.first(),
					inferencePair.first(), inferencePair.second()));
		}

		return inferenceTriplets;
	}

	/**
	 * Dummy inference for the second stage.
	 *
	 * @param graphPairs
	 * @return Dummy inference pair with a singleton list of
	 *         {@link EvaluationResult} and an inference exactness flag (that is
	 *         always true).
	 */
	protected List<Triplet<DERIV, List<EvaluationResult>, Boolean>> noFactorGraphDummyInference(
			List<Pair<DERIV, FactorGraph>> graphPairs) {

		// Factor graph inference (streaming to distribute computation).
		final List<Triplet<DERIV, List<EvaluationResult>, Boolean>> inferenceTriplets = StreamSupport
				.stream(Spliterators.spliterator(graphPairs,
						Spliterator.IMMUTABLE), true)
				.map(pair -> {
					final Pair<List<EvaluationResult>, Boolean> inferencePair = doFactorGraphDummyInference(
							pair.second());

					return Triplet.of(pair.first(), inferencePair.first(),
							inferencePair.second());
				}).collect(Collectors.toList());

		return inferenceTriplets;
	}

	/**
	 * Set IDs for all skolem terms except reference IDs. Assumes the ID is the
	 * first argument of the quantifier.
	 *
	 * @author Yoav Artzi
	 */
	private class SetIds implements ILogicalExpressionVisitor {

		private LogicalExpression tempReturn = null;

		@Override
		public void visit(Lambda lambda) {
			lambda.getBody().accept(this);
			if (lambda.getBody() != tempReturn) {
				tempReturn = new Lambda(lambda.getArgument(), tempReturn);
			} else {
				tempReturn = lambda;
			}
		}

		@Override
		public void visit(Literal literal) {
			// Visit the predicate.
			literal.getPredicate().accept(this);
			final LogicalExpression newPredicate = tempReturn;

			// Go over the arguments.
			final int numArgs = literal.numArgs();
			final LogicalExpression[] args = new LogicalExpression[numArgs];
			boolean argChanged = false;
			for (int i = 0; i < numArgs; ++i) {
				final LogicalExpression arg = literal.getArg(i);
				arg.accept(this);
				args[i] = tempReturn;
				if (tempReturn != arg) {
					argChanged = true;
				}
			}

			final Literal updatedLiteral;
			if (argChanged) {
				updatedLiteral = new Literal(newPredicate, args);
			} else if (newPredicate != literal.getPredicate()) {
				updatedLiteral = new Literal(newPredicate, literal);
			} else {
				updatedLiteral = literal;
			}

			if (AMRServices.isSkolemPredicate(updatedLiteral.getPredicate())
					&& updatedLiteral.getArg(0)
							.equals(SkolemServices.getIdPlaceholder())) {
				final LogicalExpression[] argsWithID = updatedLiteral
						.argumentCopy();
				argsWithID[0] = new SkolemId();
				tempReturn = new Literal(updatedLiteral.getPredicate(),
						argsWithID);
			} else {
				tempReturn = updatedLiteral;
			}

		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			// Nothing to do.
			tempReturn = logicalConstant;
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do.
			tempReturn = variable;
		}

	}

}
