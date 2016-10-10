package edu.uw.cs.lil.amr.parser.factorgraph.table;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.CollectionUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.math.LogSumExp;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;

/**
 * Table to store values according to a tuple of keys. The table allows to
 * marginalize over specific keys to sum. The table supports marginalizing over
 * log-space values.
 *
 * @author Yoav Artzi
 */
public class Table implements ITableImmutable, Cloneable {

	private final ColumnHeader[] headers;

	private final int[] indexMultiplier;

	private final int numKeys;

	/**
	 * Indicates if the values are in log-space. This flag is used to decide how
	 * to marginalize over variables when getting values, using sum (for normal
	 * space) or sum-log-exp (for log-space).
	 */
	protected final boolean logSpace;

	protected final double[] values;

	public Table(boolean logSpace, ColumnHeader... headers) {
		this.logSpace = logSpace;
		this.headers = headers;
		// Compute the number of rows, and allocate the values array.
		this.values = new double[Arrays.stream(headers)
				.mapToInt(ColumnHeader::numValues)
				.reduce(1, (i1, i2) -> i1 * i2)];
		// Init the array.
		Arrays.setAll(values, (i) -> logSpace ? 0.0 : 1.0);
		this.numKeys = headers.length;
		this.indexMultiplier = new int[numKeys];
		int accumulator = 1;
		for (int i = numKeys - 1; i >= 0; --i) {
			indexMultiplier[i] = accumulator;
			accumulator *= headers[i].numValues();
		}
	}

	public Table(Table other, boolean logSpace) {
		this.headers = other.headers;
		this.indexMultiplier = other.indexMultiplier;
		this.numKeys = other.numKeys;
		this.values = Arrays.copyOf(other.values, other.values.length);
		this.logSpace = logSpace;
		if (this.logSpace != other.logSpace) {
			for (int i = 0; i < values.length; ++i) {
				this.values[i] = logSpace ? Math.log(this.values[i])
						: Math.exp(this.values[i]);
			}
		}
	}

	private Table(Table other) {
		this.headers = other.headers;
		this.indexMultiplier = other.indexMultiplier;
		this.numKeys = other.numKeys;
		this.logSpace = other.logSpace;
		this.values = new double[other.values.length];
	}

	private static Map<INode, LogicalExpression> pairsToMap(
			MappingPair... mappingPairs) {
		final Map<INode, LogicalExpression> mapping = new HashMap<>();
		for (final MappingPair pair : mappingPairs) {
			if (mapping.put(pair.getNode(), pair.getValue()) != null) {
				throw new IllegalArgumentException(
						"Duplicate mapping of key to value: " + mappingPairs);
			}
		}
		return mapping;
	}

	/**
	 * @see #add(Map, double)
	 */
	public void add(double value, MappingPair... mappingPairs) {
		add(pairsToMap(mappingPairs), value);
	}

	/**
	 * Set the corresponding value to the current value plus the given value.
	 */
	public void add(Map<INode, LogicalExpression> valueMapping, double value) {
		apply(computeIndexedKey(valueMapping), (double d) -> d + value);
	}

	/**
	 * @see #apply(Map, DoubleUnaryOperator)
	 */
	public void apply(DoubleUnaryOperator function,
			MappingPair... mappingPairs) {
		apply(pairsToMap(mappingPairs), function);
	}

	/**
	 * Apply the give operator the all values specified by the given mapping.
	 */
	public void apply(Map<INode, LogicalExpression> valueMapping,
			DoubleUnaryOperator operator) {
		apply(computeIndexedKey(valueMapping), operator);
	}

	@Override
	public Table clone() {
		final Table table = new Table(this);
		System.arraycopy(values, 0, table.values, 0, values.length);
		return table;
	}

	public Table cloneEmpty() {
		final Table table = new Table(this);
		// Init the array.
		Arrays.setAll(table.values, (i) -> table.logSpace ? 0.0 : 1.0);
		return table;
	}

