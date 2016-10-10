package edu.uw.cs.lil.amr.learn.gradient;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import edu.cornell.cs.nlp.spf.learn.LearningStats;
import edu.cornell.cs.nlp.utils.composites.Triplet;

public class StatUpdates implements Consumer<LearningStats>, Serializable {
	private static final long								serialVersionUID	= -3078503656155084572L;
	private final List<Triplet<String, Integer, Integer>>	countUpdates		= new LinkedList<>();
	private final List<Triplet<String, Double, String>>		meanUpdates			= new LinkedList<Triplet<String, Double, String>>();
	private final List<Triplet<Integer, Integer, String>>	sampleAppends		= new LinkedList<>();

	@Override
	public void accept(LearningStats stats) {
		for (final Triplet<String, Integer, Integer> countUpdate : countUpdates) {
			if (countUpdate.second() == null) {
				stats.count(countUpdate.first(), countUpdate.third());
			} else {
				stats.count(countUpdate.first(), countUpdate.second(),
						countUpdate.third());
			}
		}

		for (final Triplet<String, Double, String> update : meanUpdates) {
			stats.mean(update.first(), update.second(), update.third());
		}

		for (final Triplet<Integer, Integer, String> append : sampleAppends) {
			stats.appendSampleStat(append.first(), append.second(),
					append.third());
		}
	}

	public void addAll(StatUpdates statUpdates) {
		meanUpdates.addAll(statUpdates.meanUpdates);
		countUpdates.addAll(statUpdates.countUpdates);
		sampleAppends.addAll(statUpdates.sampleAppends);
	}

	public void appendSampleStat(int itemNumber, int iterationNumber,
			String stat) {
		sampleAppends.add(Triplet.of(itemNumber, iterationNumber, stat));
	}

	public void count(String label, int interationNumber) {
		countUpdates.add(Triplet.of(label, null, interationNumber));
	}

	public void mean(String label, double value, String unit) {
		meanUpdates.add(Triplet.of(label, value, unit));
	}
}
