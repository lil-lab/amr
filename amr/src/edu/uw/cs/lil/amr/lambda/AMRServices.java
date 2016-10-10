package edu.uw.cs.lil.amr.lambda;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax.SimpleSyntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.ILogicalExpressionPrinter;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.LogicalExpressionToIndentedString;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.StripSkolemIds;
import edu.cornell.cs.nlp.spf.mr.language.type.ComplexType;
import edu.cornell.cs.nlp.spf.mr.language.type.RecursiveComplexType;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.uw.cs.lil.amr.ner.IllinoisNERWrapper;
import edu.uw.cs.lil.amr.ner.RecognizedNamedEntity;
import edu.uw.cs.lil.amr.util.propbank.PropBank;
import edu.uw.cs.lil.amr.util.propbank.PropBankFrame;
import edu.uw.cs.lil.lemma.lemmatizer.ILemmatizer;
import edu.uw.cs.lil.lemma.lemmatizer.UnionLemmatizer;
import edu.uw.cs.lil.lemma.lemmatizer.WordNetLemmatizer;
import jregex.Matcher;
import jregex.Pattern;

/**
 * Various AMR services that are used throughout the system. This service class
 * must be initialized after {@link LogicLanguageServices}.
 *
 * @author Yoav Artzi
 */
public class AMRServices {

	/**
	 * Complete e-typed AMR.
	 */
	public static final SimpleSyntax					AMR						= new SimpleSyntax(
			"A");

	/**
	 * Syntactic category for numbers.
	 */
	public static final SimpleSyntax					I						= new SimpleSyntax(
			"I");

	/**
	 * Syntactic category for a skolem ID.
	 */
	public static final SimpleSyntax					ID						= new SimpleSyntax(
			"ID");

	/**
	 * Syntactic category for a skolemized keyword.
	 */
	public static final SimpleSyntax					KEY						= new SimpleSyntax(
			"KEY");

	/**
	 * Syntactic category for strings copied as is from the sentence.
	 */
	public static final SimpleSyntax					TXT						= new SimpleSyntax(
			"TXT");
	private static final Pattern						FRAME_PREDICATE_REGEX	= new Pattern(
			"({pred}.+)-\\d+");

	private static AMRServices							INSTANCE;

	private static final String							INVERSE_RELATION_SUFFIX	= "-of";

	private final ILogicalExpressionPrinter				amrPrinter				= new LogicalExpressionToIndentedString.Printer(
			"  ");

	private final LogicalConstant						dummyEntity;

	private final ILemmatizer							lemmatizer;

	private final SpecificationMapping					mapping;

	private final IllinoisNERWrapper					namedEntityRecognizer;

	private final LogicalConstant						nameInstancePredicate;

	private final String								opPredicatePrefix;

	private final PropBank								propBank;

	private final String								refPredicateBaseName;
	/**
	 * Caching creation of reference predicates.
	 */
	private final Map<Type, LogicalConstant>			refPredicatesCache		= new ConcurrentHashMap<>();

	private final String								skolemPredicateBaseName;

	/**
	 * Caching creation of skolem predicates.
	 */
	private transient final Map<Type, LogicalConstant>	skolemPredicatesCache	= new ConcurrentHashMap<>();

	private final MaxentTagger							tagger;

	private final Type									textType;

	private final Type									typingPredicateType;

	private AMRServices(String skolemPredicateBaseName, Type textType,
			String refPredicateBaseName, SpecificationMapping mapping,
			File stanfordModelFile, String opPredicatePrefix,
			LogicalConstant dummyEntity, LogicalConstant nameInstancePredicate,
			Type typingPredicateType, IllinoisNERWrapper namedEntityRecognizer,
			File propBankDir) throws IOException {
		this.opPredicatePrefix = opPredicatePrefix;
		this.dummyEntity = dummyEntity;
		this.nameInstancePredicate = nameInstancePredicate;
		this.typingPredicateType = typingPredicateType;
		this.namedEntityRecognizer = namedEntityRecognizer;
		// Add a lemmatizer that simply returns the lower-cased word.
		this.lemmatizer = new UnionLemmatizer(new WordNetLemmatizer(),
				word -> SetUtils.createSingleton(word.toLowerCase()));
		this.skolemPredicateBaseName = skolemPredicateBaseName;
		this.textType = textType;
		this.refPredicateBaseName = refPredicateBaseName;
		this.mapping = mapping;
		this.tagger = stanfordModelFile == null ? null
				: new MaxentTagger(stanfordModelFile.getAbsolutePath());
		this.propBank = propBankDir == null ? null : new PropBank(propBankDir);
	}

