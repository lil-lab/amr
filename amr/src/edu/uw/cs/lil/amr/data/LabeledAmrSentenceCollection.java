/*******************************************************************************
 * Copyright (C) 2011 - 2015 Yoav Artzi, All rights reserved.
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
 *******************************************************************************/
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.cornell.cs.nlp.spf.ccg.categories.ICategoryServices;
import edu.cornell.cs.nlp.spf.data.collection.IDataCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.data.situated.labeled.LabeledSituatedSentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.ccgbank.IBankParser;
import edu.uw.cs.lil.amr.ccgbank.ISuperTagger;

/**
 * Dataset of {@link LabeledSituatedSentence}s.
 *
 * @author Yoav Artzi
 */
public class LabeledAmrSentenceCollection
		implements IDataCollection<LabeledAmrSentence> {

	public static final ILogger LOG = LoggerFactory
			.create(LabeledAmrSentenceCollection.class);

	private static final long serialVersionUID = -9050523066649926171L;

	private final List<LabeledAmrSentence> data;

	public LabeledAmrSentenceCollection(SingleSentenceCollection dataset,
			ISuperTagger superTagger,
			ICategoryServices<LogicalExpression> categoryServices,
			IBankParser bankParser) {
		this.data = Collections.unmodifiableList(StreamSupport
				.stream(Spliterators.spliterator(dataset.iterator(),
						dataset.size(), Spliterator.IMMUTABLE), true)
				.map((di) -> {
					return new LabeledAmrSentence(
							new SituatedSentence<>(di.getSample(),
									new AMRMeta(di.getSample())),
							di.getLabel(), di.getProperties(), categoryServices,
							superTagger, bankParser);
				}).collect(Collectors.toList()));
		LOG.info("Init %s :: data.size=%d", getClass(), data.size());
	}

	@Override
	public Iterator<LabeledAmrSentence> iterator() {
		return data.iterator();
	}

	@Override
	public int size() {
		return data.size();
	}

	public static class Creator
			implements IResourceObjectCreator<LabeledAmrSentenceCollection> {

		private final String type;

		public Creator() {
			this("data.amr.labeled");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public LabeledAmrSentenceCollection create(Parameters params,
				IResourceRepository repo) {
			final SingleSentenceCollection rawData = (SingleSentenceCollection) repo
					.get(params.get("data"));

			if (params.contains("cache")) {
				final File cacheFile = params.getAsFile("cache");
				if (cacheFile.exists()) {
					try (ObjectInput input = new ObjectInputStream(
							new BufferedInputStream(
									new FileInputStream(cacheFile)))) {
						final LabeledAmrSentenceCollection dataset = (LabeledAmrSentenceCollection) input
								.readObject();

						// Basic checks to see the cached copy is up to date.
						// This doesn't guarantee anything, but can catch some
						// basic cases.
						boolean valid = true;
						valid &= dataset.size() == rawData.size();
						final Iterator<LabeledAmrSentence> cachedIterator = dataset
								.iterator();
						final Iterator<SingleSentence> rawIterator = rawData
								.iterator();
						while (valid && cachedIterator.hasNext()
								&& rawIterator.hasNext()) {
							final LabeledAmrSentence cached = cachedIterator
									.next();
							final SingleSentence raw = rawIterator.next();
							valid &= cached.getLabel().equals(raw.getLabel());
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

			final LabeledAmrSentenceCollection dataset = new LabeledAmrSentenceCollection(
					rawData,
					params.contains("tagger") ? repo.get(params.get("tagger"))
							: null,
					repo.get(
							ParameterizedExperiment.CATEGORY_SERVICES_RESOURCE),
					params.contains("bankParser")
							? repo.get(params.get("bankParser")) : null);

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
			return new ResourceUsage.Builder(type,
					LabeledAmrSentenceCollection.class)
							.setDescription("Data set of labeled AMR sentences")
							.addParam("data", SingleSentenceCollection.class,
									"Dataset of sentences paired with logical forms to convert to AMR data.")
							.addParam("tagger", ISuperTagger.class,
									"CCGBank super tagger (default: none)")
							.addParam("bankParser", IBankParser.class,
									"CCGBank parser (default: none)")
							.addParam("cache", File.class,
									"Caching file (default: none)")
							.build();
		}

	}

}
