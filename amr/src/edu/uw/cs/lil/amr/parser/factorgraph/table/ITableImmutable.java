package edu.uw.cs.lil.amr.parser.factorgraph.table;

import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.INode;

public interface ITableImmutable {

	double get(Map<INode, LogicalExpression> valueMapping);

	double get(MappingPair... mappingPairs);

	int size();

}
