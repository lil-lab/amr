package edu.uw.cs.lil.amr.learn.estimators;

import java.io.Serializable;

import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;

/**
 * Weight gradient update procedure.
 *
 * @author Yoav Artzi
 */
public interface IWeightUpdateProcedure extends Serializable {

	/**
	 * Apply the update to the weight vector.
	 *
	 * @return <code>true</code> iff the weight vector was modified.
	 */
	boolean applyUpdate(IHashVector gradient, IHashVector weights);

	void init();

}