	/**
	 * Create a reference predicate, e.g., ref:<id,e>.
	 */
	public static LogicalConstant createRefPredicate(Type type) {
		final LogicalConstant cached = INSTANCE.refPredicatesCache.get(type);

		if (cached != null) {
			return cached;
		}

		final ComplexType predicateType = LogicLanguageServices
				.getTypeRepository()
				.getTypeCreateIfNeeded(type, SkolemServices.getIDType());
		final LogicalConstant predicate = LogicalConstant
				.create(INSTANCE.refPredicateBaseName, predicateType, true);

		INSTANCE.refPredicatesCache.put(type, predicate);

		return predicate;
	}

	/**
	 * Create a skolem term predicate, e.g., a:<id,<<e,t>,e>>.
	 */
	public static LogicalConstant createSkolemPredicate(Type type) {
		final LogicalConstant cached = INSTANCE.skolemPredicatesCache.get(type);

		if (cached != null) {
			return cached;
		}

		final ComplexType predicateType = LogicLanguageServices
				.getTypeRepository().getTypeCreateIfNeeded(
						LogicLanguageServices.getTypeRepository()
								.getTypeCreateIfNeeded(type,
										LogicLanguageServices
												.getTypeRepository()
												.getTypeCreateIfNeeded(
														LogicLanguageServices
																.getTypeRepository()
																.getTruthValueType(),
														type)),
						SkolemServices.getIDType());
		final LogicalConstant predicate = LogicalConstant
				.create(INSTANCE.skolemPredicateBaseName, predicateType, true);

		INSTANCE.skolemPredicatesCache.put(type, predicate);

		return predicate;
	}

	public static LogicalConstant createTextConstant(List<String> tokens) {
		final String TEXT_STRING_SEP = "++";
		final String escapedString = LogicalConstant
				.escapeString(tokens.stream().map(token -> {
					final StringBuilder modifiedToken = new StringBuilder(
							token);
					if (modifiedToken.charAt(0) == '"') {
						modifiedToken.deleteCharAt(0);
					}
					if (modifiedToken
							.charAt(modifiedToken.length() - 1) == '"') {
						modifiedToken.deleteCharAt(modifiedToken.length() - 1);
					}
					return modifiedToken.toString();
				}).collect(Collectors.joining(TEXT_STRING_SEP)));
		return LogicalConstant.create(escapedString, INSTANCE.textType, true);
	}

	public static LogicalConstant createTextConstant(TokenSeq tokens) {
		return createTextConstant(tokens.toList());
	}

	public static Syntax getCompleteSentenceSyntax() {
		return AMRServices.AMR;
	}

	public static LogicalConstant getDummyEntity() {
		return INSTANCE.dummyEntity;
	}

	public static Type getFinalType(Type type) {
		Type currentType = type;
		while (!(currentType instanceof RecursiveComplexType)
				&& currentType.isComplex()) {
			currentType = currentType.getRange();
		}
		return currentType;
	}

	public static Set<RecognizedNamedEntity> getNamedEntities(
			Sentence sentence) {
		return INSTANCE.namedEntityRecognizer == null ? null
				: INSTANCE.namedEntityRecognizer
						.getNamedEntities(sentence.getTokens());
	}

	public static Set<PropBankFrame> getPropBankFrames(String lemma) {
		return INSTANCE.propBank == null ? Collections.emptySet()
				: INSTANCE.propBank.getFrames(lemma);
	}

