package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Arrays;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;

/**
 * Factor node, {@link IFactor}.
 *
 * @author Yoav Artzi
 */
public class Factor implements IFactor {

	private FactorTable			belief	= null;
	private Edge[]				edges	= new Edge[0];
	private final String		id;
	private final FactorTable	table;

	public Factor(FactorTable table, String id) {
		this.table = table;
		this.id = id;
	}

	@Override
	public void addEdge(Edge edge) {
		// Assert if the edge is not connected to this factor.
		assert edge.getFactor() == this;
		edges = Arrays.copyOf(edges, edges.length + 1);
		edges[edges.length - 1] = edge;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Factor other = (Factor) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}

	@Override
	public FactorTable getBelief() {
		return belief;
	}

	@Override
	public Edge getEdge(int index) {
		return edges[index];
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public FactorTable getTable() {
		return table;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (id == null ? 0 : id.hashCode());
		return result;
	}

	@Override
	public int numEdges() {
		return edges.length;
	}

	@Override
	public void setBelief(FactorTable belief) {
		this.belief = belief;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	@Override
	public String toString(boolean verbose) {
		final StringBuilder sb = new StringBuilder("[ FAC ").append(id)
				.append(" edges=")
				.append(StreamSupport
						.stream(Spliterators.<Edge> spliterator(edges,
								Spliterator.IMMUTABLE), true)
						.map(Edge::toString).collect(Collectors.joining(",")))
				.append("]");

		if (verbose) {
			sb.append("\nFactor table:\n").append(table);
			sb.append("\nBelief:\n").append(belief);
		}

		return sb.toString();
	}

}
