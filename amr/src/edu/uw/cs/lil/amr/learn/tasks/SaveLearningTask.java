package edu.uw.cs.lil.amr.learn.tasks;

import java.io.File;
import java.io.IOException;
import java.util.function.IntConsumer;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelImmutable;
import edu.cornell.cs.nlp.spf.parser.ccg.model.Model;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class SaveLearningTask implements IntConsumer {

	public static final ILogger			LOG	= LoggerFactory
													.create(SaveLearningTask.class);
	private final File					directory;
	private final String				filePrefix;

	private final IModelImmutable<?, ?>	model;

	public SaveLearningTask(IModelImmutable<?, ?> model, File directory,
			String filePrefix) {
		this.model = model;
		this.directory = directory;
		this.filePrefix = filePrefix;
	}

	@Override
	public void accept(int epoch) {
		final File outputFile = new File(directory, String.format("%s%d.sp",
				filePrefix, epoch));
		LOG.info("Saving model to %s", outputFile);

		try {
			final long start = System.currentTimeMillis();
			Model.write(model, outputFile);
			LOG.info("Model saving time: %.3fsec",
					(System.currentTimeMillis() - start) / 1000.0);
		} catch (final IOException e) {
			LOG.error("Failed to write model to disk");
		}
	}

	public static class Creator implements
			IResourceObjectCreator<SaveLearningTask> {

		private final String	type;

		public Creator() {
			this("learn.task.save");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public SaveLearningTask create(Parameters params,
				IResourceRepository repo) {
			return new SaveLearningTask(repo.get(params.get("model")),
					params.getAsFile("dir"), params.get("prefix"));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, SaveLearningTask.class)
					.setDescription("Model saving learning task")
					.addParam("model", IModelImmutable.class,
							"The model to save to disk")
					.addParam("dir", File.class, "Target directory")
					.addParam("prefix", String.class, "Output file name prefix")
					.build();
		}

	}

}
