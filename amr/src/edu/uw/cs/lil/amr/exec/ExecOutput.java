package edu.uw.cs.lil.amr.exec;

import java.util.List;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.exec.IExecOutput;
import edu.cornell.cs.nlp.spf.exec.IExecution;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.IJointOutput;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.utils.collections.ListUtils;
import edu.cornell.cs.nlp.utils.filter.IFilter;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.parser.GraphAmrParserOutput;

/**
 * {@link IExecOutput} wrapper for {@link GraphAmrParserOutput}.
 *
 * @author Yoav Artzi
 */
public class ExecOutput implements IExecOutput<LogicalExpression> {

	public static final ILogger												LOG	= LoggerFactory
			.create(ExecOutput.class);
	private final boolean													breakTies;
	private final IJointDataItemModel<LogicalExpression, LogicalExpression>	model;

	private final IJointOutput<LogicalExpression, LogicalExpression>		output;

	public ExecOutput(IJointOutput<LogicalExpression, LogicalExpression> output,
			IJointDataItemModel<LogicalExpression, LogicalExpression> model,
			boolean breakTies) {
		this.output = output;
		this.model = model;
		this.breakTies = breakTies;
	}

	@Override
	public List<IExecution<LogicalExpression>> getAllExecutions() {
		return output.getDerivations().stream().map((derivation) -> {
			return new Execution(derivation, model);
		}).collect(Collectors.toList());
	}

	@Override
	public long getExecTime() {
		return output.getInferenceTime();
	}

	@Override
	public List<IExecution<LogicalExpression>> getExecutions(
			IFilter<LogicalExpression> filter) {
		return output.getDerivations().stream().map((derivation) -> {
			return new Execution(derivation, model);
		}).filter((execuction) -> filter.test(execuction.getResult()))
				.collect(Collectors.toList());
	}

	@Override
	public List<IExecution<LogicalExpression>> getExecutions(
			LogicalExpression label) {
		return output.getDerivations(label).stream().map((derivation) -> {
			return new Execution(derivation, model);
		}).collect(Collectors.toList());
	}

	@Override
	public List<IExecution<LogicalExpression>> getMaxExecutions() {
		final List<IExecution<LogicalExpression>> max = output
				.getMaxDerivations().stream().map((derivation) -> {
					return new Execution(derivation, model);
				}).collect(Collectors.toList());

		if (max.size() > 1 && breakTies) {
			// Case break ties by simple taking the first one. This is
			// equivalent to random tie breaking.
			LOG.info(
					"Multiple max-scoring derivation (%d) --> breaking ties by selecting the first one",
					max.size());
			return ListUtils.createSingletonList(max.get(0));
		} else {
			return max;
		}
	}

}
