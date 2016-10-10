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
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IOverloadedParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.parse.IParseFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.UnaryRuleName;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Features to pair unary shifting operations and the semantic type of the root
 * instance, if such exist.
 *
 * @author Yoav Artzi
 * @param <DI>
 *            Inference data item.
 */
public class SemanticShiftingFeatureSet<DI extends IDataItem<?>> implements
		IParseFeatureSet<DI, LogicalExpression> {

	private static final String	DEFAULT_TAG			= "SHIFTSEM";
	private static final long	serialVersionUID	= -8435882657427038868L;
	private final double		scale;

	private final String		tag;

	public SemanticShiftingFeatureSet(String tag, double scale) {
		this.tag = tag;
		this.scale = scale;
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		return Collections.emptySet();
	}

	@Override
	public void setFeatures(IParseStep<LogicalExpression> step,
			IHashVector feats, DI dataItem) {
		if (step instanceof IOverloadedParseStep) {
			final UnaryRuleName rule = ((IOverloadedParseStep<LogicalExpression>) step)
					.getRuleName().getUnaryRule();
			final LogicalExpression semantics = ((IOverloadedParseStep<LogicalExpression>) step)
					.getIntermediate().getSemantics();
			if (semantics != null) {
				final LogicalConstant instanceType;
				if (AMRServices.isSkolemTerm(semantics)) {
					instanceType = AMRServices
							.getTypingPredicate((Literal) semantics);
				} else if (AMRServices.isSkolemTermBody(semantics)) {
					instanceType = AMRServices
							.getTypingPredicate((Lambda) semantics);
				} else {
					return;
				}
				if (instanceType != null) {
					feats.set(tag, rule.toString(), instanceType.getBaseName(),
							1.0 * scale);
				}
			}
		}
	}

	public static class Creator<DI extends IDataItem<?>> implements
			IResourceObjectCreator<SemanticShiftingFeatureSet<DI>> {

		private String	type;

		public Creator() {
			this("feat.rule.shift.semantics");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SemanticShiftingFeatureSet<DI> create(Parameters params,
				IResourceRepository repo) {
			return new SemanticShiftingFeatureSet<>(params.get("tag",
					DEFAULT_TAG), params.getAsDouble("scale", 1.0));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, SemanticShiftingFeatureSet.class)
					.addParam("scale", Double.class,
							"Scaling factor (default:1.0)")
					.addParam("tag", String.class,
							"Feature set tag (default: " + DEFAULT_TAG + ")")
					.setDescription(
							"Features to pair unary shifting operations and the semantic type of the root instance, if such exist")
					.build();
		}

	}
}
