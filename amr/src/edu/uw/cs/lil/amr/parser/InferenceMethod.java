package edu.uw.cs.lil.amr.parser;

import edu.uw.cs.lil.amr.parser.factorgraph.inference.BeamSearch;
import edu.uw.cs.lil.amr.parser.factorgraph.inference.LoopyBP;

/**
 * Inference methods used in {@link GraphAmrParser} for the second step (inference
 * over the factor graph).
 *
 * @author Yoav Artzi
 */
public enum InferenceMethod {
	/**
	 * Beam search over the space of configurations. See {@link BeamSearch}.
	 */
	BEAM,

	/**
	 * Loopy belief propagation. See {@link LoopyBP}.
	 */
	LBP,

	/**
	 * Skip the the factor graph by simply taking the root expression. Dummy
	 * inference for inference without the factor graph.
	 */
	NONE;
}
