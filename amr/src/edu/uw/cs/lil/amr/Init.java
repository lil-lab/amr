/*******************************************************************************
 * Copyright (C) 2011 - 2015 Yoav Artzi, All rights reserved.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *******************************************************************************/
package edu.uw.cs.lil.amr;

import java.io.File;
import java.io.IOException;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory.Type;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.SyntaxAttributeTyping;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.mr.lambda.FlexibleTypeComparator;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices.Builder;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpressionReader;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemId;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.printers.LogicalExpressionToIndentedString;
import edu.cornell.cs.nlp.spf.mr.language.type.TypeRepository;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.SortConjunctions;
import edu.uw.cs.lil.amr.lambda.SpecificationMapping;
import edu.uw.cs.lil.amr.ner.IllinoisNERWrapper;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;

/**
 * Common initialization for AMR utilities.
 *
 * @author Yoav Artzi
 */
public class Init {
	public static final ILogger LOG = LoggerFactory.create(Init.class);

	private Init() {
		// Service class.
	}

	public static void init(File typesFile, boolean indentLogicalForms)
			throws IOException {
		init(typesFile, null, null, indentLogicalForms, null, null, null,
				false);
	}

	public static void init(File typesFile, File specmapFile,
			File stanfordModel, boolean indentLogicalForms, File nerConfig,
			File nerTranslation, File propBankDir, boolean underspecifyPropBank)
					throws IOException {

		// //////////////////////////////////////////
		// Use tree hash vector
		// //////////////////////////////////////////

		HashVectorFactory.DEFAULT = Type.FAST_TREE;

		// //////////////////////////////////////////
		// Init lambda calculus system.
		// //////////////////////////////////////////

		try {
			// Init the logical expression type system
			final Builder builder = new LogicLanguageServices.Builder(
					new TypeRepository(typesFile), new FlexibleTypeComparator())
							.setUseOntology(true).setNumeralTypeName("i")
							.closeOntology(false);
			if (indentLogicalForms) {
				builder.setPrinter(
						new LogicalExpressionToIndentedString.Printer());
			}
			LogicLanguageServices.setInstance(builder.build());
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		// //////////////////////////////////////////
		// Skolem services.
		// //////////////////////////////////////////

		SkolemServices.setInstance(new SkolemServices.Builder(
				LogicLanguageServices.getTypeRepository().getType("id"),
				LogicalConstant.read("na:id")).build());
		LogicalExpressionReader.register(new SkolemId.Reader());

		// //////////////////////////////////////////
		// AMR services.
		// //////////////////////////////////////////

		final AMRServices.Builder builder = new AMRServices.Builder("a",
				LogicLanguageServices.getTypeRepository().getType("txt"), "ref",
				stanfordModel, "c_op", LogicalConstant.read("DUMMY:e"),
				LogicalConstant.read("name:<e,t>"), LogicLanguageServices
						.getTypeRepository().getTypeCreateIfNeeded("<e,t>"));

		if (nerConfig != null) {
			try {
				LOG.info("Initializing named entity recognizer...");
				builder.setNamedEntityRecognizer(
						new IllinoisNERWrapper(nerConfig, nerTranslation));
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}

		if (propBankDir != null) {
			builder.setPropBankDir(propBankDir);
		}

		if (specmapFile != null) {
			builder.setSpecificationMapping(SpecificationMapping
					.read(specmapFile, underspecifyPropBank));
		}
		AMRServices.setInstance(builder.build());

		// //////////////////////////////////////////////////
		// Lexical factoring services.
		// //////////////////////////////////////////////////

		FactoringServices
				.set(new FactoringServices.Builder().setHashCodeOrdering(false)
						.addConstant(LogicLanguageServices.getTrue())
						.addConstant(LogicLanguageServices.getFalse())
						.setPreprocessor(c -> c.cloneWithNewSemantics(
								SortConjunctions.of(c.getSemantics())))
				.setFilter(
						(LogicalConstant c) -> !AMRServices.isAmrRelation(c)
								&& !c.getType()
										.equals(SkolemServices.getIDType())
						&& !c.equals(AMRServices.getDummyEntity())
						&& !AMRServices.isSkolemPredicate(c)
						&& !AMRServices.isRefPredicate(c)
						&& !c.equals(SkolemServices.getIdPlaceholder()))
				.build());

		// //////////////////////////////////////////
		// AMR coordination services.
		// //////////////////////////////////////////

		CoordinationServices.set(new CoordinationServices("c_op",
				LogicalConstant.read("and:<e,t>"),
				LogicalConstant.read("or:<e,t>"), "conj", "disj",
				Syntax.read("NP[pl]")));

		// //////////////////////////////////////////
		// Register custom syntactic types.
		// //////////////////////////////////////////

		Syntax.register(AMRServices.TXT);
		Syntax.register(AMRServices.I);
		Syntax.register(AMRServices.AMR);
		Syntax.register(AMRServices.ID);
		Syntax.register(AMRServices.KEY);

		// //////////////////////////////////////////
		// Syntax-attribute typing constraints.
		// //////////////////////////////////////////

		SyntaxAttributeTyping.setInstance(new SyntaxAttributeTyping.Builder()
				.addAllowedSyntax("pl", Syntax.NP)
				.addAllowedSyntax("sg", Syntax.NP)
				.addAllowedSyntax("sg", Syntax.N)
				.addAllowedSyntax("pl", Syntax.N)
				.addAllowedSyntax("nb", Syntax.N)
				.addAllowedSyntax("date", Syntax.N).build());

	}
}
