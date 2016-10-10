package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import edu.uw.cs.lil.amr.parser.factorgraph.table.Table;

/**
 * Factor graph edge connecting a {@link IFactor} and a {@link INode}.
 *
 * @author Yoav Artzi
 */
public class Edge {

	private final IFactor	factor;
	private Table			toFactorMessage;
	private Table			toNodeMessage;
	private final INode		variable;

	public Edge(INode variable, IFactor factor, boolean logSpace) {
		this.variable = variable;
		this.factor = factor;
		this.toNodeMessage = new Table(logSpace, variable.getColumnHeader());
		this.toFactorMessage = new Table(logSpace, variable.getColumnHeader());
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
		final Edge other = (Edge) obj;
		if (factor == null) {
			if (other.factor != null) {
				return false;
			}
		} else if (!factor.equals(other.factor)) {
			return false;
		}
		if (variable == null) {
			if (other.variable != null) {
				return false;
			}
		} else if (!variable.equals(other.variable)) {
			return false;
		}
		return true;
	}

	public IFactor getFactor() {
		return factor;
	}

	public Table getToFactorMessage() {
		return toFactorMessage;
	}

	public Table getToVariableMessage() {
		return toNodeMessage;
	}

	public INode getVariable() {
		return variable;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (factor == null ? 0 : factor.hashCode());
		result = prime * result + (variable == null ? 0 : variable.hashCode());
		return result;
	}

	public void setToFactorMessage(Table toFactorMessage) {
		this.toFactorMessage = toFactorMessage;
	}

	public void setToVariableMessage(Table toVariableMessage) {
		this.toNodeMessage = toVariableMessage;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean verbose) {
		final StringBuilder sb = new StringBuilder(Integer.toString(variable
				.getId())).append("--").append(factor.getId());

		if (verbose) {
			sb.append("\nFactor-to-node message:\n");
			sb.append(toNodeMessage.toString());
			sb.append("\nNode-to-factor message:\n");
			sb.append(toFactorMessage.toString());
		}

		return sb.toString();
	}

}
