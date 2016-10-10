package edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

/**
 * Factory to create stub assignment generators.
 *
 * @author Yoav Artzi
 */
public class AssignmentGeneratorFactoryStub implements Serializable,
		IAssignmentGeneratorFactory {

	private static final long	serialVersionUID	= 5508614184814179689L;

	@Override
	public Function<LogicalExpression, Set<LogicalExpression>> create(
			LogicalExpression exp) {
		return (e) -> Collections.emptySet();
	}

	public static class Creator implements
			IResourceObjectCreator<AssignmentGeneratorFactoryStub> {

		private final String	type;

		public Creator() {
			this("fg.assignment.factory.stub");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AssignmentGeneratorFactoryStub create(Parameters params,
				IResourceRepository repo) {
			return new AssignmentGeneratorFactoryStub();
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type,
					AssignmentGeneratorFactoryStub.class).setDescription(
					"Factory for stub assignment generators for factor graphs")
					.build();
		}

	}

}
