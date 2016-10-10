package edu.uw.cs.lil.amr.parser.constraints;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.normalform.INormalFormConstraint;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.IArrayRuleNameSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;

/**
 * Constraint to allow pre-specified unary rules only at the lexical level.
 *
 * @author Yoav Artzi
 */
public class UnaryLexicalConstraint implements INormalFormConstraint {

	private static final long			serialVersionUID	= 7324041897181242195L;
	private final Set<UnaryRuleName>	constrainedRules;

	public UnaryLexicalConstraint(Set<UnaryRuleName> constrainedRules) {
		this.constrainedRules = ObjectSets
				.unmodifiable(new ObjectOpenHashSet<>(constrainedRules));
	}

	@Override
	public boolean isValid(IArrayRuleNameSet leftGeneratingRules,
			IArrayRuleNameSet rightGeneratingRules, RuleName consideredRule) {
		// No binary constants provided.
		return true;
	}

	@Override
	public boolean isValid(IArrayRuleNameSet generatingRules,
			RuleName consideredRule) {

		if (constrainedRules.contains(consideredRule)) {
			for (int i = 0; i < generatingRules.numRuleNames(); ++i) {
				if (!generatingRules.getRuleName(i).equals(
						ILexicalParseStep.LEXICAL_DERIVATION_STEP_RULENAME)) {
					return false;
				}
			}
		}

		return true;
	}

	public static class Creator implements
			IResourceObjectCreator<UnaryLexicalConstraint> {

		private final String	type;

		public Creator() {
			this("constraint.unary.lex");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public UnaryLexicalConstraint create(Parameters params,
				IResourceRepository repo) {
			return new UnaryLexicalConstraint(params.getSplit("rules").stream()
					.map(s -> UnaryRuleName.create(s))
					.collect(Collectors.toSet()));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, UnaryLexicalConstraint.class)
					.addParam("rules", UnaryRuleName.class,
							"The names of unary rules to constraint only the lexical level")
					.build();
		}

	}

}
