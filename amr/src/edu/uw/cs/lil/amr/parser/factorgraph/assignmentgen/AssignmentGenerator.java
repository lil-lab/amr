package edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;

/**
 * Mapping of logical expressions to all potential assignments in a
 * {@link FactorGraph}.
 *
 * @author Yoav Artzi
 */
public class AssignmentGenerator implements
		Function<LogicalExpression, Set<LogicalExpression>> {

	private final Map<LogicalConstant, Set<LogicalExpression>>	assignments;

	private AssignmentGenerator(
			Map<LogicalConstant, Set<LogicalExpression>> assignments) {
		this.assignments = assignments;
	}

	@Override
	public Set<LogicalExpression> apply(LogicalExpression exp) {
		if (assignments.containsKey(exp)) {
			return assignments.get(exp);
		} else if (exp instanceof LogicalConstant) {
			return AMRServices.getSpecMapping().getAssignments(
					(LogicalConstant) exp);
		} else {
			return Collections.emptySet();
		}
	}

	public static class Builder {

		private final Map<LogicalConstant, Set<LogicalExpression>>	assignments;

		public Builder(Map<LogicalConstant, Set<LogicalExpression>> assignments) {
			this.assignments = assignments;
		}

		public AssignmentGenerator build() {
			return new AssignmentGenerator(assignments);
		}

	}

}
