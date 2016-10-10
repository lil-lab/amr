package edu.uw.cs.lil.amr.lambda;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.language.type.Type;
import edu.uw.cs.lil.amr.util.mapping.SpecifierMapper;
import edu.uw.cs.lil.amr.util.propbank.PropBankFrame;

/**
 * Mapping between specified and underspecified constants.
 *
 * @author Yoav Artzi
 * @see SpecifierMapper
 */
public class SpecificationMapping {
	private static String					PROPBANK_ROLE_SEP		= "-";
	private static String					PROPBANK_ROLE_WILDCARD	= PROPBANK_ROLE_SEP
			+ "XX";

	private final Map<String, Set<String>>	assignmentMapping;
	private final boolean					underspecifyPropBank;

	private final Map<String, String>		underspecMapping;

	public SpecificationMapping(boolean underspecifyPropBank) {
		this(new HashMap<>(), underspecifyPropBank);
	}

	public SpecificationMapping(Map<String, Set<String>> mapping,
			boolean underspecifyPropBank) {
		this.assignmentMapping = mapping;
		this.underspecifyPropBank = underspecifyPropBank;
		final Map<String, String> uMapping = new HashMap<>();
		mapping.entrySet().stream().forEach((e) -> {
			e.getValue().stream().forEach((c) -> uMapping.put(c, e.getKey()));
		});
		this.underspecMapping = Collections.unmodifiableMap(uMapping);
	}

	public static SpecificationMapping read(File file,
			boolean underspecifyPropBank) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			final Map<String, Set<String>> mapping = new HashMap<>();
			String line;
			while ((line = reader.readLine()) != null) {
				final String[] split = line.split("\t");
				mapping.put(split[0], Collections.unmodifiableSet(
						new HashSet<>(Arrays.asList(split[1].split(",")))));
			}
			return new SpecificationMapping(mapping, underspecifyPropBank);
		}
	}

	public Set<LogicalExpression> getAssignments(
			LogicalConstant underspecifiedConstant) {
		final LogicalConstant stripped = OverloadedLogicalConstant
				.getWrapped(underspecifiedConstant);
		final String baseName = stripped.getBaseName();
		final Type type = stripped.getType();
		if (assignmentMapping.containsKey(baseName)) {
			return assignmentMapping.get(baseName).stream()
					.map((name) -> LogicalConstant.create(name, type, true))
					.collect(Collectors.toSet());
		} else if (underspecifyPropBank
				&& baseName.endsWith(PROPBANK_ROLE_WILDCARD)) {
			final String lemma = baseName.substring(0,
					baseName.length() - PROPBANK_ROLE_WILDCARD.length());
			final Set<PropBankFrame> frames = AMRServices
					.getPropBankFrames(lemma);
			if (frames.isEmpty()) {
				return Collections.emptySet();
			}
			return frames.stream()
					.map(frame -> LogicalConstant.create(
							lemma + PROPBANK_ROLE_SEP
									+ String.format("%02d", frame.getId()),
							type, true))
					.collect(Collectors.toSet());
		} else {
			return Collections.emptySet();
		}
	}

	public boolean isUnderspecified(LogicalConstant constant) {
		final String baseName = OverloadedLogicalConstant.getWrapped(constant)
				.getBaseName();

		// Case of underspecified relation.
		if (assignmentMapping.containsKey(baseName)) {
			return true;
		}

		// Case of underspecified PropBank predicate.
		if (underspecifyPropBank && baseName.endsWith(PROPBANK_ROLE_WILDCARD)
				&& !AMRServices
						.getPropBankFrames(baseName.substring(0,
								baseName.length()
										- PROPBANK_ROLE_WILDCARD.length()))
						.isEmpty()) {
			return true;
		}

		return false;
	}

	public LogicalConstant underspecify(LogicalConstant constant) {
		final String baseName = constant.getBaseName();

		// Verify type <?,<?,t>>.
		if (constant.getType().isComplex()
				&& constant.getType().getRange().isComplex()
				&& LogicLanguageServices.getTypeRepository().getTruthValueType()
						.equals(constant.getType().getRange().getRange())
				&& underspecMapping.containsKey(baseName)) {
			return LogicalConstant.create(underspecMapping.get(baseName),
					constant.getType(), true);
		}

		// Verify type <?,t>.
		if (underspecifyPropBank && constant.getType().isComplex()
				&& constant.getType().getRange().equals(LogicLanguageServices
						.getTypeRepository().getTruthValueType())) {
			// If this is a PropBank frame, underspecify.
			if (AMRServices.isPropBankFrame(baseName)) {
				final String lemma = baseName.substring(0,
						baseName.length() - PROPBANK_ROLE_WILDCARD.length());
				if (!AMRServices.getPropBankFrames(lemma).isEmpty()) {
					return LogicalConstant.create(
							lemma + PROPBANK_ROLE_WILDCARD, constant.getType(),
							true);
				}
			}
		}

		return constant;
	}
}
