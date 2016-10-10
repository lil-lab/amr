package edu.uw.cs.lil.amr.lambda.convert;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jregex.Pattern;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.spf.mr.language.type.ComplexType;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.string.StringReader;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Converter to convert AMR (in string representation) to
 * {@link LogicalExpression}.
 *
 * @author Yoav Artzi
 */
public class AmrToLogicalExpressionConverter {
	private static final Pattern	INTEGER	= new Pattern("\\d+");

	private static Literal createBinaryLiteral(LogicalConstant predicate,
			LogicalExpression arg1, LogicalExpression arg2) {
		return new Literal(predicate, ArrayUtils.create(arg1, arg2));
	}

	private static Literal createBinaryLiteral(String predicateName,
			LogicalExpression arg1, LogicalExpression arg2, Type returnType) {
		return createBinaryLiteral(
				createConstant(
						predicateName,
						LogicLanguageServices.getTypeRepository()
								.getTypeCreateIfNeeded(
										LogicLanguageServices
												.getTypeRepository()
												.getTypeCreateIfNeeded(
														returnType,
														arg2.getType()),
										arg1.getType())), arg1, arg2);
	}

	private static LogicalConstant createConstant(String name, Type type) {
		return LogicalConstant.create(LogicalConstant.escapeString(name), type,
				true);
	}

	private static LogicalConstant createEntity(String name) {
		if (INTEGER.matches(name)) {
			return LogicLanguageServices.intToLogicalExpression(Long
					.valueOf(name));
		} else {
			return createConstant(name, LogicLanguageServices
					.getTypeRepository().getEntityType());
		}
	}

	private static String createModifierPrediacteName(String string) {
		return "c_" + string.substring(1);
	}

	private static Literal createUnaryLiteral(LogicalConstant predicate,
			LogicalExpression arg) {
		return new Literal(predicate, ArrayUtils.create(arg));
	}

	private static Literal createUnaryLiteral(String predicateName,
			LogicalExpression arg, Type returnType) {
		final ComplexType type = LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded(returnType, arg.getType());
		return createUnaryLiteral(LogicalConstant.create(
				LogicalConstant.escapeString(predicateName), type, true), arg);
	}

	private static String getNextToken(StringReader reader) throws IOException {
		final StringBuilder sb = new StringBuilder();
		stringPrefixWhitespaces(reader);
		final char peek = peek(reader);
		if (peek == '"') {
			// Case quoted text, read until closing quotation mark
			sb.append((char) reader.read());
			char c = (char) reader.read();
			sb.append(c);
			while ((c = (char) reader.read()) != '"') {
				sb.append(c);
			}
			sb.append(c);
		} else if (peek == '(' || peek == ')') {
			sb.append((char) reader.read());
		} else {
			// Read until the next white space or ')'
			while (!Character.isWhitespace(peek(reader)) && peek(reader) != ')') {
				sb.append((char) reader.read());
			}
		}
		stringPrefixWhitespaces(reader);
		return sb.toString();
	}

	/**
	 * Parse a single quoted text (e.g., "text").
	 */
	private static LogicalConstant parseQuote(String token) {
		return createConstant(token, AMRServices.getTextType());
	}

	private static char peek(Reader reader) throws IOException {
		reader.mark(1000);
		final char c = (char) reader.read();
		reader.reset();
		return c;
	}

	private static void stringPrefixWhitespaces(Reader reader)
			throws IOException {
		while (Character.isWhitespace(peek(reader))) {
			reader.read();
		}
	}

	public LogicalExpression read(String amr) throws IOException {
		try (StringReader reader = new StringReader(amr)) {
			final String firstToken = getNextToken(reader);
			if (!firstToken.equals("(")) {
				throw new IllegalStateException("AMR doesn't start with '(': "
						+ amr);
			}
			final Map<LogicalConstant, Pair<SkolemId, Type>> skolemIDs = new HashMap<>();
			final LogicalExpression instance = parseInstance(reader,
					new HashMap<String, Variable>(), skolemIDs);

			// Replace all dummy logical constants with the proper skolem IDs.
			final SetReferences visitor = new SetReferences(skolemIDs);
			visitor.visit(instance);
			return visitor.tempReturn;
		}
	}

