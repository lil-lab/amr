package edu.uw.cs.lil.amr.learn.gradient;

import java.io.Serializable;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutputLogger;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;

/**
 * Function to compute the gradient of the objective for a single sample.
 *
 * @author Yoav Artzi
 */
public interface IGradientFunction extends Serializable {

	GradientComputation of(
			LabeledAmrSentence dataItem,
			IJointDataItemModel<LogicalExpression, LogicalExpression> dataItemModel,
			int dataItemNumber,
			int epochNumber,
			GraphAmrParser parser,
			IJointInferenceFilterFactory<LabeledAmrSentence, LogicalExpression, LogicalExpression, LogicalExpression> filterFactory,
			IJointOutputLogger<LogicalExpression, LogicalExpression, LogicalExpression> parserOutputLogger);

}
