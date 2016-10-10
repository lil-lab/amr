package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.util.ArrayList;
import java.util.List;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.BinaryRuleSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IBinaryParseRule;

public class CoordinationRuleSet extends BinaryRuleSet<LogicalExpression> {

	private CoordinationRuleSet(
			List<IBinaryParseRule<LogicalExpression>> rules) {
		super(rules);
	}

	public static CoordinationRuleSet create(
			ICategoryServices<LogicalExpression> categoryServices) {

		final List<IBinaryParseRule<LogicalExpression>> rules = new ArrayList<>(
				3);
		rules.add(new CoordinationC1Rule());
		rules.add(new CoordinationC2Rule());
		final CoordinationCX1Rule cx1 = new CoordinationCX1Rule(
				categoryServices);
		rules.add(cx1);
		// TODO Decide if to disable CX2 -- currently disabled, if this option
		// is kept, need to clean up the code
		// final CoordinationCX2Rule cx2 = new CoordinationCX2Rule(
		// categoryServices);
		// rules.add(cx2);
		final CoordinationCX3Rule cx3 = new CoordinationCX3Rule(
				categoryServices);
		rules.add(cx3);
		final CoordinationCX4Rule cx4 = new CoordinationCX4Rule(
				categoryServices);
		rules.add(cx4);
		rules.add(new CoordinationCXRaisedApplyBackward(cx1, categoryServices));
		// rules.add(new CoordinationCXRaisedApplyBackward(cx2,
		// categoryServices));
		rules.add(new CoordinationCXRaisedApplyBackward(cx3, categoryServices));
		rules.add(new CoordinationCXRaisedApplyBackward(cx4, categoryServices));
		rules.add(new CoordinationCXRaisedApplyForward(cx1, categoryServices));
		// rules.add(new CoordinationCXRaisedApplyForward(cx2,
		// categoryServices));
		rules.add(new CoordinationCXRaisedApplyForward(cx3, categoryServices));
		rules.add(new CoordinationCXRaisedApplyForward(cx4, categoryServices));
		return new CoordinationRuleSet(rules);
	}

	public static class Creator
			implements IResourceObjectCreator<CoordinationRuleSet> {

		private final String type;

		public Creator() {
			this("rule.coordination.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public CoordinationRuleSet create(Parameters params,
				IResourceRepository repo) {
			return CoordinationRuleSet.create(repo
					.get(ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type, CoordinationRuleSet.class)
					.setDescription("AMR coordination rule set")
					.addParam("conj", String.class, "Conjunction label.")
					.addParam("disj", String.class, "Disjunction label")
					.addParam("conjInstance", LogicalConstant.class,
							"Instance typing predicate for conjunctions.")
					.addParam("disjInstance", LogicalConstant.class,
							"Instance typing predicate for disjunctions.")
					.build();
		}

	}

}
