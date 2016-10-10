package edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen;

import java.util.Set;
import java.util.function.Function;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

public interface IAssignmentGeneratorFactory {

	Function<LogicalExpression, Set<LogicalExpression>> create(
			LogicalExpression exp);

}
