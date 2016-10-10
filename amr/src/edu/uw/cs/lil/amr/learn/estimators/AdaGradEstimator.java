package edu.uw.cs.lil.amr.learn.estimators;

import java.io.Serializable;
import java.util.function.IntFunction;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

/**
 * The AdaGrad update rule from Duchi et al. 2010. Also see Notes on AdaGrad by
 * Chris Dyer.
 *
 * @author Yoav Artzi
 */
public class AdaGradEstimator extends AbstractAdaGradEstimator {

	public static final ILogger	LOG					= LoggerFactory
			.create(AdaGradEstimator.class);
	private static final long	serialVersionUID	= -5037390883131512545L;

	public AdaGradEstimator(boolean initHistory,
			IntFunction<Double> rateFunction) {
		super(initHistory, rateFunction);
	}

	@Override
	public boolean applyUpdate(IHashVector gradient, IHashVector weights) {
		final IHashVectorImmutable update = computeUpdate(gradient);
		if (update != null) {
			// Apply the update.
			update.addTimesInto(1.0, weights);
			numUpdates++;
			return true;
		} else {
			return false;
		}
	}

	public static class Creator
			implements IResourceObjectCreator<AdaGradEstimator> {

		private final String type;

		public Creator() {
			this("estimator.adagrad");
		}

		public Creator(String type) {
			this.type = type;
		}

		@SuppressWarnings("unchecked")
		@Override
		public AdaGradEstimator create(Parameters params,
				IResourceRepository repo) {
			final IntFunction<Double> rateFunction;
			if (params.contains("c") && params.contains("alpha0")) {
				final double c = params.getAsDouble("c");
				final double alpha0 = params.getAsDouble("alpha0");
				rateFunction = (Serializable & IntFunction<Double>) n -> (alpha0
						/ (1 + c * n));
				LOG.info(
						"Estimator with a decaying leanring rate: %f / (1 + %f * num_updates)",
						alpha0, c);
			} else {
				final double rate = params.getAsDouble("rate", 1.0);
				rateFunction = (Serializable & IntFunction<Double>) n -> rate;
				LOG.info("Estimator with a constant learning rate: %f", rate);
			}

			return new AdaGradEstimator(
					params.getAsBoolean("initHistory", false), rateFunction);
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage.builder(type, AdaGradEstimator.class)
					.setDescription(
							"The AdaGrad update rule from Duchi et al. 2010. Also see Notes on AdaGrad by Chris Dyer.")
					.addParam("initHistory", Boolean.class,
							"Initialize the history vector with 1.0s. This is a modification of the vanilla AdaGrad algorihtm (default: false)")
					.addParam("rate", Double.class,
							"Learning rate (only used of alpha0 or c are not used) (default: 1.0)")
					.addParam("c", Double.class,
							"Decaying learning rate: alpha0 / (1+c*num_updates) (only used if alpha0 is specified as well, rate is then ignored)")
					.addParam("alpha0", Double.class,
							"Decaying learning rate: alpha0 / (1+c*num_updates) (only used if c is specified as well, rate is then ignored)")
					.build();
		}

	}

}
