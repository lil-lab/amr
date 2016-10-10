package edu.uw.cs.lil.amr.features;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.IDataItem;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.model.lexical.AbstractLexicalFeatureSet;
import edu.cornell.cs.nlp.utils.function.PredicateUtils;
import edu.uw.cs.lil.amr.exec.SloppyLexicon;

/**
 * Feature to trigger for using a lexical entry created using the sloppy
 * lexicon.
 *
 * @author Yoav Artzi
 * @param <DI>
 *            Sample.
 * @param <MR>
 *            Meaning representation.
 */
public class SloppyLexiconFeatures<DI extends IDataItem<?>, MR>
		extends AbstractLexicalFeatureSet<DI, MR> {

	private final static String	DEFAULT_TAG			= "SLOPPYLEX";
	private static final long	serialVersionUID	= -7832309700174617972L;

	public SloppyLexiconFeatures(String tag,
			Predicate<LexicalEntry<MR>> ignoreFilter,
			boolean computeSyntaxAttributeFeatures) {
		super(ignoreFilter, computeSyntaxAttributeFeatures, tag);
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		// No default features.
		return Collections.emptySet();
	}

	@Override
	protected boolean doAddEntry(LexicalEntry<MR> entry,
			IHashVector parametersVector) {
		// Nothing to do.
		return false;
	}

	@Override
	protected void doSetFeatures(LexicalEntry<MR> entry, IHashVector features) {
		super.doSetFeatures(entry, features);
		if (Boolean.TRUE.toString()
				.equals(entry.getProperty(SloppyLexicon.ENTRY_MARKER))) {
			features.add(featureTag, 1.0);
		}
	}

	public static class Creator<DI extends IDataItem<?>, MR>
			implements IResourceObjectCreator<SloppyLexiconFeatures<DI, MR>> {

		private String type;

		public Creator() {
			this("feat.lex.sloppy");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SloppyLexiconFeatures<DI, MR> create(Parameters params,
				IResourceRepository repo) {
			return new SloppyLexiconFeatures<>(params.get("tag", DEFAULT_TAG),
					PredicateUtils.alwaysTrue(),
					params.getAsBoolean("syntaxAttrib", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, SloppyLexiconFeatures.class)
					.addParam("tag", String.class,
							"Feature tag (default: " + DEFAULT_TAG + ")")
					.addParam("syntaxAttrib", Boolean.class,
							"Compute syntax attribute features (default: false)")
					.setDescription(
							"Feature to trigger for using a lexical entry created using the sloppy lexicon")
					.build();
		}

	}

}
