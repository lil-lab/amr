package edu.uw.cs.lil.amr.lambda;

import java.io.ObjectStreamException;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.Ontology;

/**
 * Wraps a logical constant with various flags to capture the word surface form
 * and the directionality of argument according to the lexical entry
 * introducing the constant.
 *
 * If an ontology is maintained, wrapped constants are added to the ontology and
 * equals() behaves accordingly. Meaning, it will do instance comparison.
 * Otherwise, it will do equals based on {@link LogicalConstant}.
 *
 * @author Yoav Artzi
 *
 */
public class OverloadedLogicalConstant extends LogicalConstant {

	private static final String		OVERLAOD_DIRECTIONALITY_SEP	= "^^";
	private static final String		OVERLOAD_SEPARATOR			= "~~";
	private static final String		OVERLOAD_STRING_CONCAT_SEP	= "-";
	private static final long		serialVersionUID			= 8067352356936004531L;
	private final String			directionalityString;

	private final TokenSeq			surfaceForm;
	private final String			surfaceFormString;
	private final LogicalConstant	wrappedConstant;

	private OverloadedLogicalConstant(LogicalConstant constant,
			TokenSeq surfaceForm, String directionalityString) {
		super(wrapBaseName(constant.getBaseName(), surfaceForm,
				directionalityString), constant.getType(), true);
		assert !(constant instanceof OverloadedLogicalConstant) : "Can't wrap a wrapper";
		assert constant != null : "There must be a wrapped cosntant";
		this.directionalityString = directionalityString;
		this.surfaceForm = surfaceForm;
		this.wrappedConstant = constant;
		this.surfaceFormString = surfaceForm
				.toString(OVERLOAD_STRING_CONCAT_SEP);
	}

	/**
	 * If the constant is a {@link OverloadedLogicalConstant} returns the
	 * wrapped constant, otherwise returns the constant itself.
	 */
	public static LogicalConstant getWrapped(LogicalConstant constant) {
		if (constant instanceof OverloadedLogicalConstant) {
			return ((OverloadedLogicalConstant) constant).getWrappedConstant();
		} else {
			return constant;
		}
	}

	public static OverloadedLogicalConstant wrap(LogicalConstant constant,
			TokenSeq surfaceForm) {
		return wrap(constant, surfaceForm, null);
	}

	public static OverloadedLogicalConstant wrap(LogicalConstant constant,
			TokenSeq surfaceForm, String directionalityString) {
		assert !(constant instanceof OverloadedLogicalConstant) : "Can't wrap a wrapper";
		if (LogicLanguageServices.getOntology() == null) {
			// If not maintaining an ontology, simply create a new object.
			return new OverloadedLogicalConstant(constant, surfaceForm,
					directionalityString);
		} else {
			// If an ontology is being maintained, only create a new object if
			// one doesn't exist. Overloaded constants are generated
			// dynamically, so the 'force' flag is set to true.
			return LogicLanguageServices.getOntology()
					.getOrAdd(
							LogicalConstant.makeFullName(
									wrapBaseName(constant.getBaseName(),
											surfaceForm, directionalityString),
									constant.getType()),
							true, () -> new OverloadedLogicalConstant(constant,
									surfaceForm, directionalityString));
		}
	}

	private static String wrapBaseName(String baseName, TokenSeq surfaceForm,
			String directionalityString) {
		final StringBuilder overloaded = new StringBuilder(baseName)
				.append(OVERLOAD_SEPARATOR)
				.append(surfaceForm.toString(OVERLOAD_STRING_CONCAT_SEP));
		if (directionalityString != null) {
			overloaded.append(OVERLAOD_DIRECTIONALITY_SEP)
					.append(directionalityString);
		}
		return overloaded.toString();
	}

	/**
	 * Clones the wrapping with a new constant.
	 */
	public OverloadedLogicalConstant cloneWrapper(LogicalConstant constant) {
		return new OverloadedLogicalConstant(constant, surfaceForm,
				directionalityString);

	}

	/**
	 * Wrapped constants are not stored in the {@link Ontology}, even if one is
	 * used. Therefore, when they are de-serialized, they use a different
	 * {@link #readResolve()} method than {@link LogicalConstant}. To allow
	 * proper equals() behavior for de-serialized objects, the equals() method
	 * is also customized.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		final OverloadedLogicalConstant other = (OverloadedLogicalConstant) obj;

		if (!super.doEquals(other)) {
			return false;
		}

		if (wrappedConstant == null) {
			if (other.wrappedConstant != null) {
				return false;
			}
		} else if (!wrappedConstant.equals(other.wrappedConstant)) {
			return false;
		}

		return true;
	}

	public String getDirectionalityString() {
		return directionalityString;
	}

	public TokenSeq getSurfaceForm() {
		return surfaceForm;
	}

	public String getSurfaceFormString() {
		return surfaceFormString;
	}

	public LogicalConstant getWrappedConstant() {
		return wrappedConstant;
	}

	/**
	 * Resolves read serialized objects. This method basically returns the
	 * de-serialized object. We add it here to override
	 * {@link LogicalConstant#readResolve()} and avoid its behavior, which
	 * results in an exception when called for this class.
	 *
	 * @throws ObjectStreamException
	 */
	@Override
	protected Object readResolve() throws ObjectStreamException {
		return this;
	}

}
