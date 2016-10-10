package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import edu.uw.cs.lil.amr.parser.factorgraph.table.Table.FactorTable;

/**
 * Factor graph factor. A factor is connected via edges to {@link INode} s and
 * contains a table of weights and features.
 *
 * @author Yoav Artzi
 */
public interface IFactor {

	void addEdge(Edge edge);

	FactorTable getBelief();

	Edge getEdge(int index);

	String getId();

	FactorTable getTable();

	int numEdges();

	void setBelief(FactorTable belief);

	String toString(boolean verbose);
}
