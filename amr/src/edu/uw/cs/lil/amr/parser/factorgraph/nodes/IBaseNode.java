package edu.uw.cs.lil.amr.parser.factorgraph.nodes;

import java.util.Set;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.visitor.IFactorGraphVisitor;

/**
 * Basic factor graph node. This node provides a proxy to a sub-expression in
 * the logical form.
 *
 * @author Yoav Artzi
 */
public interface IBaseNode {

	void accept(IFactorGraphVisitor visitor);

	@Override
	boolean equals(Object obj);

	Set<IBaseNode> getChildren();

	LogicalExpression getExpression();

	int getId();

	IBaseNode getParent();

	@Override
	int hashCode();

	String toString(boolean verbose);

}
