package edu.uw.cs.lil.amr;

import java.io.IOException;

import org.apache.commons.cli.ParseException;

import edu.uw.cs.lil.amr.exp.AmrGenericExperiment;
import edu.uw.cs.lil.amr.util.convert.AMRToLambda;
import edu.uw.cs.lil.amr.util.convert.LambdaToAMR;
import edu.uw.cs.lil.amr.util.giza.CreateGizaInputFile;
import edu.uw.cs.lil.amr.util.mapping.SpecifierMapper;
import edu.uw.cs.lil.amr.util.parseutil.AMRTestParseUtil;

/**
 * Main entry point for the AMR package.
 *
 * @author Yoav Artzi
 */
public class Main {

	private Main() {
		// Service class. No ctor.
	}

	public static void main(String[] args) throws IOException, ParseException {
		if (args.length == 0) {
			usage();
		} else if ("test".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			System.arraycopy(args, 1, parseArgs, 0, parseArgs.length);
			AMRTestParseUtil.main(parseArgs);
		} else if ("amr2lam".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			System.arraycopy(args, 1, parseArgs, 0, parseArgs.length);
			AMRToLambda.main(parseArgs);
		} else if ("lam2amr".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			System.arraycopy(args, 1, parseArgs, 0, parseArgs.length);
			LambdaToAMR.main(parseArgs);
		} else if ("underspec".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			System.arraycopy(args, 1, parseArgs, 0, parseArgs.length);
			SpecifierMapper.main(parseArgs);
		} else if ("gizaprep".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			System.arraycopy(args, 1, parseArgs, 0, parseArgs.length);
			CreateGizaInputFile.main(parseArgs);
		} else if ("parse".equals(args[0])) {
			final String[] parseArgs = new String[args.length - 1];
			int i = 1;
			for (int j = 1; j < args.length; ++j) {
				if (args[j].startsWith("rootDir=")) {
					parseArgs[0] = args[j].split("=", 2)[1]
							+ "/experiments/parse/parse.exp";
				} else {
					parseArgs[i++] = args[j];
				}
			}
			AmrGenericExperiment.main(parseArgs);
		} else {
			AmrGenericExperiment.main(args);
		}
	}

	private static void usage() {
		System.out.println("Usage:");
		System.out.println(
				"... parse rootDir=<dist root> modelFile=<.sp model> sentences=<input file> [logLevel=DEBUG|INFO|DEV|WARN|ERROR] [allowSloppy=true|false]");
		System.out.println(
				"\tParses input sentences using a pre-trained model. All paths must be absolute. The output and logs will be available in experiments/parse/logs.");
		System.out.println(
				"\trootDir: The root directory of the AMR repository.");
		System.out.println("\tmodelFile: Pre-trained .sp AMR model.");
		System.out.println(
				"\tsentences: Input file with a raw sentence on each line.");
		System.out
				.println("\tlogLevel: System logging level (default: ERROR).");
		System.out.println(
				"\tallowSloppy: Enable two-pass inference with second sloppy pass (default: true).");
		System.out
				.println("... amr2lam <types_file> <input_file> <output_file>");
		System.out.println(
				"\tConvert AMR data file to a .lam file that pairs sentences with logical forms (raw AMR must be pre-processed).");
		System.out.println(
				"... lam2amr <types_file> <input_file> <output_file> [indent]");
		System.out.println(
				"\tConvert a file of sentences paired with logical forms to sentences paired with AMRs.");
		System.out.println("... test <exp_file> <data_1> <data_2> ... ");
		System.out.println(
				"\tTest parsing util. exp_file contains defintions and parser setup.");
		System.out.println("... underspec <types_file> <data_1> <data_2> ...");
		System.out.println(
				"\tUtility to create the logical constant underspecification maping.");
		System.out.println("... <amr_exp_file>");
		System.out.println("\tRun AMR experiment.");
	}

}
