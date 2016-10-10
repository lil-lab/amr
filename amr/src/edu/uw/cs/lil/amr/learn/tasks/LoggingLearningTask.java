package edu.uw.cs.lil.amr.learn.tasks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.function.IntConsumer;

import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelImmutable;
import edu.cornell.cs.nlp.spf.parser.ccg.model.ModelLogger;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * Learning task that logs the model to a file.
 *
 * @author Yoav Artzi
 */
public class LoggingLearningTask implements IntConsumer {

	public static final ILogger			LOG	= LoggerFactory
			.create(LoggingLearningTask.class);
	private final String				baseName;
	private final ModelLogger			logger;
	private final IModelImmutable<?, ?>	model;

	private final File path;

	public LoggingLearningTask(ModelLogger logger, File path, String baseName,
			IModelImmutable<?, ?> model) {
		this.logger = logger;
		this.path = path;
		this.baseName = baseName;
		this.model = model;
	}

	@Override
	public void accept(int epochNumber) {
		try (PrintStream out = new PrintStream(
				new File(path, baseName + epochNumber + ".log"))) {
			logger.log(model, out);
		} catch (final FileNotFoundException e) {
			LOG.error("Model logging failed");
		}
	}

	public static class Creator
			implements IResourceObjectCreator<LoggingLearningTask> {

		private final String type;

		public Creator() {
			this("learn.task.log");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public LoggingLearningTask create(Parameters params,
				IResourceRepository repo) {
			return new LoggingLearningTask(repo.get(params.get("logger")),
					params.getAsFile("path"), params.get("base"),
					repo.get(params.get("model")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, LoggingLearningTask.class)
					.setDescription("Learning task to log a model to a file")
					.addParam("model", IModelImmutable.class,
							"The model to log")
					.addParam("base", String.class,
							"The base name of the log file")
					.addParam("path", File.class, "Output path")
					.addParam("logger", ModelLogger.class, "Model logger")
					.build();
		}

	}
}
