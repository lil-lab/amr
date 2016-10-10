package edu.uw.cs.lil.amr.features;

import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.base.hashvector.KeyArgs;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.data.IDataItem;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.spf.parser.ccg.ILexicalParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.model.parse.IParseFeatureSet;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;

/**
 * Compute features that couple an instance type, one of its modifying relations
 * and the instance type of the modifier entity. These features fire throughout
 * the chart and make sure not to double count.
 * <p>
 * ATTACH features trigger in the chart when creating a relation between two AMR
 * entities. Each feature includes the relation and the typing predicates of
 * both entities. It's only created if it wasn't present before, so it's
 * computed for the children and the root and only the features that are in the
 * root and are new are returned (i.e., not in the children).
 * <p>
 * These features are essential to overcome pruning issues, especially in long
 * sentences. We basically try to learn some selectional preferences in the
 * chart.
 * <p>
 * However, they create issues when interacting with coordination. Certain types
 * of coordination can duplicate an expression multiple times. Each of the
 * duplicated instances potentially triggers multiple ATTACH features. While the
 * attachment itself might have existed before, it now will be counted multiple
 * times. This can lead to weird negative updates. To make things worse, the
 * multiple counting will lead to large negative updates, which will be hard to
 * recover from.
 * <p>
 * To avoid this issue, at least partially, we make these features binary,
 * instead of cumulative. However, this still leads to weird situations. For
 * example, if an attachment appears in both children and was created earlier in
 * the tree, when these children were part of disjoint sub-trees, it will still
 * have a count of 2.0, although the feature should be binary. If the feature
 * will get a high positive score, this can lead to weird structures that will
 * try to use it and we will have the same problem as with coordination,
 * although potentially less common.
 *
 * @author Yoav Artzi
 * @param <DI>
 *            Data item type.
 */
