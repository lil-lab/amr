package edu.uw.cs.lil.amr.ontology;

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

/**
 * An AMR ontology. The AMR ontology is a shallow ordering of type. It has two
 * layers. The first layer includes root types, and each root type as a set of
 * type. The sets are disjoint and the root type is a member of its set.
 *
 * @author Yoav Artzi
 */
public class AMROntology {

	private final Set<String>				allTypes;
	private final Map<String, Set<String>>	typeMapping;

	public AMROntology(Map<String, Set<String>> typeMapping) {
		this.typeMapping = typeMapping;
		final Set<String> types = new HashSet<>();
		for (final Set<String> set : typeMapping.values()) {
			types.addAll(set);
		}
		this.allTypes = Collections.unmodifiableSet(types);
	}

	public static AMROntology read(File file) throws IOException {
		final Map<String, Set<String>> typeMapping = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				final String[] split = line.split("\t");
				typeMapping.put(split[0], Collections
						.unmodifiableSet(new HashSet<>(Arrays.asList(split))));
			}
			return new AMROntology(typeMapping);
		}
	}

	/**
	 * The set of all root types.
	 */
	public Set<String> getRootTypes() {
		return Collections.unmodifiableSet(typeMapping.keySet());
	}

	/**
	 * All types in the ontology in a single set.
	 */
	public Set<String> getTypes() {
		return allTypes;
	}

	/**
	 * All types for a single root type.
	 */
	public Set<String> getTypes(String rootType) {
		return typeMapping.get(rootType);
	}

	/**
	 * Checks if a given string represents a root type in this ontology.
	 */
	public boolean isRootType(String type) {
		return typeMapping.containsKey(type);
	}

	/**
	 * Checks if a given string represents a type in this ontology.
	 */
	public boolean isType(String type) {
		return allTypes.contains(type);
	}

}