	/**
	 * Checks if the two tables are equivalent up to a threshold delta. If
	 * 'true', it's guaranteed that the maximum difference between two values in
	 * the tables is smaller or equal to delta.
	 */
	public boolean equals(Table other, double delta) {
		if (!headers.equals(other.headers)) {
			return false;
		}

		final int length = values.length;
		for (int i = 0; i < length; ++i) {
			if (logSpace) {
				if (Math.abs(Math.exp(values[i])
						- Math.exp(other.values[i])) > delta) {
					return false;
				}
			} else if (Math.abs(values[i] - other.values[i]) > delta) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Gets the requested value from the table, marginalizes (sums) over all
	 * un-specified keys.
	 */
	@Override
	public double get(Map<INode, LogicalExpression> valueMapping) {
		final DoubleValue sum = new DoubleValue();
		if (logSpace) {
			sum.value = Double.NEGATIVE_INFINITY;
		} else {
			sum.value = 0.0;
		}
		apply(computeIndexedKey(valueMapping), operand -> {
			if (logSpace) {
				sum.value = LogSumExp.of(sum.value, operand);
			} else {
				sum.value += operand;
			}
			return operand;
		});
		return sum.value;
	}

	/**
	 * @see #get(Map)
	 */
	@Override
	public double get(MappingPair... mappingPairs) {
		return get(pairsToMap(mappingPairs));
	}

	public boolean isMappingComplete(Map<INode, LogicalExpression> mapping) {
		for (final ColumnHeader header : headers) {
			if (!mapping.containsKey(header.getNode())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @see #multiply(Map, double)
	 */
	public void multiply(double value, MappingPair... mappingPairs) {
		multiply(pairsToMap(mappingPairs), value);
	}

	/**
	 * Set the corresponding values to the current value times the given value.
	 */
	public void multiply(Map<INode, LogicalExpression> valueMapping,
			double value) {
		apply(computeIndexedKey(valueMapping), (double d) -> d * value);
	}

	/**
	 * Normalize the values in the table.
	 */
	public void normalize() {
		final int length = values.length;
		if (logSpace) {
			final double sum = LogSumExp.of(values);
			for (int i = 0; i < length; ++i) {
				values[i] -= sum;
			}
		} else {
			double sum = 0.0;
			for (int i = 0; i < length; ++i) {
				sum += values[i];
			}
			for (int i = 0; i < length; ++i) {
				values[i] /= sum;
			}
		}
	}

	/**
	 * @see #set(Map, double)
	 */
	public void set(double value, MappingPair... mappingPairs) {
		set(pairsToMap(mappingPairs), value);
	}

	/**
	 * Set all values specified by the mapping to the given value.
	 */
	public void set(Map<INode, LogicalExpression> valueMapping, double value) {
		apply(computeIndexedKey(valueMapping), (double d) -> value);
	}

	/**
	 * Set all values in the table to a value.
	 */
	public void setAll(double value) {
		final int length = values.length;
		for (int i = 0; i < length; ++i) {
			values[i] = value;
		}
	}

	@Override
	public int size() {
		return values.length;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();

		// Header.
		sb.append("value\t");
		for (int i = 0; i < numKeys; ++i) {
			sb.append(headers[i].getNode().getExpression());
			if (i + 1 < numKeys) {
				sb.append('\t');
			}
		}
		sb.append('\n');

		final Iterator<List<LogicalExpression>> iterator = CollectionUtils
				.cartesianProduct(Arrays.stream(headers)
						.map((ColumnHeader header) -> header.getNode()
								.slowGetAssignments())
						.collect(Collectors.toList()))
				.iterator();

		while (iterator.hasNext()) {
			final List<LogicalExpression> keys = iterator.next();
			final Map<INode, LogicalExpression> mapping = new HashMap<>();
			for (int i = 0; i < numKeys; ++i) {
				mapping.put(headers[i].getNode(), keys.get(i));
			}
			sb.append(get(mapping)).append('\t')
					.append(keys.stream().map(LogicalExpression::toString)
							.collect(Collectors.joining("\t")));
			if (iterator.hasNext()) {
				sb.append('\n');
			}
		}

		return sb.toString();
	}

	private void apply(int[] indexedKeys, DoubleUnaryOperator operator) {
		// Count the number of wildcards to allocate the wildcards index array.
		int numWildcards = 0;
		for (int i = 0; i < numKeys; ++i) {
			if (indexedKeys[i] < 0) {
				++numWildcards;
			}
		}

		// Shortcircuit in the case of no wildcards, simply apply the operator
		// to the value.
		if (numWildcards == 0) {
			final int index = computeIndex(indexedKeys);
			values[index] = operator.applyAsDouble(values[index]);
			return;
		}

		// Create an array to hold all wildcards to quickly access them and
		// initialize the start fully specified key. Also, for each wildcard,
		// also have the max value.
		final int[] currentKey = Arrays.copyOf(indexedKeys, numKeys);
		final int[] wildcardIndices = new int[numWildcards];
		final int[] wildcardMaxValue = new int[numWildcards];
		for (int i = 0, currentWildcard = 0; i < numKeys; ++i) {
			if (indexedKeys[i] < 0) {
				wildcardMaxValue[currentWildcard] = headers[i].numValues() - 1;
				wildcardIndices[currentWildcard++] = i;
				currentKey[i] = 0;
			}
		}

		while (true) {
			// Apply the operator.
			final int index = computeIndex(currentKey);
			values[index] = operator.applyAsDouble(values[index]);

			// Increment the indexed key array.
			boolean incremented = false;
			for (int wildcardIndex = 0; wildcardIndex < numWildcards; ++wildcardIndex) {
				if (currentKey[wildcardIndices[wildcardIndex]] < wildcardMaxValue[wildcardIndex]) {
					++currentKey[wildcardIndices[wildcardIndex]];
					incremented = true;
					break;
				} else {
					currentKey[wildcardIndices[wildcardIndex]] = 0;
				}
			}
			// Case no incremented keyed index found, application of operator
			// completed, return.
			if (!incremented) {
				return;
			}
		}
	}

	private int computeIndex(int[] indexedKey) {
		int index = 0;
		for (int i = 0; i < numKeys; ++i) {
			if (indexedKey[i] < 0) {
				throw new IllegalArgumentException(
						"all keys must be specified: " + indexedKey);
			}
			index += indexedKey[i] * indexMultiplier[i];
		}
		return index;
	}

	private int computeIndex(Map<INode, LogicalExpression> valueMapping) {
		return computeIndex(computeIndexedKey(valueMapping));
	}

	private int[] computeIndexedKey(
			Map<INode, LogicalExpression> valueMapping) {
		final int[] indexedKey = new int[numKeys];
		for (int i = 0; i < numKeys; ++i) {
			final INode node = headers[i].getNode();
			if (valueMapping.containsKey(node)) {
				indexedKey[i] = headers[i].getIndex(valueMapping.get(node));
			} else {
				indexedKey[i] = -1;
			}
		}
		return indexedKey;
	}

	public static class FactorTable extends Table
			implements Iterable<Pair<Double, IHashVectorImmutable>> {

		private final IHashVectorImmutable[] featureVectors;

		public FactorTable(boolean logSpace, ColumnHeader... headers) {
			super(logSpace, headers);
			this.featureVectors = new IHashVector[super.values.length];
		}

		public FactorTable(FactorTable other) {
			super(other);
			this.featureVectors = new IHashVectorImmutable[super.values.length];
		}

		public FactorTable(FactorTable other, boolean logSpace) {
			super(other, logSpace);
			this.featureVectors = new IHashVectorImmutable[super.values.length];
			System.arraycopy(other.featureVectors, 0, featureVectors, 0,
					featureVectors.length);
		}

		@Override
		public FactorTable clone() {
			final FactorTable table = new FactorTable(this);
			System.arraycopy(super.values, 0, table.values, 0,
					super.values.length);
			System.arraycopy(featureVectors, 0, table.featureVectors, 0,
					featureVectors.length);
			return table;
		}

		@Override
		public FactorTable cloneEmpty() {
			final FactorTable table = new FactorTable(this);
			// Init the array.
			Arrays.setAll(table.values, (i) -> table.logSpace ? 0.0 : 1.0);
			return table;
		}

		public IHashVectorImmutable getFeatures(
				Map<INode, LogicalExpression> valueMapping) {
			return featureVectors[super.computeIndex(valueMapping)];
		}

		@Override
		public Iterator<Pair<Double, IHashVectorImmutable>> iterator() {
			return new Iterator<Pair<Double, IHashVectorImmutable>>() {
				int i = 0;
				final int len = values.length;

				@Override
				public boolean hasNext() {
					return i < len;
				}

				@Override
				public Pair<Double, IHashVectorImmutable> next() {
					final Pair<Double, IHashVectorImmutable> next = Pair
							.of(values[i], featureVectors[i]);
					++i;
					return next;
				}
			};
		}

		@Override
		public void set(Map<INode, LogicalExpression> valueMapping,
				double value) {
			throw new IllegalStateException("missing features");
		}

		public void set(Map<INode, LogicalExpression> valueMapping,
				double value, IHashVectorImmutable features) {
			super.set(valueMapping, value);
			featureVectors[super.computeIndex(valueMapping)] = features;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();

			// Header.
			sb.append("value\t");
			for (int i = 0; i < super.numKeys; ++i) {
				sb.append(super.headers[i].getNode().getExpression());
				if (i + 1 < super.numKeys) {
					sb.append('\t');
				}
			}
			sb.append('\n');

			final Iterator<List<LogicalExpression>> iterator = CollectionUtils
					.cartesianProduct(Arrays.stream(super.headers)
							.map((ColumnHeader header) -> header.getNode()
									.slowGetAssignments())
							.collect(Collectors.toList()))
					.iterator();

			while (iterator.hasNext()) {
				final List<LogicalExpression> keys = iterator.next();
				final Map<INode, LogicalExpression> mapping = new HashMap<>();
				for (int i = 0; i < super.numKeys; ++i) {
					mapping.put(super.headers[i].getNode(), keys.get(i));
				}
				sb.append(get(mapping)).append('\t')
						.append(keys.stream().map(LogicalExpression::toString)
								.collect(Collectors.joining("\t")));
				sb.append('\t').append(getFeatures(mapping));
				if (iterator.hasNext()) {
					sb.append('\n');
				}
			}

			return sb.toString();
		}

	}

	private class DoubleValue {
		double value = 0;
	}

}
