package edu.uw.cs.lil.amr.learn.estimators;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * A simple {@link IWeightUpdateProcedure} that scales the gradient by a
 * constant and
 * returns it.
 *
 * @author Yoav Artzi
 */
public class ScalingEstimator implements IWeightUpdateProcedure {

	public static final ILogger LOG = LoggerFactory
			.create(ScalingEstimator.class);

	private static final long serialVersionUID = 7199850456568700022L;

	private final double learningRate;

	public ScalingEstimator(double learningRate) {
		this.learningRate = learningRate;
	}

	@Override
	public boolean applyUpdate(IHashVector gradient, IHashVector weights) {
		final IHashVectorImmutable update = computeUpdate(gradient);
		if (update == null) {
			return false;
		} else {
			update.addTimesInto(1.0, weights);
			return true;
		}
	}

	@Override
	public void init() {
		// Nothing to do.
	}

	private IHashVectorImmutable computeUpdate(IHashVector gradient) {
		gradient.multiplyBy(learningRate);
		gradient.dropNoise();

		LOG.info("Update: %s", gradient);

		// Check for NaNs and super large updates
		if (gradient.isBad()) {
			LOG.error("Bad update: %s", gradient);
			throw new IllegalStateException("bad update");
		} else {
			if (!gradient.valuesInRange(-100, 100)) {
				LOG.warn("Large update");
			}
			return gradient;
		}
	}

	public static class Creator
			implements IResourceObjectCreator<ScalingEstimator> {

		private final String type;

		public Creator() {
			this("estimator.scaling");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public ScalingEstimator create(Parameters params,
				IResourceRepository repo) {
			return new ScalingEstimator(params.getAsDouble("rate", 1.0));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, ScalingEstimator.class)
					.setDescription(
							"A simple {@link IEstimator} that scales the gradient by a constant and returns it.")
					.addParam("rate", Double.class,
							"Learning rate constant, used to scale updates (default: 1.0)")
					.build();
		}
	}

}
