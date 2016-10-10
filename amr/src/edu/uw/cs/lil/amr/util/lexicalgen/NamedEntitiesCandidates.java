package edu.uw.cs.lil.amr.util.lexicalgen;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.ILogicalExpressionVisitor;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.ontology.AMROntology;

/**
 * Utility to generate named entity recognition/linking candidates from a
 * labeled dataset.
 *
 * @author Yoav Artzi
 */
public class NamedEntitiesCandidates {

	private NamedEntitiesCandidates() {
		// Utility. No ctor.
	}

	public static void main(String[] args) {
		try {
			// //////////////////////////////////////////
			// Init logging
			// //////////////////////////////////////////

			Logger.DEFAULT_LOG = new Log(System.err);
			Logger.setSkipPrefix(true);
			LogLevel.INFO.set();

			// //////////////////////////////////////////
			// Init AMR.
			// //////////////////////////////////////////

			Init.init(new File(args[0]), false);

			final LogicalConstant namePredicate = LogicalConstant
					.read("c_name:<e,<txt,t>>");

			final AMROntology ontology = AMROntology.read(new File(args[1]));

			for (final SingleSentence sentence : SingleSentenceCollection.read(
					new File(args[2]), new Tokenizer())) {
				System.out.println(sentence.getSample());
				for (final Pair<String, String> entityType : GetNamedEntities
						.of(sentence.getLabel(), namePredicate)) {
					if (entityType.second() == null
							|| !ontology.isType(entityType.second())) {
						System.out.println(entityType.first());
					} else {
						System.out.println(String.format("%s\t%s",
								entityType.first(), entityType.second()));
					}
				}
				System.out.println();
			}
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static class GetNamedEntities implements ILogicalExpressionVisitor {

		private final List<Pair<String, String>>	entityTypePairs	= new LinkedList<>();
		private final LogicalConstant				namePredicate;

		public GetNamedEntities(LogicalConstant namePredicate) {
			this.namePredicate = namePredicate;
		}

		public static List<Pair<String, String>> of(LogicalExpression exp,
				LogicalConstant namePredicate) {
			final GetNamedEntities visitor = new GetNamedEntities(namePredicate);
			visitor.visit(exp);
			return visitor.entityTypePairs;
		}

		@Override
		public void visit(Lambda lambda) {
			lambda.getArgument().accept(this);
			lambda.getBody().accept(this);
		}

		@Override
		public void visit(Literal literal) {
			String namedEntity = null;
			final int len = literal.numArgs();
			for (int i = 0; i < len; ++i) {
				final LogicalExpression arg = literal.getArg(i);
				if (arg instanceof Literal
						&& ((Literal) arg).getPredicate().equals(namePredicate)
						&& ((Literal) arg).numArgs() == 2
						&& AMRServices.isTextType(((Literal) arg).getArg(1)
								.getType())) {
					namedEntity = ((LogicalConstant) ((Literal) arg).getArg(1))
							.getBaseName();
				}
			}

			if (namedEntity != null) {

				// Try to see if there's a neighboring typing predicate.
				String type = null;
				for (int i = 0; i < len; ++i) {
					final LogicalExpression arg = literal.getArg(i);
					if (arg instanceof Literal
							&& ((Literal) arg).getPredicate() instanceof LogicalConstant
							&& ((Literal) arg).numArgs() == 1) {
						if (type != null) {
							throw new IllegalStateException(
									"multiple typing predicates: " + literal);
						}
						type = ((LogicalConstant) ((Literal) arg)
								.getPredicate()).getBaseName();
					}
				}

				if (type == null) {
					entityTypePairs.add(Pair.of(namedEntity, (String) null));
				} else {
					entityTypePairs.add(Pair.of(namedEntity, type));
				}
			}

			literal.getPredicate().accept(this);
			for (int i = 0; i < len; ++i) {
				literal.getArg(i).accept(this);
			}
		}

		@Override
		public void visit(LogicalConstant logicalConstant) {
			// Nothing to do.
		}

		@Override
		public void visit(LogicalExpression logicalExpression) {
			logicalExpression.accept(this);
		}

		@Override
		public void visit(Variable variable) {
			// Nothing to do.
		}

	}

}
