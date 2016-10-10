package edu.uw.cs.lil.amr.util.mapping;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import jregex.Pattern;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.GetConstantsSet;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.Init;
import edu.uw.cs.lil.amr.data.Tokenizer;

/**
 * Generates a mapping of fully-specified {@link LogicalConstant} to their
 * underspecified form and vice-versa. The mapping is done by applying the
 * following regex ordered rules (where T is a {@link Type}):
 * <ul>
 * <li>c_ARG\d+:T -> c_ARGX:T</li>
 * <li>c_ARG\d+-of:T -> c_ARGX-of:T</li>
 * <li>c_op({num}\d+):T -> c_op{\num}:T (stays the same)</li>
 * <li>c_snt({num}\d+):T -> c_snt{\num}:T (stays the same)</li>
 * <li>c_.+-of:T -> c_REL-of:T</li>
 * <li>c_.+:T -> c_REL:T</li>
 * </ul>
 * The mapping is written into STDOUT and includes a mapping strings, as typing
 * is preserved when mapping.
 *
 * @author Yoav Artzi
 */
public class SpecifierMapper {
	public static final ILogger			LOG		= LoggerFactory
														.create(SpecifierMapper.class);
	private static final Pattern		ARGX	= new Pattern("c_ARG\\d+");
	private static final Pattern		ARGX_OF	= new Pattern("c_ARG\\d+-of");
	private static final Set<String>	EXCEPTIONS;
	private static final Pattern		OP		= new Pattern("c_op\\d+");
	private static final Pattern		REL		= new Pattern("c_.+");

	private static final Pattern		REL_OF	= new Pattern("c_.+-of");
	private static final Pattern		SNT		= new Pattern("c_snt\\d+");

	static {
		final Set<String> excepts = new HashSet<>();
		excepts.add("c_calendar");
		excepts.add("c_century");
		excepts.add("c_day");
		excepts.add("c_dayperiod");
		excepts.add("c_decade");
		excepts.add("c_era");
		excepts.add("c_month");
		excepts.add("c_quarter");
		excepts.add("c_season");
		excepts.add("c_timezone");
		excepts.add("c_weekday");
		excepts.add("c_year");
		excepts.add("c_year2");
		EXCEPTIONS = Collections.unmodifiableSet(excepts);

	}

	public static void main(String[] args) throws IOException {
		Init.init(new File(args[0]), false);

		final Map<String, Set<String>> mapping = new HashMap<>();
		for (int i = 1; i < args.length; ++i) {
			LOG.info("Processing %s ...", args[i]);
			for (final SingleSentence dataItem : SingleSentenceCollection.read(
					new File(args[i]), new Tokenizer())) {
				for (final LogicalConstant constant : GetConstantsSet
						.of(dataItem.getLabel())) {
					// Verify type <?,<?,t>>.
					if (constant.getType().isComplex()
							&& constant.getType().getRange().isComplex()
							&& LogicLanguageServices
									.getTypeRepository()
									.getTruthValueType()
									.equals(constant.getType().getRange()
											.getRange())) {
						final String underspecBaseName = map(constant
								.getBaseName());
						if (underspecBaseName != null) {
							if (!mapping.containsKey(underspecBaseName)) {
								mapping.put(underspecBaseName, new HashSet<>());
							}
							mapping.get(underspecBaseName).add(
									constant.getBaseName());
						}
					}
				}
			}
		}
		for (final Entry<String, Set<String>> entry : mapping.entrySet()) {
			System.out.println(String.format("%s\t%s", entry.getKey(), entry
					.getValue().stream().collect(Collectors.joining(","))));
		}
	}

	private static String map(String baseName) {
		if (EXCEPTIONS.contains(baseName)) {
			return null;
		} else if (ARGX.matches(baseName)) {
			return "c_ARGX";
		} else if (ARGX_OF.matches(baseName)) {
			return "c_ARGX-of";
		} else if (OP.matches(baseName) || SNT.matches(baseName)) {
			return null;
		} else if (REL_OF.matches(baseName)) {
			return "c_REL-of";
		} else if (REL.matches(baseName)) {
			return "c_REL";
		} else {
			throw new IllegalArgumentException("Invalid base name: " + baseName);
		}
	}

}