	/**
	 * @param reader
	 *            The opening parenthesis is assumed to be removed.
	 * @param hashMap
	 * @return A e-typed logical form with an indefinite determiner.
	 */
	private LogicalExpression parseInstance(StringReader reader,
			Map<String, Variable> variables,
			Map<LogicalConstant, Pair<SkolemId, Type>> skolemIDs)
			throws IOException {
		// Variable name.
		final String varName = getNextToken(reader);
		// Verify that the next token is a '/'.
		final String slash = getNextToken(reader);
		if (!slash.equals("/")) {
			throw new IllegalStateException("Unexpected token: " + slash);
		}
		// Unary typing predicate.
		final String typingPredicateName = getNextToken(reader);

		// Handle a few special cases.

		// Compute the type of the instance.
		final Type instanceType = LogicLanguageServices.getTypeRepository()
				.getEntityType();

		// Create the variable and update the mapping.
		final Variable variable = new Variable(instanceType);
		variables.put(varName, variable);

		// Names.
		if (typingPredicateName.equals("name") && peek(reader) != ')') {
			final Literal nameInstance = parseNameInstance(reader, skolemIDs,
					variable, variables, varName);

			if (nameInstance != null) {
				// Remove the variable from the mapping.
				variables.remove(varName);

				return nameInstance;
			}
		}

		// Create the skolem ID and keep it.
		final SkolemId skolemId = new SkolemId();
		skolemIDs.put(LogicalConstant.create(varName, LogicLanguageServices
				.getTypeRepository().getEntityType(), true), Pair.of(skolemId,
				instanceType));

		// The conjunction in the body of the exists.
		final List<LogicalExpression> conjuctionArgs = new LinkedList<>();
		// Add the unary typing literal.
		conjuctionArgs.add(createUnaryLiteral(typingPredicateName, variable,
				LogicLanguageServices.getTypeRepository().getTruthValueType()));

		// Get the arguments and append them to the conjunction arguments.
		while (peek(reader) != ')') {
			conjuctionArgs.add(parseRelation(reader, getNextToken(reader),
					variable, variables, skolemIDs));
		}

		// Remove the variable from the mapping.
		variables.remove(varName);

		// Remove the closing parenthesis.
		getNextToken(reader);

		final LogicalExpression lambdaBody;
		if (conjuctionArgs.size() == 1) {
			lambdaBody = conjuctionArgs.get(0);
		} else {
			lambdaBody = new Literal(
					LogicLanguageServices.getConjunctionPredicate(),
					conjuctionArgs.toArray(new LogicalExpression[conjuctionArgs
							.size()]));
		}
		return new Literal(AMRServices.createSkolemPredicate(instanceType),
				ArrayUtils.create(skolemId, new Lambda(variable, lambdaBody)));
	}

	private Literal parseNameInstance(StringReader reader,
			Map<LogicalConstant, Pair<SkolemId, Type>> skolemIDs,
			Variable variable, Map<String, Variable> variables, String varName)
			throws IOException {
		final int initialPosition = reader.position();
		final List<Pair<String, String>> ops = new LinkedList<>();
		final List<Literal> relations = new LinkedList<>();
		while (peek(reader) != ')') {
			final String argKey = getNextToken(reader);
			if (argKey.startsWith(":op") && peek(reader) == '"') {
				// Get the argument value.
				ops.add(Pair.of(argKey, getNextToken(reader)));
			} else {
				// Non text relation.
				relations.add(parseRelation(reader, argKey, variable,
						variables, skolemIDs));
			}
		}

		// If the list of ops (quoted chucks of text is empty), fail and try to
		// to read it as regular instance.
		if (ops.isEmpty()) {
			reader.reset(initialPosition);
			return null;
		}

		// Remove the closing parenthesis.
		getNextToken(reader);

		// Sort the arguments list.
		Collections.sort(ops, (o1, o2) -> o1.first().compareTo(o2.first()));

		final List<String> nameList = ops.stream().map((p) -> p.second())
				.collect(Collectors.toList());

		// Create the logical constant to return.
		final LogicalConstant nameConstant = AMRServices
				.createTextConstant(nameList);

		// Verify we can create back the original tokens.
		if (!nameList.equals(AMRServices.textConstantToStrings(nameConstant))) {
			throw new IllegalStateException(
					String.format(
							"Failed to recover name list from text constant: %s <-> %s [%s]",
							nameList, nameConstant, reader));
		}

		// Create name instance, e.g., (a:<id,<<e,t>,e>> (lambda $0:e
		// (and:<t*,t> (name:<e,t> $0) (op:<e,<txt,t>> $0 NAME:txt))))

		// Create the skolem ID and keep it.
		final SkolemId skolemId = new SkolemId();
		skolemIDs.put(LogicalConstant.create(varName, LogicLanguageServices
				.getTypeRepository().getEntityType(), true), Pair.of(skolemId,
				LogicLanguageServices.getTypeRepository().getEntityType()));

		relations.add(
				0,
				createBinaryLiteral("c_op", variable, nameConstant,
						LogicLanguageServices.getTypeRepository()
								.getTruthValueType()));
		relations.add(
				0,
				createUnaryLiteral("name", variable, LogicLanguageServices
						.getTypeRepository().getTruthValueType()));

		final Literal nameSkolemTerm = new Literal(
				AMRServices.createSkolemPredicate(LogicLanguageServices
						.getTypeRepository().getEntityType()),
				ArrayUtils.create(skolemId, new Lambda(variable, new Literal(
						LogicLanguageServices.getConjunctionPredicate(),
						relations.toArray(new Literal[relations.size()])))));

		return nameSkolemTerm;
	}

