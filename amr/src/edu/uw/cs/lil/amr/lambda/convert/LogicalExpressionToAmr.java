package edu.uw.cs.lil.amr.lambda.convert;

import java.util.HashMap;
import java.util.Map;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.cornell.cs.nlp.utils.string.StringUtils;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Visitor to convert a {@link LogicalExpression} to a AMR string. Basically
 * reverses {@link AmrToLogicalExpressionConverter}.
 *
 * @author Kenton Lee
 * @author Yoav Artzi
 */
public class LogicalExpressionToAmr implements ILogicalExpressionVisitor {
	public static final ILogger			LOG					= LoggerFactory
																	.create(LogicalExpressionToAmr.class);
	private static final String			RELATION_PREFIX		= "c_";
	private final StringBuilder			amrString			= new StringBuilder();
	private final boolean				indent;
	private final String				indentString;
	private final Map<SkolemId, String>	skolemMapping;
	private int							unknownRefCounter	= 0;
	int									currentDepth		= 0;

	boolean								valid				= true;

	private LogicalExpressionToAmr(boolean indent, String indentString) {
		this.skolemMapping = new HashMap<>();
		this.indent = indent;
		this.indentString = indentString;
	}

	public static String of(LogicalExpression exp) {
		return of(exp, false);
	}

	public static String of(LogicalExpression exp, boolean indent) {
		return of(exp, indent, "  ");
	}

	public static String of(LogicalExpression exp, boolean indent,
			String indentString) {
		final LogicalExpressionToAmr visitor = new LogicalExpressionToAmr(
				indent, indentString);
		visitor.visit(exp);
		return visitor.valid ? visitor.amrString.toString() : null;
	}

	private static Literal getTypingLiteral(Literal literal) {
		final int len = literal.numArgs();
		for (int i = 0; i < len; i++) {
			final LogicalExpression arg = literal.getArg(i);
			if (arg instanceof Literal
					&& ((Literal) arg).numArgs() == 1
					&& ((Literal) arg).getPredicate() instanceof LogicalConstant) {
				return (Literal) arg;
			}
		}
		return null;
	}

	@Override
	public void visit(Lambda lambda) {
		valid = false;
	}

