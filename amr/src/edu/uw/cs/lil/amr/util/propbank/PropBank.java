package edu.uw.cs.lil.amr.util.propbank;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class PropBank {
	public static final ILogger						LOG	= LoggerFactory
																.create(PropBank.class);
	private final Set<String>						frameNames;
	private final Map<String, Set<PropBankFrame>>	frames;

	public PropBank(File dir) throws IOException {
		frames = Files
				.walk(Paths.get(dir.toURI()))
				.filter(path -> Files.isRegularFile(path))
				.filter(path -> path.toString().endsWith(".xml"))
				.flatMap(path -> PropBankReader.of(path).stream())
				.flatMap(p -> p.stream())
				.collect(
						Collectors.groupingBy(f -> f.getLemma(),
								Collectors.toSet()));
		this.frameNames = frames.values().stream().flatMap(s -> s.stream())
				.map(frame -> frame.getConstantText())
				.collect(Collectors.toSet());
	}

	public Set<PropBankFrame> getFrames(String lemma) {
		return frames.getOrDefault(lemma, Collections.emptySet());
	}

	public boolean isFrame(String frameName) {
		return frameNames.contains(frameName);
	}

}