public class AttachmentFeatures<DI extends IDataItem<?>> implements
		IParseFeatureSet<DI, LogicalExpression> {

	public static final ILogger														LOG					= LoggerFactory
																												.create(AttachmentFeatures.class);

	private static final String														FEATURE_TAG			= "ATTACH";

	private static final long														serialVersionUID	= -8748145969638256157L;

	private transient final LoadingCache<LogicalExpression, IHashVectorImmutable>	cache;

	private final int																cacheConcurrencyLevel;

	private final int																cacheSize;

	private final double															scale;

	private final Type																typingPredicateType;

	public AttachmentFeatures(double scale, Type typingPredicateType,
			int cacheSize, int cacheConcurrencyLevel) {
		assert typingPredicateType != null;
		this.cacheSize = cacheSize;
		this.cacheConcurrencyLevel = cacheConcurrencyLevel;
		this.scale = scale;
		this.typingPredicateType = typingPredicateType;
		this.cache = CacheBuilder
				.newBuilder()
				.maximumSize(cacheSize)
				.concurrencyLevel(cacheConcurrencyLevel)
				.build(new CacheLoader<LogicalExpression, IHashVectorImmutable>() {

					@Override
					public IHashVectorImmutable load(LogicalExpression key)
							throws Exception {
						final ExtractFeatures visitor = new ExtractFeatures();
						visitor.visit(key);
						LOG.debug("%s -> %s", key, visitor.features);
						return visitor.features;
					}
				});
	}

	@Override
	public Set<KeyArgs> getDefaultFeatures() {
		return Collections.emptySet();
	}

	@Override
	public void setFeatures(IParseStep<LogicalExpression> step,
			IHashVector feats, DI dataItem) {
		if (step instanceof ILexicalParseStep) {
			// Skip lexical steps. Don't generate attachment features for
			// lexical structures.
			return;
		}

		final LogicalExpression root = step.getRoot().getSemantics();
		if (root == null) {
			return;
		}

		try {
			// Compute the features for the root.
			final IHashVector rootFeatures = HashVectorFactory.create(cache
					.get(root));

			// Deduct the features of each of the children, so we only count new
			// features.
			final int numChildren = step.numChildren();
			for (int i = 0; i < numChildren; ++i) {
				final Category<LogicalExpression> child = step.getChild(i);
				if (child.getSemantics() != null) {
					cache.get(child.getSemantics()).addTimesInto(-1.0,
							rootFeatures);
				}
			}

			// All features must be >= 0. We can get negative features (see the
			// step validation method), so this removes them.
			rootFeatures.applyFunction(d -> d < 0.0 ? 0.0 : d);

			rootFeatures.dropZeros();
			rootFeatures.addTimesInto(1.0, feats);
		} catch (final ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @throws ObjectStreamException
	 */
	protected Object readResolve() throws ObjectStreamException {
		return new AttachmentFeatures<>(scale, typingPredicateType, cacheSize,
				cacheConcurrencyLevel);
	}

	public static class Creator<DI extends IDataItem<?>> implements
			IResourceObjectCreator<AttachmentFeatures<DI>> {

		private String	type;

		public Creator() {
			this("feat.amr.relations");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AttachmentFeatures<DI> create(Parameters params,
				IResourceRepository repo) {
			return new AttachmentFeatures<>(params.getAsDouble("scale", 1.0),
					LogicLanguageServices.getTypeRepository()
							.getTypeCreateIfNeeded(
									params.get("predicateType", "<e,t>")),
					params.getAsInteger("cache", 1000), params.getAsInteger(
							"cacheConcurrency", Runtime.getRuntime()
									.availableProcessors()));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, AttachmentFeatures.class)
					.setDescription("")
					.addParam("scale", Double.class,
							"Scaling factor (default: 1.0)")
					.addParam("type", Type.class,
							"The semantic type of typing predicate (default: <e,t>)")
					.addParam("cache", Integer.class,
							"Cache size (default: 1000)").build();
		}

	}

	private class ExtractFeatures implements ILogicalExpressionVisitor {
		private final IHashVector			features				= HashVectorFactory
																			.create();
		/**
		 * Store a list of typing predicate to allow proper handling of
		 * coordination.
		 */
		private final List<LogicalConstant>	nestedTypingConstants	= new LinkedList<>();

		@Override
		public void visit(Lambda lambda) {
			lambda.getBody().accept(this);
		}

		@Override
		public void visit(Literal literal) {
			final int len = literal.numArgs();
			if (literal.getPredicate().equals(
					LogicLanguageServices.getConjunctionPredicate())) {
				visitConjunction(literal);
			} else {
				for (int i = 0; i < len; ++i) {
					literal.getArg(i).accept(this);
				}
				literal.getPredicate().accept(this);
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			// A typing constant is either a constant that without a complex
			// type (e.g., txt, i) or a constant with the pre-specified complex
			// (predicate) type.
			if (logicalConstant.getType().equals(typingPredicateType)
					|| !logicalConstant.getType().isComplex()) {
				// There are no nested entities at this level, so clear the
				// list.
				nestedTypingConstants.clear();
				// Add the current typing constant to return to above layers.
				nestedTypingConstants.add(logicalConstant);
			}
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Substituting a functional variable may change how elements are
			// relating to each other and the features computed, so we reset the
			// current typing predicate.
			if (variable.getType().isComplex()) {
				nestedTypingConstants.clear();
			}
		}

		private void visitConjunction(Literal literalBody) {
			final int len = literalBody.numArgs();
			LogicalConstant typingPredicate = null;
			// Two synchronized lists. The relations list includes binary
			// relations, where the arg2s list includes the typing constants of
			// the arg2 fo each relation.
			final List<LogicalConstant> relations = new ArrayList<>(len);
			final List<LogicalConstant> arg2s = new ArrayList<>(len);
			for (int i = 0; i < len; ++i) {
				// Visit the argument to get nested entities. First, clear the
				// current typing constant (not clear that this is really
				// necessary, but good for safety).
				nestedTypingConstants.clear();
				final LogicalExpression arg = literalBody.getArg(i);
				arg.accept(this);
				// At this point, if there is a nested entity/entities in this
				// argument, the list of typing constants includes them. There
				// could be multiple entities in the case of an AMR
				// coordination.
				if (!nestedTypingConstants.isEmpty()) {
					if (arg instanceof Literal) {
						if (((Literal) arg).numArgs() == 2
								&& ((Literal) arg).getArg(0) instanceof Variable
								&& ((Literal) arg).getPredicate() instanceof LogicalConstant) {
							// Case the argument is a relation between an entity
							// and an entity represented by a constant.
							for (final LogicalConstant constant : nestedTypingConstants) {
								relations.add((LogicalConstant) ((Literal) arg)
										.getPredicate());
								arg2s.add(constant);
							}
						} else if (((Literal) arg).numArgs() == 1
								&& ((Literal) arg).getArg(0) instanceof Variable) {
							// Case the argument is itself a unary typing
							// literal for this level.
							assert typingPredicate == null : "There's a single typing predicate for each entity, so it must be null if we just found one";
							assert nestedTypingConstants.size() == 1;
							typingPredicate = nestedTypingConstants.get(0);
						}
					}
					// Clear the list of nested typing constants.
					nestedTypingConstants.clear();
				}
			}

			assert relations.size() == arg2s.size() : "These two lists must be in sync";

			// Case this is an AMR coordination, propagate the typing nested
			// typing constants and return.
			if (typingPredicate != null
					&& CoordinationServices
							.isAmrCoordinationPredicate(typingPredicate)) {
				nestedTypingConstants.addAll(arg2s);
				return;
			}

			// Create all bigram relation-arg2 features. These don't require the
			// typing predicate of the current level.
			final int relPairLen = relations.size();
			for (int i = 0; i < relPairLen; ++i) {
				// If this a coordinator, ignore this pair.
				if (!CoordinationServices.isCOpPredicate(relations.get(i))) {
					// Binary (0/1) feature.
					features.set(FEATURE_TAG, relations.get(i).getBaseName(),
							arg2s.get(i).getType().isComplex() ? arg2s.get(i)
									.getBaseName() : arg2s.get(i).getType()
									.toString(), 1.0 * scale);
				}
			}

			// Update the global typing predicate to propagate the type of
			// this instance. If the typing predicate of this literal is an AMR
			// conjunction predicate, propagate the types of the coordinated
			// elements.
			if (typingPredicate != null) {
				// Create the features.
				for (int i = 0; i < relPairLen; ++i) {
					// Binary (0/1) bigram arg1-relation feature.
					features.set(FEATURE_TAG, typingPredicate.getBaseName(),
							relations.get(i).getBaseName(), 1.0 * scale);

					// Binary (0/1) feature. This is a trigram features
					// that captures arg1-relation-arg2 occurrence. If the
					// type of arg2 is not complex, just use the type rather
					// than th base name.
					features.set(FEATURE_TAG, typingPredicate.getBaseName(),
							relations.get(i).getBaseName(), arg2s.get(i)
									.getType().isComplex() ? arg2s.get(i)
									.getBaseName() : arg2s.get(i).getType()
									.toString(), 1.0 * scale);
				}
				nestedTypingConstants.add(typingPredicate);
			}
		}
	}

}
