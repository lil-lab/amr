package edu.uw.cs.lil.amr.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.sentence.SentenceCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class AmrSentenceCollection
		implements IDataCollection<SituatedSentence<AMRMeta>> {

	public static final ILogger LOG = LoggerFactory
			.create(AmrSentenceCollection.class);

	private static final long serialVersionUID = -4173482722436057009L;

	private final List<SituatedSentence<AMRMeta>> entries;

	public AmrSentenceCollection(SentenceCollection collection) {
		this.entries = StreamSupport
				.stream(Spliterators.spliterator(collection.iterator(),
						collection.size(), Spliterator.IMMUTABLE), true)
				.map(s -> new SituatedSentence<>(s, new AMRMeta(s)))
				.collect(Collectors.toList());
	}

	@Override
	public Iterator<SituatedSentence<AMRMeta>> iterator() {
		return entries.iterator();
	}

	@Override
	public int size() {
		return entries.size();
	}

	public static class Creator
			implements IResourceObjectCreator<AmrSentenceCollection> {

		private final String type;

		public Creator() {
			this("data.amr");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AmrSentenceCollection create(Parameters params,
				IResourceRepository repo) {
			final SentenceCollection rawData = (SentenceCollection) repo
					.get(params.get("data"));

			if (params.contains("cache")) {
				final File cacheFile = params.getAsFile("cache");
				if (cacheFile.exists()) {
					try (ObjectInput input = new ObjectInputStream(
							new BufferedInputStream(
									new FileInputStream(cacheFile)))) {
						final AmrSentenceCollection dataset = (AmrSentenceCollection) input
								.readObject();

						// Basic checks to see the cached copy is up to date.
						// This doesn't guarantee anything, but can catch some
						// basic cases.
						boolean valid = true;
						valid &= dataset.size() == rawData.size();
						final Iterator<SituatedSentence<AMRMeta>> cachedIterator = dataset
								.iterator();
						final Iterator<Sentence> rawIterator = rawData
								.iterator();
						while (valid && cachedIterator.hasNext()
								&& rawIterator.hasNext()) {
							final SituatedSentence<AMRMeta> cached = cachedIterator
									.next();
							final Sentence raw = rawIterator.next();
							valid &= cached.getSample().getSample().getTokens()
									.equals(raw.getSample().getTokens());
						}
						valid &= cachedIterator.hasNext() == rawIterator
								.hasNext();

						if (valid) {
							LOG.info("Using cached copy: %s", cacheFile);
							return dataset;
						} else {
							LOG.info("Cached copy invalid, re-processing");
						}
					} catch (IOException | ClassNotFoundException e) {
						// Ignore and continue.
						LOG.info(
								"Exception when loading cached copy, re-processing");
					}
				} else {
					LOG.info("Cached file missing, re-processing");
				}
			}

			final AmrSentenceCollection dataset = new AmrSentenceCollection(
					rawData);

			if (params.contains("cache")) {
				try (final OutputStream os = new FileOutputStream(
						params.getAsFile("cache"));
						final OutputStream buffer = new BufferedOutputStream(
								os);
						final ObjectOutput output = new ObjectOutputStream(
								buffer)) {
					output.writeObject(dataset);
					LOG.info("Cached to: %s", params.get("cache"));
				} catch (final IOException e) {
					LOG.info("Failed to cache to: %s", params.get("cache"));
					LOG.info(e);
				}
			}

			return dataset;
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type, AmrSentenceCollection.class)
					.setDescription("Collection of AMR sentences")
					.addParam("data", SingleSentenceCollection.class,
							"Collection of sentences to convert to AMR data.")
					.addParam("cache", File.class,
							"Caching file (default: none)")
					.build();
		}

	}

}