	@Override
	public void visit(Literal literal) {
		// Assumes that each literal is either a skolem or reference predicate
		if (AMRServices.isSkolemPredicate(literal.getPredicate())
				&& literal.numArgs() == 2
				&& literal.getArg(0) instanceof SkolemId
				&& literal.getArg(1) instanceof Lambda) {
			// Skolem term.
			final LogicalExpression body = ((Lambda) literal.getArg(1))
					.getBody();
			final SkolemId skolemVar = (SkolemId) literal.getArg(0);
			final Variable lambdaVar = ((Lambda) literal.getArg(1))
					.getArgument();
			if (body instanceof Literal
					&& !LogicLanguageServices
							.isCoordinationPredicate(((Literal) body)
									.getPredicate())
					&& ((Literal) body).numArgs() == 1
					&& ((Literal) body).getPredicate() instanceof LogicalConstant) {
				// Single literal, only the typing literal.
				final LogicalConstant typingPredicate = (LogicalConstant) ((Literal) body)
						.getPredicate();
				appendInstanceHeader(
						getVariableName(skolemVar,
								typingPredicate.getBaseName()),
						typingPredicate.getBaseName());
			} else if (body instanceof Literal
					&& LogicLanguageServices
							.isCoordinationPredicate(((Literal) body)
									.getPredicate())) {
				// Conjunction of properties
				final Literal typingLiteral = getTypingLiteral((Literal) body);
				final LogicalConstant typingPredicate = (LogicalConstant) typingLiteral
						.getPredicate();
				final Literal coordinationBody = (Literal) body;
				if (typingPredicate == null) {
					LOG.debug("Invalid skolem term: no typing predicate");
					valid = false;
					return;
				}
				appendInstanceHeader(
						getVariableName(skolemVar,
								typingPredicate.getBaseName()),
						typingPredicate.getBaseName());
				currentDepth++;
				final int len = coordinationBody.numArgs();
				for (int i = 0; i < len; i++) {
					final LogicalExpression arg = coordinationBody.getArg(i);
					if (arg.equals(typingLiteral)) {
						if (!typingLiteral.getArg(0).equals(lambdaVar)) {
							LOG.debug("Invalid skolem term: typing predicate on the wrong expression");
							valid = false;
							return;
						}
						continue;
					} else if (arg instanceof Literal
							&& ((Literal) arg).numArgs() == 2
							&& ((Literal) arg).getPredicate() instanceof LogicalConstant
							&& ((LogicalConstant) ((Literal) arg)
									.getPredicate()).getBaseName().startsWith(
									RELATION_PREFIX)) {
						final Literal relation = (Literal) arg;
						final LogicalExpression relationArg = relation
								.getArg(1);
						final String relationName = ((LogicalConstant) relation
								.getPredicate()).getBaseName().substring(
								RELATION_PREFIX.length());
						if (!relation.getArg(0).equals(lambdaVar)) {
							LOG.debug("Invalid skolem term: relation to the wrong expression");
							valid = false;
							return;
						}
						if (relationName.equals("op")) {
							if (relationArg instanceof LogicalConstant
									&& AMRServices
											.isTextType(((LogicalConstant) relationArg)
													.getType())) {
								int opCounter = 1;
								for (final String token : AMRServices
										.textConstantToStrings((LogicalConstant) relationArg)) {
									appendRelation("op" + opCounter++);
									appendString(token);
								}
							} else {
								LOG.debug("op relation does not a have a text-type argument");
								valid = false;
							}
						} else {
							appendRelation(relationName);
							((Literal) arg).getArg(1).accept(this);
						}
					} else {
						LOG.debug("Invalid skolem term: coordinated predicate is neither a type predicate nor a relation predicate");
						valid = false;
						return;
					}
				}
				currentDepth--;
			}
			appendInstanceFooter();
		} else if (AMRServices.isRefPredicate(literal.getPredicate())
				&& literal.numArgs() == 1) {
			if (literal.getArg(0) instanceof SkolemId) {
				// Reference. Retrieve the variable name from the expression
				// mapping. If the declaration is below this reference, i.e. the
				// variable name is not yet in the mapping, choose x as the
				// prefix
				appendString(getVariableName((SkolemId) literal.getArg(0), "x"));
			} else {
				LOG.debug("Unknown reference, setting to unknown unique variable");
				appendString(getUnknownVariableName());
			}
		} else {
			LOG.debug("Invalid literal: %s", literal);
			valid = false;
			return;
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		appendString(logicalConstant.getBaseName());
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do.
	}

	private void appendIndentation() {
		if (indent) {
			appendString("\n%s",
					StringUtils.multiply(indentString, currentDepth));
		}
	}

	private void appendInstanceFooter() {
		appendString(")");
	}

	private void appendInstanceHeader(String varName, String typePredicate) {
		appendString("(%s / %s", varName, typePredicate);
	}

	private void appendRelation(String relation) {
		appendIndentation();
		appendString(" :%s ", relation);
	}

	private void appendString(String format, Object... arguments) {
		amrString.append(String.format(format, arguments));
	}

	private String getUnknownVariableName() {
		return "unk" + unknownRefCounter++;
	}

	private String getVariableName(SkolemId id, String suggestion) {
		if (skolemMapping.containsKey(id)) {
			return skolemMapping.get(id);
		} else {
			// Use the first character of the suggestion as the default name
			// Append a unique id if the name is already used
			String varName = suggestion.substring(0, 1);
			if (skolemMapping.containsValue(varName)) {
				int index = 2;
				while (skolemMapping.containsValue(varName + index)) {
					index++;
				}
				varName = varName + index;
			}
			skolemMapping.put(id, varName);
			return varName;
		}
	}
}
