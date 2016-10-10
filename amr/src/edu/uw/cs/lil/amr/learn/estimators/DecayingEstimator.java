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
 * Stochastic parameter estimation update rule with an adaptive decay
 * coefficient.
 *
 * @author Yoav Artzi
 */
public class DecayingEstimator implements IWeightUpdateProcedure {

	public static final ILogger LOG = LoggerFactory
			.create(DecayingEstimator.class);

	private static final long serialVersionUID = 1355775128400941641L;

	/**
	 * Used to define the temperature of parameter updates. temp = alpha0 / (1 +
	 * c * num_update_so_far)
	 */
	private final double	alpha0;
	/**
	 * Used to define the temperature of parameter updates. temp = alpha0 / (1 +
	 * c * num_update_so_far)
	 */
	private final double	c;

	private int stocGradientNumUpdates = 0;

	public DecayingEstimator(double alpha0, double c) {
		super();
		this.alpha0 = alpha0;
		this.c = c;
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
		stocGradientNumUpdates = 0;
	}

	@Override
	public String toString() {
		return new StringBuilder("stocgrad [c=").append(c).append(", ")
				.append("alpha0=").append(alpha0).append("]").toString();
	}

	private IHashVectorImmutable computeUpdate(IHashVector gradient) {

		// Scale the update.
		final double scale = alpha0 / (1.0 + c * stocGradientNumUpdates);
		gradient.multiplyBy(scale);
		gradient.dropNoise();
		stocGradientNumUpdates++;
		LOG.info("Temperature: %f", scale);
		if (gradient.size() == 0) {
			LOG.info("Empty gradient -- skip update");
			return null;
		}

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
			implements IResourceObjectCreator<DecayingEstimator> {

		private final String type;

		public Creator() {
			this("estimator.decaying");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public DecayingEstimator create(Parameters params,
				IResourceRepository repo) {
			return new DecayingEstimator(params.getAsDouble("alpha0", 0.1),
					params.getAsDouble("c", 0.0001));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, DecayingEstimator.class)
					.setDescription(
							"Stochastic gradient parameter estimation update rule")
					.addParam("c", "double",
							"Learing rate c parameter, temperature=alpha_0/(1+c*tot_number_of_training_instances)")
					.addParam("alpha0", "double",
							"Learing rate alpha0 parameter, temperature=alpha_0/(1+c*tot_number_of_training_instances)")
					.build();
		}

	}

}
