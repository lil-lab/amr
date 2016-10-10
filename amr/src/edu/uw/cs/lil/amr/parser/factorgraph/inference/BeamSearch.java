package edu.uw.cs.lil.amr.parser.factorgraph.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.queue.BoundedPriorityQueue;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.EvaluationResult;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IFactor;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetFactors;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.GetVariables;

/**
 * Inference with beam search over the space of configurations. In each step,
 * the configuration is expanded by specifying another variables and adding to
 * the score and features the factors connected to it that are now fully
 * specified.
 *
 * @author Yoav Artzi
 */
public class BeamSearch {

	public static final ILogger LOG = LoggerFactory.create(BeamSearch.class);

	public static Pair<List<EvaluationResult>, Boolean> of(FactorGraph graph,
			int beamSize) {
		return of(graph, beamSize, false);
	}

	public static Pair<List<EvaluationResult>, Boolean> of(FactorGraph graph,
			int beamSize, boolean sloppy) {

		LOG.debug("Starting beam search for: %s",
				graph.getRoot().getExpression());

		// Determine the order in which the variables are iterated. Iterate
		// first over variables that have a single assignment. Then start with
		// variables with low-connectivity.
		final List<INode> variables = GetVariables.of(graph).stream()
				.sorted((n1, n2) -> {
					if (n1.numAssignments() == 1 && n2.numAssignments() != 1) {
						return -1;
					} else if (n2.numAssignments() == 1
							&& n1.numAssignments() != 1) {
						return 1;
					} else {
						return Integer.compare(n1.numEdges(), n2.numEdges());
					}

				}).collect(Collectors.toList());

		final List<Configuration> configurations = new ArrayList<>(beamSize);
		configurations.add(new Configuration(0.0, Collections.emptyMap(),
				Collections.emptyMap()));
		final Set<INode> specifiedVariables = new HashSet<>();
		final Set<IFactor> allFactors = GetFactors.of(graph);
		final AtomicBoolean exact = new AtomicBoolean(true);
		for (final INode variable : variables) {
			specifiedVariables.add(variable);

			LOG.debug("Processing variable: %s", variable);

			// Get the factors added by specifying this variable.
			final int numEdges = variable.numEdges();
			final Set<IFactor> newFactors = new HashSet<>();
			for (int i = 0; i < numEdges; ++i) {
				final IFactor factor = variable.getEdge(i).getFactor();
				final int factorEdges = factor.numEdges();
				boolean fullySpecified = true;
				for (int j = 0; j < factorEdges; ++j) {
					if (!specifiedVariables
							.contains(factor.getEdge(j).getVariable())) {
						fullySpecified = false;
						break;
					}
				}
				if (fullySpecified) {
					newFactors.add(factor);
				}
			}

			// Expand each existing configuration by specifying this variable
			// and creating a new configuration with an updated score.
			final PriorityQueue<Configuration> queue = new BoundedPriorityQueue<>(
					beamSize,
					(c1, c2) -> Double.compare(c1.getScore(), c2.getScore()));

			StreamSupport
					.stream(Spliterators.<Configuration> spliterator(
							configurations, Spliterator.IMMUTABLE), true)
					.forEach(configuration -> expand(configuration, variable,
							newFactors).forEach(c -> {
								synchronized (queue) {
									LOG.debug("Oferring: %s", c);
									if (!queue.offer(c)) {
										exact.set(false);
										LOG.debug(() -> {
											LOG.debug(
													"Failed to queue: %s, peek=%s",
													c, queue.peek());
										});
									}
								}
							}));

			if (queue.isEmpty() && sloppy) {
				// Case the queue over-flowed and we allow sloppy inference,
				// simply assign this variable in underspecified form and don't
				// trigger any features.
				configurations.stream().map(c -> {
					final Map<INode, LogicalExpression> newMapping = new HashMap<>(
							c.getMapping());
					newMapping.put(variable, variable.getExpression());
					return new Configuration(c.getScore(), newMapping,
							c.getFeatureMapping());
				});
			} else {
				// Replace the list of current configurations with the queue.
				configurations.clear();
				configurations.addAll(queue);
			}

			// Track all specified factors. This is for book keeping only.
			allFactors.removeAll(newFactors);
		}

		// Verify all the factors where consumed.
		if (!allFactors.isEmpty()) {
			LOG.error("%d factors not processed", allFactors.size());
			throw new RuntimeException();
		}

		// For each configuration create the output LF.
		final List<EvaluationResult> results = configurations.stream()
				.map(configuration -> {
					final LogicalExpression specified = GetExpression.of(graph,
							configuration.getMapping());
					final EvaluationResult evaluationResult = new EvaluationResult(
							configuration.getScore(),
							configuration.getFeatures(), specified);
					LOG.debug("Creating evaluation result [hash=%d]: %s -> %s",
							evaluationResult.hashCode(), configuration,
							specified);
					return evaluationResult;
				}).collect(Collectors.toList());

		return Pair.of(results, exact.get());
	}

	private static Configuration expand(Configuration configuration,
			INode variable, LogicalExpression assignment,
			Set<IFactor> factors) {
		LOG.debug("Expansion, in %d: %s -> %s", configuration.hashCode(),
				assignment, variable);
		LOG.debug("Expanding: %s", configuration);
		final Map<INode, LogicalExpression> newMapping = new HashMap<>(
				configuration.getMapping());
		newMapping.put(variable, assignment);
		final Map<IFactor, IHashVectorImmutable> featureMapping = new HashMap<>(
				configuration.getFeatureMapping());
		double score = configuration.getScore();
		for (final IFactor factor : factors) {
			assert factor.getTable().isMappingComplete(
					newMapping) : "The mapping must fully specify the factor at this point";
			final double oldScore = score;
			score += factor.getTable().get(newMapping);
			LOG.debug("Adding factor to %d (%.4f -> %.4f): %s",
					configuration.hashCode(), oldScore, score, factor);
			featureMapping.put(factor,
					factor.getTable().getFeatures(newMapping));
		}
		final Configuration expanded = new Configuration(score, newMapping,
				featureMapping);
		LOG.debug("Created configuration: %d -> %s", configuration.hashCode(),
				expanded);
		return expanded;
	}

	private static Stream<Configuration> expand(Configuration configuration,
			INode variable, Set<IFactor> factors) {
		return IntStream.range(0, variable.numAssignments())
				.mapToObj(index -> expand(configuration, variable,
						variable.getAssignment(index), factors));
	}

	private static class Configuration {

		private final Map<IFactor, IHashVectorImmutable>	featureMapping;
		private final Map<INode, LogicalExpression>			mapping;
		private final double								score;

		public Configuration(double score,
				Map<INode, LogicalExpression> mapping,
				Map<IFactor, IHashVectorImmutable> featureMapping) {
			this.score = score;
			this.featureMapping = Collections.unmodifiableMap(featureMapping);
			this.mapping = Collections.unmodifiableMap(mapping);
		}

		public Map<IFactor, IHashVectorImmutable> getFeatureMapping() {
			return featureMapping;
		}

		public IHashVectorImmutable getFeatures() {
			final IHashVector features = HashVectorFactory.create();
			for (final IHashVectorImmutable factorFeatures : featureMapping
					.values()) {
				if (factorFeatures != null) {
					factorFeatures.addTimesInto(1.0, features);
				}
			}
			return features;
		}

		public Map<INode, LogicalExpression> getMapping() {
			return mapping;
		}

		public double getScore() {
			return score;
		}

		@Override
		public String toString() {
			return String.format("[%.4f, %d, hash=%d] %s", score,
					mapping.size(), hashCode(), mapping);
		}

	}

}
