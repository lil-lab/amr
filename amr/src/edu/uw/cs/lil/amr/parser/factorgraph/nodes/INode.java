package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.List;
import java.util.Set;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.uw.cs.lil.amr.parser.factorgraph.table.ColumnHeader;
import edu.uw.cs.lil.amr.parser.factorgraph.table.Table;

/**
 * Factor graph node.
 *
 * @author Yoav Artzi
 */
public interface INode extends IBaseNode {

	/**
	 * Add an edge connecting a neighboring factor.
	 */
	void addEdge(Edge edge);

	/**
	 * Get assignment. Index can be from 0 to {@link #numAssignments()}-1.
	 */
	LogicalExpression getAssignment(int index);

	/**
	 * Get the log-belief (marginal posterior) of this node.
	 */
	Table getBelief();

	/**
	 * The corresponding column header to this node.
	 */
	ColumnHeader getColumnHeader();

	/**
	 * Get the {@link Edge} with the given index. The index may be between 0 and
	 * {@link #numEdges()}-1.
	 */
	Edge getEdge(int index);

	/**
	 * Get the most likely assignments according to the set belief and their
	 * marginal probability.
	 */
	Pair<Set<LogicalExpression>, Double> getMaxAssignments();

	/**
	 * The number of possible assignments.
	 */
	int numAssignments();

	/**
	 * The number of {@link Edge}s connected to this node.
	 */
	int numEdges();

	/**
	 * Set the belief table for this node.
	 */
	void setBelief(Table belief);

	/**
	 * Inefficient readonly access to all the assignments.
	 */
	List<LogicalExpression> slowGetAssignments();

}