	private Literal parseRelation(StringReader reader, String argKey,
			Variable variable, Map<String, Variable> variables,
			Map<LogicalConstant, Pair<SkolemId, Type>> skolemIDs)
			throws IOException {
		if (!argKey.startsWith(":")) {
			throw new IllegalStateException("Arg not starting with :");
		}
		// Create the predicate. The predicate name is stripped of the
		// leading ':'.
		final String predicateName = createModifierPrediacteName(argKey);

		final char peek = peek(reader);
		if (peek == '"') {
			// Case quoted text.
			final LogicalConstant constant = parseQuote(getNextToken(reader));
			return createBinaryLiteral(predicateName, variable, constant,
					LogicLanguageServices.getTypeRepository()
							.getTruthValueType());
		} else if (peek == '(') {
			// String the opening parenthesis.
			getNextToken(reader);
			final LogicalExpression argInstance = parseInstance(reader,
					variables, skolemIDs);
			return createBinaryLiteral(predicateName, variable, argInstance,
					LogicLanguageServices.getTypeRepository()
							.getTruthValueType());
		} else {
			// Case constant.
			final String token = getNextToken(reader);
			// If this is a reference variable, create an entity anyway. We
			// don't want to have variables used in such cases. References
			// should be resolved using IDs.
			final LogicalExpression arg = createEntity(token);
			return createBinaryLiteral(predicateName, variable, arg,
					LogicLanguageServices.getTypeRepository()
							.getTruthValueType());
		}
	}

	private class SetReferences implements ILogicalExpressionVisitor {

		private final Map<LogicalConstant, Pair<SkolemId, Type>>	skolemIDs;

		private LogicalExpression									tempReturn	= null;

		public SetReferences(
				Map<LogicalConstant, Pair<SkolemId, Type>> skolemIDs) {
			this.skolemIDs = skolemIDs;
		}

		@Override
		public void visit(Lambda lambda) {
			lambda.getBody().accept(this);
			if (tempReturn == lambda.getBody()) {
				tempReturn = lambda;
			} else {
				tempReturn = new Lambda(lambda.getArgument(), tempReturn);
			}
		}

		@Override
		public void visit(Literal literal) {
			boolean literalChanged = false;
			// Visit the predicate
			literal.getPredicate().accept(this);
			final LogicalExpression newPredicate;
			if (tempReturn == literal.getPredicate()) {
				newPredicate = literal.getPredicate();
			} else {
				if (tempReturn == null) {
					return;
				}
				newPredicate = tempReturn;
				literalChanged = true;
			}
			// Go over the arguments
			final int numArgs = literal.numArgs();
			final LogicalExpression[] args = new LogicalExpression[numArgs];
			for (int i = 0; i < numArgs; ++i) {
				final LogicalExpression arg = literal.getArg(i);
				arg.accept(this);
				if (tempReturn == null) {
					return;
				}
				args[i] = tempReturn;
				if (tempReturn != arg) {
					literalChanged = true;
				}
			}
			if (literalChanged) {
				if (args.length == 1 && newPredicate instanceof LogicalConstant) {
					tempReturn = createUnaryLiteral(
							(LogicalConstant) newPredicate, args[0]);
				} else if (args.length == 2
						&& newPredicate instanceof LogicalConstant) {
					tempReturn = createBinaryLiteral(
							(LogicalConstant) newPredicate, args[0], args[1]);
				} else {
					// This might fail if an unexpected literal observed.
					tempReturn = new Literal(newPredicate, args);
				}
			} else {
				tempReturn = literal;
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			if (skolemIDs.containsKey(logicalConstant)) {
				final SkolemId referenceID = skolemIDs.get(logicalConstant)
						.first();
				tempReturn = new Literal(
						AMRServices.createRefPredicate(skolemIDs.get(
								logicalConstant).second()),
						ArrayUtils.create(referenceID));
			} else {
				tempReturn = logicalConstant;
			}
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do.
			tempReturn = variable;
		}

	}

}
