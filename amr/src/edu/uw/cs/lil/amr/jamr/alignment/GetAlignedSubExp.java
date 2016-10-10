package edu.uw.cs.lil.amr.jamr.alignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class GetAlignedSubExp implements ILogicalExpressionVisitor {

	public static final ILogger	LOG		= LoggerFactory
												.create(GetAlignedSubExp.class);
	private String				index	= "0";
	private final Set<String>	indices;
	private boolean				invalid	= false;

	private LogicalExpression	result	= null;

	private GetAlignedSubExp(Set<String> indices) {
		this.indices = new HashSet<>(indices);
	}

	public static LogicalExpression of(LogicalExpression exp, String indexSet) {
		final GetAlignedSubExp visitor = new GetAlignedSubExp(
				AlignmentServices.splitIndexSet(indexSet));
		visitor.visit(exp);
		if (visitor.invalid) {
			return null;
		} else if (visitor.indices.isEmpty()) {
			return visitor.result;
		} else {
			LOG.info("Failed to consume all indices, returning null: %s",
					visitor.indices);
			return null;
		}
	}

	@Override
	public void visit(Lambda lambda) {
		// Shouldn't get here, throw an exception.
		invalid = true;
		result = null;
	}

	@Override
	public void visit(Literal literal) {
		final String currentIndex = index;

		if (AMRServices.isSkolemTerm(literal) && literal.numArgs() == 2
				&& literal.getArg(1) instanceof Lambda) {
			final Literal body = (Literal) ((Lambda) literal.getArg(1))
					.getBody();
			if (body.getPredicate().equals(
					LogicLanguageServices.getConjunctionPredicate())) {
				LogicalExpression typingLiteral = null;
				int relationIndex = 0;
				final int len = body.numArgs();
				final List<LogicalExpression> newArgs = new ArrayList<>(len);
				for (int i = 0; i < len; ++i) {
					index = currentIndex;
					final LogicalExpression arg = body.getArg(i);
					if (arg instanceof Literal) {
						final Literal argLiteral = (Literal) arg;
						if (argLiteral.numArgs() == 1) {
							typingLiteral = argLiteral;
						} else {
							// Binary literal.

							// Skip if relation to a reference.
							if (AMRServices.isRefLiteral(argLiteral.getArg(1))) {
								continue;
							}

							// Process the second argument.
							if (argLiteral.getArg(1) instanceof LogicalConstant) {
								final LogicalConstant constant = (LogicalConstant) argLiteral
										.getArg(1);
								if (constant.getType().equals(
										AMRServices.getTextType())) {
									// Special case: text constant -- must break
									// into original constants to track the
									// indices properly.
									final int numTokens = AMRServices
											.textConstantToStrings(constant)
											.size();
									final Set<String> txtIndices = IntStream
											.range(relationIndex,
													relationIndex + numTokens)
											.mapToObj(num -> index + "." + num)
											.collect(Collectors.toSet());
									if (indices.containsAll(txtIndices)) {
										indices.removeAll(txtIndices);
										result = argLiteral;
									} else if (indices.removeAll(txtIndices)) {
										// Common error, so make it only a debug
										// message.
										LOG.debug(
												"Only part of name is aligned: %s",
												literal);
										invalid = true;
										result = null;
										return;
									} else {
										result = null;
									}

									relationIndex += numTokens;

								} else {
									// Non-text constant.
									// Update the index.
									index += "." + relationIndex;

									if (indices.remove(index)) {
										result = argLiteral;
									} else {
										result = null;
									}

									// Increase the index count.
									relationIndex++;
								}
							} else {
								// Update the index.
								index += "." + relationIndex;
								// Visit.
								argLiteral.getArg(1).accept(this);
								if (result != null) {
									if (indices.contains(currentIndex)) {
										result = new Literal(
												argLiteral.getPredicate(),
												ArrayUtils.create(
														argLiteral.getArg(0),
														result));
									} else {
										// If we are not capturing the current
										// instance, can already return the
										// result to the upper level.
										return;
									}
								}
								// Increase the relation index.
								relationIndex++;
							}

							// If the result is not null, add to the new
							// arguments list.
							if (result != null) {
								newArgs.add(result);
							}

						}
					} else {
						LOG.info("Unexpected AMR structure: %s", literal);
						result = null;
						invalid = true;
						return;
					}
				}
				if (indices.remove(currentIndex)) {
					if (newArgs.isEmpty()) {
						result = new Literal(literal.getPredicate(),
								ArrayUtils.create(
										literal.getArg(0),
										new Lambda(((Lambda) literal.getArg(1))
												.getArgument(), typingLiteral)));
					} else {
						newArgs.add(0, typingLiteral);
						result = new Literal(
								literal.getPredicate(),
								ArrayUtils.create(
										literal.getArg(0),
										new Lambda(
												((Lambda) literal.getArg(1))
														.getArgument(),
												new Literal(
														LogicLanguageServices
																.getConjunctionPredicate(),
														newArgs.toArray(new LogicalExpression[newArgs
																.size()])))));
					}
				} else {
					// Either one and only one of newArgs is null, and then we
					// return its second argument. Or newArgs is empty, and then
					// return null.
					if (newArgs.isEmpty()) {
						result = null;
					} else if (newArgs.size() == 1
							&& newArgs.get(0) instanceof Literal) {
						result = ((Literal) newArgs.get(0)).getArg(1);
					} else {
						LOG.info(
								"Illegal alignment sub-graph, multiple disconnected aligments: %s",
								newArgs);
						invalid = true;
						result = null;
						return;
					}
				}
			} else {
				// Single instance property, must be the unary typing literal.
				// If the set of indices, includes it, return the literal,
				// otherwise return null.
				if (indices.remove(currentIndex)) {
					result = literal;
				} else {
					result = null;
				}
			}
		}
	}

	@Override
	public void visit(LogicalConstant logicalConstant) {
		// Nothing to do, simply return the constant.
		result = logicalConstant;
	}

	@Override
	public void visit(LogicalExpression logicalExpression) {
		logicalExpression.accept(this);
	}

	@Override
	public void visit(Variable variable) {
		// Nothing to do, simply return the variable.
		result = variable;
	}

}
