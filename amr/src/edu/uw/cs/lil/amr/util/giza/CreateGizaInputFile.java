/*******************************************************************************
 * UW SPF - The University of Washington Semantic Parsing Framework
 * <p>
 * Copyright (C) 2013 Yoav Artzi
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
 ******************************************************************************/
package edu.uw.cs.lil.amr.util.giza;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import jregex.Pattern;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoringServices;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsMultiSet;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class CreateGizaInputFile {

	private static final Pattern	PUNCTUATION_PATTERN	= new Pattern(
																"[.;:-_+='`]");

	public static void main(String[] args) throws IOException {
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

		// //////////////////////////////////////////
		// Print output file.
		// //////////////////////////////////////////

		// Read input data.
		final SingleSentenceCollection data = SingleSentenceCollection.read(new File(
				args[1]), new Tokenizer());

		for (final SingleSentence sentence : data) {
			System.out.println(sentence.getSample().getTokens().toList()
					.stream().filter(CreateGizaInputFile::useToken)
					.map(String::toLowerCase).collect(Collectors.joining(" ")));
			System.out.println(GetConstantsMultiSet
					.of(AMRServices.underspecifyAndStrip(sentence.getLabel()))
					.stream().filter(CreateGizaInputFile::useConstant)
					.map(LogicalConstant::toString)
					.collect(Collectors.joining(" ")));
			System.out.println();
		}

	}

	private static boolean useConstant(LogicalConstant constant) {
		return FactoringServices.isFactorable(constant)
				&& !AMRServices.isAmrRelation(constant);
	}

	private static boolean useToken(String token) {
		if (token.equals("-RRB-") || token.equals("-LRB-")) {
			return false;
		}

		if (PUNCTUATION_PATTERN.matches(token)) {
			return false;
		}

		return true;
	}

}
