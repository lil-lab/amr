package edu.uw.cs.lil.amr.features;

import java.util.Collections;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.parse.IParseFeatureSet;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class ParseStepSyntaxFeatures<MR> implements
		IParseFeatureSet<Sentence, MR> {

	public static final ILogger	LOG					= LoggerFactory
															.create(ParseStepSyntaxFeatures.class);

	private static final String	FEATURE_TAG			= "SYNRULE";

	private static final long	serialVersionUID	= -513194329499838526L;

	private final double		scale;

	public ParseStepSyntaxFeatures(double scale) {
		this.scale = scale;
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		return Collections.emptySet();
	}

	@Override
	public void setFeatures(IParseStep<MR> parseStep, IHashVector features,
			Sentence dataItem) {
		final int numChildren = parseStep.numChildren();
		if (numChildren == 0) {
			if (!parseStep.getRuleName().equals(
					ILexicalParseStep.LEXICAL_DERIVATION_STEP_RULENAME)) {
				features.add(FEATURE_TAG, parseStep.getRuleName().toString(),
						1.0 * scale);
			}
		} else if (numChildren == 1) {
			features.add(FEATURE_TAG, parseStep.getRuleName().toString(),
					parseStep.getChild(0).getSyntax().toString(), 1.0 * scale);
		} else if (numChildren == 2) {
			features.add(FEATURE_TAG, parseStep.getRuleName().toString(),
					parseStep.getChild(0).getSyntax().toString(), parseStep
							.getChild(1).getSyntax().toString(), 1.0 * scale);
		} else if (numChildren != 0) {
			LOG.error("Invalid number of children");
		}
	}

	public static class Creator<MR> implements
			IResourceObjectCreator<ParseStepSyntaxFeatures<MR>> {

		private String	type;

		public Creator() {
			this("feat.synrule");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public ParseStepSyntaxFeatures<MR> create(Parameters params,
				IResourceRepository repo) {
			return new ParseStepSyntaxFeatures<>(params.getAsDouble("scale",
					1.0));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			// TODO Auto-generated method stub
			return null;
		}

	}

}
