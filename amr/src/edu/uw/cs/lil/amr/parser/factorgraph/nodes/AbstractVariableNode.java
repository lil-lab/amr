package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.uw.cs.lil.amr.parser.factorgraph.table.ColumnHeader;
import edu.uw.cs.lil.amr.parser.factorgraph.table.MappingPair;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table;

public abstract class AbstractVariableNode extends AbstractDummyNode implements
		INode {

	private final List<LogicalExpression>	assignmentList;

	private final LogicalExpression[]		assignments;

	private Table							belief	= null;

	private Edge[]							edges	= new Edge[0];

	private final ColumnHeader				header;

	public AbstractVariableNode(int id, Set<AbstractDummyNode> children,
			LogicalExpression[] assignments) {
		super(id, children);
		this.assignmentList = Collections.unmodifiableList(Arrays
				.asList(assignments));
		this.assignments = assignments;
		assert new HashSet<>(assignmentList).size() == assignments.length : "Assignments are not unique";
		this.header = new ColumnHeader(this);
	}

	/**
	 * Constructing factors can be done in parallel, so this method is
	 * synchronized to allow adding edges from multiple threads.
	 */
	@Override
	public synchronized void addEdge(Edge edge) {
		// Verify the edge is linked to this node.
		assert edge.getVariable() == this;
		edges = Arrays.copyOf(edges, edges.length + 1);
		edges[edges.length - 1] = edge;
	}

	@Override
	public LogicalExpression getAssignment(int index) {
		return assignments[index];
	}

	@Override
	public Table getBelief() {
		return belief;
	}

	@Override
	public ColumnHeader getColumnHeader() {
		return header;
	}

	@Override
	public Edge getEdge(int index) {
		return edges[index];
	}

	@Override
	public Pair<Set<LogicalExpression>, Double> getMaxAssignments() {
		if (belief == null) {
			throw new IllegalStateException(
					"Belief not set, can't get max assignments: " + this);
		} else if (assignments.length == 1) {
			final Set<LogicalExpression> maxAssignments = new HashSet<>();
			maxAssignments.add(assignments[0]);
			return Pair.of(maxAssignments, 0.0);
		} else {
			double max = -Double.MAX_VALUE;
			final Set<LogicalExpression> argmax = new HashSet<>();
			for (final LogicalExpression assignment : assignments) {
				final double score = belief.get(MappingPair
						.of(this, assignment));
				if (score == max) {
					argmax.add(assignment);
				} else if (score > max) {
					max = score;
					argmax.clear();
					argmax.add(assignment);
				}
			}
			return Pair.of(argmax, max);
		}
	}

	@Override
	public int numAssignments() {
		return assignments.length;
	}

	@Override
	public int numEdges() {
		return edges.length;
	}

	@Override
	public void setBelief(Table belief) {
		this.belief = belief;
	}

	@Override
	public List<LogicalExpression> slowGetAssignments() {
		return assignmentList;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	@Override
	public String toString(boolean verbose) {
		final StringBuilder sb = new StringBuilder("[ VAR ")
				.append(internalToString());
		if (edges.length > 0) {
			sb.append(" edges=").append(
					StreamSupport
							.stream(Spliterators.<Edge> spliterator(edges,
									Spliterator.IMMUTABLE), true)
							.map(Edge::toString)
							.collect(Collectors.joining(",")));
		}
		sb.append("]");

		if (verbose) {
			sb.append("\nBelief:\n").append(belief);
		}

		return sb.toString();
	}

}
