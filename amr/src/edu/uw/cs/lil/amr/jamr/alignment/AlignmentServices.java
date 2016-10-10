package edu.uw.cs.lil.amr.jamr.alignment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.composites.Pair;

/**
 * Services to handle (read and store) alignment information.
 *
 * @author Yoav Artzi
 */
public class AlignmentServices {

	public static final String	STORED_ALIGNMENTS_PROPERTY	= "alignedExp";

	public static String alignmentsToString(
			Map<Pair<Integer, Integer>, Set<LogicalExpression>> alignmentMap) {
		return alignmentMap
				.entrySet()
				.stream()
				.map(entry -> String.format("%d-%d##", entry.getKey().first(),
						entry.getKey().second())
						+ entry.getValue().stream().map(Object::toString)
								.collect(Collectors.joining("##")))
				.collect(Collectors.joining("####"));
	}

	public static Pair<Pair<Integer, Integer>, String> readAlignment(
			String string) {
		final String[] alignmentSplit = string.split("\\|");
		if (alignmentSplit.length != 2) {
			System.out.println("boo");
		}
		final String tokenRange = alignmentSplit[0];
		final String graphIndices = alignmentSplit[1];
		final String[] rangeSplit = tokenRange.split("-");
		final int start = Integer.valueOf(rangeSplit[0]);
		final int end = Integer.valueOf(rangeSplit[1]);
		return Pair.of(Pair.of(start, end), graphIndices);
	}

	public static Map<Pair<Integer, Integer>, Set<LogicalExpression>> readStoredAlignments(
			String alignmentExpString,
			ICategoryServices<LogicalExpression> categoryServices) {
		final Map<Pair<Integer, Integer>, Set<LogicalExpression>> map = new HashMap<>();
		for (final String alignmentString : alignmentExpString.split("####")) {
			final String[] split = alignmentString.split("##");
			final String[] rangeSplit = split[0].split("-");
			map.put(Pair.of(Integer.valueOf(rangeSplit[0]),
					Integer.valueOf(rangeSplit[1])),
					Arrays.stream(Arrays.copyOfRange(split, 1, split.length))
							.map(s -> categoryServices.readSemantics(s))
							.collect(Collectors.toSet()));

		}
		return map;
	}

	public static Set<String> splitIndexSet(String indexSet) {
		return new HashSet<>(Arrays.asList(indexSet.split("\\+")));
	}

}