	public static SpecificationMapping getSpecMapping() {
		return INSTANCE.mapping;
	}

	public static Type getTextType() {
		return INSTANCE.textType;
	}

	/**
	 * Get the unary typing predicate (if one exists) from a <e,t>-typed lambda
	 * term (the body of a skolem term).
	 */
	public static LogicalConstant getTypingPredicate(Lambda set) {
		if (set.getBody() instanceof Literal) {
			final Literal bodyLiteral = (Literal) set.getBody();
			final int len = bodyLiteral.numArgs();
			if (bodyLiteral.getPredicate()
					.equals(LogicLanguageServices.getConjunctionPredicate())) {
				return getTypingPredicateFromConjunction(bodyLiteral);
			} else if (len == 1
					&& bodyLiteral.getPredicate() instanceof LogicalConstant) {
				return (LogicalConstant) bodyLiteral.getPredicate();
			}
		}
		return null;
	}

	/**
	 * Given a skolem term, return the unary predicate that defines its type, if
	 * such exist.
	 */
	public static LogicalConstant getTypingPredicate(Literal skolemTerm) {
		if (skolemTerm.numArgs() == 2
				&& skolemTerm.getArg(1) instanceof Lambda) {
			return getTypingPredicate((Lambda) skolemTerm.getArg(1));
		}
		return null;
	}

	/**
	 * Get the typing predicate, if exists, from a list of conjuncts in a
	 * conjunction literal.
	 */
	public static LogicalConstant getTypingPredicateFromConjunction(
			Literal conjunctionLiteral) {
		LogicalConstant typingPredicate = null;
		final int numArgs = conjunctionLiteral.numArgs();
		for (int i = 0; i < numArgs; ++i) {
			final LogicalExpression arg = conjunctionLiteral.getArg(i);
			if (arg instanceof Literal && ((Literal) arg).numArgs() == 1
					&& ((Literal) arg)
							.getPredicate() instanceof LogicalConstant) {
				if (typingPredicate == null) {
					typingPredicate = (LogicalConstant) ((Literal) arg)
							.getPredicate();
				} else {
					return null;
				}
			}
		}
		return typingPredicate;
	}

	public static Type getTypingPredicateType() {
		return INSTANCE.typingPredicateType;
	}

	/**
	 * @param i
	 *            Create opi predicates.
	 * @param predicatType
	 *            The type of the generated predicate.
	 */
	public static LogicalConstant integerToOpPredicate(int i, Type type) {
		return LogicalConstant.create(INSTANCE.opPredicatePrefix + i, type,
				true);
	}

	public static boolean isAmrRelation(LogicalConstant c) {
		return c.getBaseName().startsWith("c_");
	}

	public static boolean isAmrRelation(LogicalExpression exp) {
		if (exp instanceof LogicalConstant) {
			return isAmrRelation((LogicalConstant) exp);
		} else {
			return false;
		}
	}

	public static boolean isNamedEntity(Literal skolemTerm) {
		if (skolemTerm.numArgs() == 2
				&& skolemTerm.getArg(1) instanceof Lambda) {
			return isNamedEntityBody((Lambda) skolemTerm.getArg(1));
		}

		return false;
	}

