package edu.uw.cs.lil.amr.features;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.ComplexSyntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.parse.IParseFeatureSet;
import edu.uw.cs.lil.amr.data.AMRMeta;

/**
 * Features to cross the syntactic attributes of a lexical entry and the POS
 * tags of the words it's applied to.
 *
 * @author Yoav Artzi
 */
public class AttributePOSTagFeatures implements
		IParseFeatureSet<SituatedSentence<AMRMeta>, LogicalExpression> {

	private static final String	DEFAULT_TAG			= "ATTRIBPOS";
	private static final long	serialVersionUID	= 1576342328833682626L;
	private final double		scale;
	private final String		tag;

	public AttributePOSTagFeatures(String tag, double scale) {
		this.tag = tag;
		this.scale = scale;
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		return Collections.emptySet();
	}

	@Override
	public void setFeatures(IParseStep<LogicalExpression> step,
			IHashVector feats, SituatedSentence<AMRMeta> dataItem) {
		if (step instanceof ILexicalParseStep
				&& ((ILexicalParseStep<LogicalExpression>) step)
						.getLexicalEntry() instanceof FactoredLexicalEntry) {
			final FactoredLexicalEntry entry = (FactoredLexicalEntry) ((ILexicalParseStep<LogicalExpression>) step)
					.getLexicalEntry();
			final String posSeq = dataItem.getState().getTags()
					.sub(step.getStart(), step.getEnd() + 1).toString("+");

			// Concrete attribute features.
			if (entry.getCategory().getSyntax() instanceof ComplexSyntax) {
				feats.add(tag, "complex", posSeq, 1.0);
			} else {
				final List<String> attributes = entry.getLexeme()
						.getAttributes();
				if (attributes.isEmpty()) {
					feats.add(tag, "noattrib", posSeq, 1.0);
				} else {
					final String attributeSeq = attributes.stream().sorted()
							.collect(Collectors.joining("+"));
					feats.add(tag, attributeSeq, posSeq, 1.0 * scale);
				}
			}

			// Agreement variable features.
			if (entry.getCategory().getSyntax().hasAttributeVariable()) {
				feats.add(tag, "var", posSeq, 1.0);
			}
		}
	}

	public static class Creator
			implements IResourceObjectCreator<AttributePOSTagFeatures> {

		private final String type;

		public Creator() {
			this("feat.attribute.pos");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AttributePOSTagFeatures create(Parameters params,
				IResourceRepository repo) {
			return new AttributePOSTagFeatures(params.get("tag", DEFAULT_TAG),
					params.getAsDouble("scale", 1.0));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, AttributePOSTagFeatures.class)
					.setDescription(
							"Features to cross the syntactic attributes of a lexical entry and the POS tags of the words it's applied to.")
					.addParam("scale", Double.class,
							"Scaling factor (default: 1.0)")
					.addParam("tag", String.class,
							"Feature set tag (default: " + DEFAULT_TAG + ")")
					.build();
		}

	}

}
