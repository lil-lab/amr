package edu.uw.cs.lil.amr.util.dataprep;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.Log;
import edu.cornell.cs.nlp.utils.log.LogLevel;
import edu.cornell.cs.nlp.utils.log.Logger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;

/**
 * Split a data file.
 *
 * @author Yoav Artzi
 */
public class SplitFolds {
	public static final ILogger	LOG	= LoggerFactory.create(SplitFolds.class);

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

		// Output dir.
		final File outputDir = new File(args[1]);

		// Read data.
		final List<SingleSentence> data = new LinkedList<>();
		final File inputFile = new File(args[2]);
		for (final SingleSentence dataItem : SingleSentenceCollection.read(
				inputFile, new Tokenizer())) {
			data.add(dataItem);
		}

		// Split to folds.
		final int numFolds = 5;
		final List<List<SingleSentence>> folds = new ArrayList<>(numFolds);
		for (int i = 0; i < numFolds; ++i) {
			folds.add(new LinkedList<SingleSentence>());
		}
		Collections.shuffle(data);
		int i = 0;
		for (final SingleSentence dataItem : data) {
			folds.get(i % numFolds).add(dataItem);
			++i;
		}

		// Write files.
		try {
			for (i = 0; i < numFolds; ++i) {
				write(folds.get(i),
						new File(outputDir, String.format(
								"%s.fold%d.lam",
								inputFile.getName().substring(0,
										inputFile.getName().lastIndexOf(".")),
								i)));
			}
		} catch (final FileNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void write(List<SingleSentence> data, File outputFile)
			throws FileNotFoundException {
		try (PrintStream out = new PrintStream(outputFile)) {

			for (final SingleSentence dataItem : data) {
				out.println(dataItem);
				out.println();
			}
		}
	}

}
