package edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetSkolemIds;

/**
 * Factory to create {@link AssignmentGenerator}.
 *
 * @author Yoav Artzi
 */
public class AssignmentGeneratorFactory implements Serializable,
		IAssignmentGeneratorFactory {

	private static final long	serialVersionUID	= -8080173287676160124L;

	@Override
	public AssignmentGenerator create(LogicalExpression exp) {

		// Get all skolem IDs, and map them to the reference placeholder
		// constant.
		final Set<LogicalExpression> ids = new HashSet<>();
		ids.addAll(GetSkolemIds.of(exp));

		final Map<LogicalConstant, Set<LogicalExpression>> allAssignments = new HashMap<>();
		allAssignments.put(SkolemServices.getIdPlaceholder(), ids);

		return new AssignmentGenerator.Builder(allAssignments).build();
	}

	public static class Creator implements
			IResourceObjectCreator<AssignmentGeneratorFactory> {

		private final String	type;

		public Creator() {
			this("fg.assignment.factory");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AssignmentGeneratorFactory create(Parameters params,
				IResourceRepository repo) {
			return new AssignmentGeneratorFactory();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type,
					AssignmentGeneratorFactory.class).setDescription(
					"Factory for assignment generators for factor graphs")
					.build();
		}

	}

}
