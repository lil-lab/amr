package edu.uw.cs.lil.amr.util.convert;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import edu.cornell.cs.nlp.spf.base.properties.Properties;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.lambda.convert.LogicalExpressionToAmr;

/**
 * Utility to convert sentences paired with AMRs to
 * {@link SingleSentenceCollection} and write to a file. The input AMR file must be
 * simplified where each AMR is on a single line (and not indented on multiple
 * lines). See the script txt2amr.py to simplify AMR files.
 *
 * @author Yoav Artzi
 */
public class LambdaToAMR {
	public static final ILogger	LOG	= LoggerFactory.create(LambdaToAMR.class);

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

		Init.init(/* "../resources/amr.types" */new File(args[0]), true);

		// //////////////////////////////////////////
		// Read the input file, convert each sample and write to output.
		// //////////////////////////////////////////

		final int lineNumber = -1;
		final boolean doIndent = args.length > 3 && args[3].equals("indent");
		try (PrintStream out = new PrintStream(new File(args[2]));) {
			for (final SingleSentence dataItem : SingleSentenceCollection.read(
					new File(args[1]), new Tokenizer())) {
				final String amr = LogicalExpressionToAmr.of(
						dataItem.getLabel(), doIndent);
				if (amr == null) {
					LOG.error("Conversion failed");
				} else {
					out.println(dataItem.getSample());
					out.println(Properties.toString(dataItem.getProperties()));
					out.println(amr);
					out.println();
				}
			}
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		} catch (final Exception e) {
			throw new RuntimeException("Exception in line: " + lineNumber, e);
		}

	}
}
