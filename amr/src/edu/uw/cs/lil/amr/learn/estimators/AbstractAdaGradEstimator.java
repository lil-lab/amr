package edu.uw.cs.lil.amr.learn.estimators;

import java.util.function.IntFunction;

import org.apache.commons.lang.text.StrBuilder;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVectorImmutable;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public abstract class AbstractAdaGradEstimator
		implements IWeightUpdateProcedure {

	public static final ILogger LOG = LoggerFactory
			.create(AbstractAdaGradEstimator.class);

	private static final long serialVersionUID = -7475984241776411616L;

	private static final IHashVector.ValueFunction updateHistoryTransformer = value -> Math
			.pow(value, -0.5);

	private final IHashVector gradientHistory = HashVectorFactory.create();

	private final boolean initHistory;

	private final IntFunction<Double> rateFunction;

	protected int numUpdates = 0;

	public AbstractAdaGradEstimator(boolean initHistory,
			IntFunction<Double> rateFunction) {
		this.initHistory = initHistory;
		this.rateFunction = rateFunction;
		LOG.info("Init %s: initHistory=%s ...", this.getClass().getSimpleName(),
				initHistory);
	}

	@Override
	public void init() {
		gradientHistory.clear();
		numUpdates = 0;
	}

	@Override
	public String toString() {
		return new StrBuilder("adagrad [learningRate=")
				.append(rateFunction).append(", initHistory=")
				.append(initHistory).append("]").toString();
	}

	protected IHashVectorImmutable computeUpdate(IHashVector gradient) {

		// Clean some noise.
		gradient.dropNoise();

		LOG.info("Gradient: %s", gradient);

		if (gradient.size() == 0) {
			return null;
		}

		// Update the gradient history.
		if (initHistory) {
			// Initialize the history vector with 1.0 for each feature seen.
			// This a modification of the vanilla AdaGrad algorithm.
			gradient.pairWiseProduct(gradient).forEach(entry -> {
				if (!gradientHistory.contains(entry.first())) {
					gradientHistory.set(entry.first(), 1.0);
				}
				gradientHistory.add(entry.first(), entry.second());
			});
		} else {
			// Vanilla AdaGrad history update.
			gradient.pairWiseProduct(gradient).addTimesInto(1.0,
					gradientHistory);
		}

		// Create the adaptive update.
		LOG.debug(() -> {
			LOG.debug("Gradient history: %s",
					gradientHistory.printValues(gradient));
			LOG.debug("Multiplier for update: %s",
					createUpdateHistoryMultiplier().printValues(gradient));
		});
		final IHashVector adaUpdate = gradient
				.pairWiseProduct(createUpdateHistoryMultiplier());

		LOG.info("Adaptive update (w/o learning rate of %f): %s",
				rateFunction.apply(numUpdates), adaUpdate);

		// Scale the update (apply the learning rate).
		adaUpdate.multiplyBy(rateFunction.apply(numUpdates));

		if (!adaUpdate.valuesInRange(-100, 100)) {
			LOG.warn("Large update");
		}

		return adaUpdate;
	}

	protected IHashVector createUpdateHistoryMultiplier() {
		final IHashVector updateHistoryCopy = HashVectorFactory
				.create(gradientHistory);
		updateHistoryCopy.applyFunction(updateHistoryTransformer);
		return updateHistoryCopy;
	}

}
