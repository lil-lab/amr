package edu.uw.cs.lil.amr.features;

import java.util.Collections;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.data.IDataItem;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.parse.IParseFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.OverloadedRuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;

/**
 * A single feature that triggers when a shifting rule is used.
 *
 * @author Yoav Artzi
 * @param <DI>
 *            Data item type.
 * @param <MR>
 *            Meaning representation.
 */
public class ShiftingRuleFeatureSet<DI extends IDataItem<?>, MR> implements
		IParseFeatureSet<DI, MR> {

	private static final String	DEFAULT_TAG			= "SHIFT";

	private static final long	serialVersionUID	= -6185558632850179871L;

	private final double		scale;
	private final String		tag;

	public ShiftingRuleFeatureSet(double scale, String tag) {
		this.scale = scale;
		this.tag = tag;
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		return Collections.emptySet();
	}

	@Override
	public void setFeatures(IParseStep<MR> parseStep, IHashVector feats,
			DI dataItem) {
		final RuleName rule = parseStep.getRuleName();
		if (rule instanceof OverloadedRuleName) {
			final OverloadedRuleName overloaded = (OverloadedRuleName) rule;
			feats.add(tag, overloaded.getUnaryRule().toString(), 1.0 * scale);
		}
	}

	public static class Creator<DI extends IDataItem<?>, MR> implements
			IResourceObjectCreator<ShiftingRuleFeatureSet<DI, MR>> {

		private String	type;

		public Creator() {
			this("feat.rule.shift");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public ShiftingRuleFeatureSet<DI, MR> create(Parameters params,
				IResourceRepository repo) {
			return new ShiftingRuleFeatureSet<>(
					params.getAsDouble("scale", 1.0), params.get("tag",
							DEFAULT_TAG));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, ShiftingRuleFeatureSet.class)
					.setDescription(
							"A single feature that triggers when a shifting rule is used")
					.addParam("scale", Double.class,
							"Scaling factor (default:1.0)")
					.addParam("tag", String.class,
							"Feature set tag (default: " + DEFAULT_TAG + ")")
					.build();
		}

	}

}
