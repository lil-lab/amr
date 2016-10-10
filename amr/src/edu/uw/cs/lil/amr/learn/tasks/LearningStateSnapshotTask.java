package edu.uw.cs.lil.amr.learn.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.IntConsumer;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * A learning task to dump to disk various stateful objects. The goal of this
 * task is to allow to continue learning from a specific snapshot. For example,
 * this can be used to dump to disk the gradient history in AdaGrad. Learning
 * tasks usually happen after every learning iteration, but this is
 * learner-dependent.
 *
 * @author Yoav Artzi
 *
 */
public class LearningStateSnapshotTask implements IntConsumer {

	public static final ILogger LOG = LoggerFactory
			.create(LearningStateSnapshotTask.class);

	private final File directory;

	private final Map<String, Serializable> objects;

	public LearningStateSnapshotTask(Map<String, Serializable> objects,
			File directory) {
		this.objects = objects;
		this.directory = directory;
	}

	@Override
	public void accept(int epoch) {
		for (final Entry<String, Serializable> entry : objects.entrySet()) {
			final Serializable object = entry.getValue();
			final String objectId = entry.getKey();
			final File outputFile = new File(directory,
					String.format("snapshot-%s-%d.sp", objectId, epoch));

			LOG.info("Dumping %s (%s) to file: %s", objectId, object.getClass(),
					outputFile);

			try {
				final long start = System.currentTimeMillis();

				try (final OutputStream os = new FileOutputStream(outputFile);
						final OutputStream buffer = new BufferedOutputStream(
								os);
						final ObjectOutput output = new ObjectOutputStream(
								buffer)) {
					output.writeObject(object);
				}
				LOG.info("Snapshot of %s took %.3fsec", objectId,
						(System.currentTimeMillis() - start) / 1000.0);
			} catch (final IOException e) {
				LOG.error("Failed to write %s to disk to disk", objectId);
			}
		}
	}

	public static class Creator
			implements IResourceObjectCreator<LearningStateSnapshotTask> {

		private final String type;

		public Creator() {
			this("learn.task.snapshot");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public LearningStateSnapshotTask create(Parameters params,
				IResourceRepository repo) {
			final Map<String, Serializable> objects = new HashMap<>();

			for (final String objectId : params.getSplit("objects")) {
				final Object object = repo.get(objectId);
				if (object instanceof Serializable) {
					objects.put(objectId, (Serializable) object);
				} else {
					throw new IllegalArgumentException(
							"Object for snapshot task must be serializable: "
									+ objectId);
				}
			}

			return new LearningStateSnapshotTask(objects,
					params.getAsFile("dir"));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, LearningStateSnapshotTask.class)
					.setDescription(
							"Learning task to dump a snapshot of a stateful object")
					.addParam("dir", File.class, "Output directory")
					.addParam("objects", Serializable.class,
							"List of serializable objects to snapshot")
					.build();
		}

	}

}