	public static boolean isNamedEntityBody(Lambda entityBody) {
		if (entityBody.getComplexType().getDomain()
				.equals(LogicLanguageServices.getTypeRepository()
						.getEntityType())
				&& entityBody.getComplexType().getRange()
						.equals(LogicLanguageServices.getTypeRepository()
								.getTruthValueType())
				&& entityBody.getBody() instanceof Literal) {
			final Literal bodyLiteral = (Literal) entityBody.getBody();
			if (bodyLiteral.getPredicate()
					.equals(LogicLanguageServices.getConjunctionPredicate())) {
				final int len = bodyLiteral.numArgs();
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = bodyLiteral.getArg(i);
					if (arg instanceof Literal && ((Literal) arg).numArgs() == 2
							&& ((Literal) arg).getArg(1) instanceof Literal
							&& isSkolemTerm(
									(Literal) ((Literal) arg).getArg(1))) {
						if (INSTANCE.nameInstancePredicate
								.equals(getTypingPredicate(
										(Literal) ((Literal) arg).getArg(1)))) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public static boolean isOpPredicate(LogicalExpression predicate) {
		return opPredicateToInteger(predicate) != null;
	}

	/**
	 * Returns 'true' if and only if the given expression is a logical constant
	 * and its stripped base name starts with {@value #INVERSE_RELATION_SUFFIX}.
	 *
	 * @param exp
	 * @return
	 */
	public static boolean isPassivePredicate(LogicalExpression exp) {
		if (!(exp instanceof LogicalConstant)) {
			return false;
		}
		final LogicalConstant constant = (LogicalConstant) exp;

		if (!isAmrRelation(constant)) {
			return false;
		}

		final String baseName = OverloadedLogicalConstant.getWrapped(constant)
				.getBaseName();
		return baseName.endsWith(INVERSE_RELATION_SUFFIX);
	}

	public static boolean isPropBankFrame(String name) {
		return INSTANCE.propBank == null ? false
				: INSTANCE.propBank.isFrame(name);
	}

	/**
	 * Checks if the literal is a reference literal (i.e., (ref:<id,e> A:id)).
	 */
	public static boolean isRefLiteral(Literal literal) {
		return literal.numArgs() == 1 && isRefPredicate(literal.getPredicate());
	}

	/**
	 * @see #isRefLiteral(Literal)
	 */
	public static boolean isRefLiteral(LogicalExpression exp) {
		return exp instanceof Literal && isRefLiteral((Literal) exp);
	}

	public static boolean isRefPredicate(LogicalExpression exp) {
		if (exp instanceof LogicalConstant && ((LogicalConstant) exp)
				.getBaseName().equals(INSTANCE.refPredicateBaseName)) {
			// Get return type, create a skolem predicate for it and compare.
			return exp.equals(createRefPredicate(getFinalType(exp.getType())));
		}
		return false;
	}

	public static boolean isSkolemPredicate(LogicalExpression exp) {
		if (exp instanceof LogicalConstant && ((LogicalConstant) exp)
				.getBaseName().equals(INSTANCE.skolemPredicateBaseName)) {
			// Get return type, create a skolem predicate for it and compare.
			return exp
					.equals(createSkolemPredicate(getFinalType(exp.getType())));
		}
		return false;
	}

	public static boolean isSkolemTerm(Literal literal) {
		return isSkolemPredicate(literal.getPredicate());
	}

	public static boolean isSkolemTerm(LogicalExpression exp) {
		if (exp instanceof Literal) {
			return isSkolemTerm((Literal) exp);
		} else {
			return false;
		}
	}

	public static boolean isSkolemTermBody(Lambda lambda) {
		final ComplexType type = lambda.getComplexType();
		return type.getDomain()
				.equals(LogicLanguageServices.getTypeRepository()
						.getEntityType())
				&& type.getRange().equals(LogicLanguageServices
						.getTypeRepository().getTruthValueType());
	}

	public static boolean isSkolemTermBody(LogicalExpression exp) {
		return exp instanceof Lambda && isSkolemTermBody((Lambda) exp);

	}

	public static boolean isTextType(Type type) {
		return INSTANCE.textType.equals(type);
	}

	public static boolean isUnderspecified(LogicalConstant constant) {
		return INSTANCE.mapping.isUnderspecified(constant);
	}

	public static Set<String> lemmatize(String token) {
		// The lemmas are interned to save memory. This procedure is usually
		// called when reading data from IO, so the cost is marginal.
		return INSTANCE.lemmatizer.lemmatize(token).stream()
				.map(s -> s.intern()).collect(Collectors.toSet());
	}

	/**
	 * Extract the "lemma" of a constant.
	 */
	@Deprecated
	public static String lemmatizeConstant(LogicalConstant constant) {
		if (isAmrRelation(constant)) {
			return null;
		}

		final String lemma;
		final Matcher matcher = FRAME_PREDICATE_REGEX
				.matcher(constant.getBaseName());
		if (matcher.matches()) {
			lemma = matcher.group("pred");
		} else {
			lemma = constant.getBaseName();
		}
		return lemma;
	}

	/**
	 * Given a passive relation, returns its active form. Otherwise, returns
	 * null.
	 *
	 * @param underspecified
	 *            The output should be underspecified or not.
	 */
	public static LogicalExpression makeRelationActive(LogicalConstant constant,
			boolean underspecified) {
		if (isAmrRelation(constant)) {
			String baseName;
			boolean overload;
			if (constant instanceof OverloadedLogicalConstant) {
				baseName = ((OverloadedLogicalConstant) constant)
						.getWrappedConstant().getBaseName();
				overload = true;
			} else {
				baseName = constant.getBaseName();
				overload = false;
			}

			if (baseName.endsWith(INVERSE_RELATION_SUFFIX)) {
				final LogicalConstant activeConstant = underspecified
						? (LogicalConstant) underspecify(LogicalConstant.create(
								baseName.substring(0, baseName.length()
										- INVERSE_RELATION_SUFFIX.length()),
								constant.getType(), true))
						: LogicalConstant.create(
								baseName.substring(0, baseName.length()
										- INVERSE_RELATION_SUFFIX.length()),
								constant.getType(), true);
				if (overload) {
					return ((OverloadedLogicalConstant) constant)
							.cloneWrapper(activeConstant);
				} else {
					return activeConstant;
				}
			}
		}
		return null;
	}

	/**
	 * Convert a binary relation to its passive version, e.g., boo:<e,<e,t>> ->
	 * boo-of:<e,<e,t>>.
	 */
	public static LogicalConstant makeRelationPassive(LogicalConstant constant,
			boolean underspecified) {
		if (isAmrRelation(constant)) {
			String baseName;
			boolean overload;
			if (constant instanceof OverloadedLogicalConstant) {
				baseName = ((OverloadedLogicalConstant) constant)
						.getWrappedConstant().getBaseName();
				overload = true;
			} else {
				baseName = constant.getBaseName();
				overload = false;
			}

			if (!baseName.endsWith(INVERSE_RELATION_SUFFIX)) {
				final LogicalConstant passiveConstant = underspecified
						? (LogicalConstant) underspecify(LogicalConstant.create(
								baseName + INVERSE_RELATION_SUFFIX,
								constant.getType(), true))
						: LogicalConstant.create(
								baseName + INVERSE_RELATION_SUFFIX,
								constant.getType(), true);
				if (overload) {
					return ((OverloadedLogicalConstant) constant)
							.cloneWrapper(passiveConstant);
				} else {
					return passiveConstant;
				}
			}
		}
		return null;
	}

	public static Integer opPredicateToInteger(LogicalExpression predicate) {
		if (predicate instanceof LogicalConstant) {
			final String name = ((LogicalConstant) predicate).getBaseName();
			final int len = name.length();
			if (len > INSTANCE.opPredicatePrefix.length()
					&& name.startsWith(INSTANCE.opPredicatePrefix)) {
				for (int i = INSTANCE.opPredicatePrefix
						.length(); i < len; ++i) {
					if (!Character.isDigit(name.charAt(i))) {
						return null;
					}
				}
				return Integer.valueOf(
						name.substring(INSTANCE.opPredicatePrefix.length()));
			}
		}
		return null;
	}

	public static void setInstance(AMRServices instance) {
		AMRServices.INSTANCE = instance;
	}

	public static Literal skolemize(LogicalExpression set) {
		if (set.getType().isComplex()) {
			final Type range = set.getType().getRange();
			if (range.equals(LogicLanguageServices.getTypeRepository()
					.getTruthValueType())) {
				final Type domain = set.getType().getDomain();
				return new Literal(createSkolemPredicate(domain), ArrayUtils
						.create(SkolemServices.getIdPlaceholder(), set));
			}
		}
		return null;
	}

	public static LogicalExpression stripSkolemIds(LogicalExpression exp) {
		return StripSkolemIds.of(exp, SkolemServices.getIdPlaceholder());
	}

	public static TokenSeq tagSentence(Sentence sentence) {
		return INSTANCE.tagger == null ? null
				: TokenSeq.of(INSTANCE.tagger
						.tagSentence(edu.stanford.nlp.ling.Sentence
								.toWordList(sentence.getTokens().toList()
										.toArray(new String[sentence.getTokens()
												.size()])))
						.stream().map((tw) -> tw.tag())
						.collect(Collectors.toList()));
	}

	public static List<String> textConstantToStrings(LogicalConstant constant) {
		final String[] split = LogicalConstant
				.unescapeString(constant.getBaseName()).split("\\+\\+");
		for (int i = 0; i < split.length; ++i) {
			split[i] = "\"" + split[i] + "\"";
		}
		return Arrays.asList(split);
	}

	public static String toString(LogicalExpression amr) {
		return INSTANCE.amrPrinter.toString(amr);
	}

	public static LogicalExpression underspecify(LogicalExpression exp) {
		return Underspecify.of(exp, INSTANCE.mapping);
	}

	public static Category<LogicalExpression> underspecifyAndStrip(
			Category<LogicalExpression> category) {
		if (category.getSemantics() == null) {
			return category;
		} else {
			return category.cloneWithNewSemantics(
					underspecifyAndStrip(category.getSemantics()));

		}
	}

	public static LexicalEntry<LogicalExpression> underspecifyAndStrip(
			LexicalEntry<LogicalExpression> entry) {
		if (entry.getCategory().getSemantics() == null) {
			return entry;
		} else {
			return new LexicalEntry<>(entry.getTokens(),
					entry.getCategory()
							.cloneWithNewSemantics(underspecifyAndStrip(
									entry.getCategory().getSemantics())),
					entry.isDynamic(), entry.getProperties());
		}
	}

	public static LogicalExpression underspecifyAndStrip(
			LogicalExpression exp) {
		return stripSkolemIds(Underspecify.of(exp, INSTANCE.mapping));
	}

	public static class Builder {
		private final LogicalConstant	dummyEntity;
		private SpecificationMapping	mapping		= new SpecificationMapping(
				false);
		private IllinoisNERWrapper		namedEntityRecognizer;
		private final LogicalConstant	nameInstancePredicate;

		private final String			opPredicatePrefix;
		private File					probBankDir	= null;
		private final String			refPredicateBaseName;
		private final String			skolemPredicateBaseName;
		private final File				stanfordModelFile;
		private final Type				textType;
		private final Type				typingPredicateType;

		public Builder(String skolemPredicateBaseName, Type textType,
				String refPredicateBaseName, File stanfordModel,
				String opPredicatePrefix, LogicalConstant dummyEntity,
				LogicalConstant nameInstancePredicate,
				Type typingPredicateType) {
			this.skolemPredicateBaseName = skolemPredicateBaseName;
			this.textType = textType;
			this.refPredicateBaseName = refPredicateBaseName;
			this.stanfordModelFile = stanfordModel;
			this.opPredicatePrefix = opPredicatePrefix;
			this.dummyEntity = dummyEntity;
			this.nameInstancePredicate = nameInstancePredicate;
			this.typingPredicateType = typingPredicateType;
		}

		public AMRServices build() throws IOException {
			return new AMRServices(skolemPredicateBaseName, textType,
					refPredicateBaseName, mapping, stanfordModelFile,
					opPredicatePrefix, dummyEntity, nameInstancePredicate,
					typingPredicateType, namedEntityRecognizer, probBankDir);
		}

		public Builder setNamedEntityRecognizer(
				IllinoisNERWrapper namedEntityRecognizer) {
			this.namedEntityRecognizer = namedEntityRecognizer;
			return this;
		}

		public Builder setPropBankDir(File probBankDir) {
			this.probBankDir = probBankDir;
			return this;
		}

		public Builder setSpecificationMapping(SpecificationMapping mapping) {
			this.mapping = mapping;
			return this;
		}

	}

}
