package edu.uw.cs.lil.amr.learn.filter;

import java.io.Serializable;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import edu.cornell.cs.nlp.spf.data.ILabeledDataItem;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.StripSkolemIds;
import edu.cornell.cs.nlp.spf.parser.ccg.lambda.pruning.SupervisedFilterFactory;
import edu.cornell.cs.nlp.spf.parser.joint.IJointInferenceFilterFactory;
import edu.cornell.cs.nlp.utils.function.PredicateUtils;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.lambda.OverloadedLogicalConstant;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.dummyref.DummyEntityServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationServices;

public class AMRSupervisedFilterFactory implements
		IJointInferenceFilterFactory<ILabeledDataItem<SituatedSentence<AMRMeta>, LogicalExpression>, LogicalExpression, LogicalExpression, LogicalExpression> {

	private static final long																				serialVersionUID	= -7716681622605077528L;
	private final SupervisedFilterFactory<ILabeledDataItem<SituatedSentence<AMRMeta>, LogicalExpression>>	baseFilterFactory;
	private final Predicate<LogicalConstant>																constantFilter;

	@SuppressWarnings("unchecked")
	public AMRSupervisedFilterFactory(
			Predicate<LogicalConstant> constantsFilter) {
		this.constantFilter = (Serializable & Predicate<LogicalConstant>) constant -> !(LogicLanguageServices
				.isCollpasibleConstant(constant)
				|| LogicLanguageServices.isArrayIndexPredicate(constant)
				|| constant.getType().equals(SkolemServices.getIDType())
				|| constant.equals(AMRServices.getDummyEntity())
				|| CoordinationServices.isCoordinator(constant))
				&& constantsFilter.test(constant);
		this.baseFilterFactory = new SupervisedFilterFactory<>(
				this.constantFilter,
				(Serializable & Predicate<LogicalExpression>) (
						arg) -> !(arg instanceof Literal && (DummyEntityServices
								.isRelationWithDummy((Literal) arg)
								|| ((Literal) arg).numArgs() == 2
										&& ((Literal) arg)
												.getArg(1) instanceof Variable)),
				(Serializable & UnaryOperator<LogicalConstant>) c -> OverloadedLogicalConstant
						.getWrapped(c));
	}

	@Override
	public AMRSupervisedFilter create(
			ILabeledDataItem<SituatedSentence<AMRMeta>, LogicalExpression> dataItem) {
		return createJointFilter(dataItem);
	}

	@Override
	public AMRSupervisedFilter createJointFilter(
			ILabeledDataItem<SituatedSentence<AMRMeta>, LogicalExpression> dataItem) {

		final LogicalExpression baseForm = AMRServices
				.underspecify(StripSkolemIds.of(dataItem.getLabel(),
						SkolemServices.getIdPlaceholder()));
		return new AMRSupervisedFilter(baseForm, dataItem.getLabel(),
				baseFilterFactory.create(baseForm), constantFilter);
	}

	public static class Creator
			implements IResourceObjectCreator<AMRSupervisedFilterFactory> {

		private final String type;

		public Creator() {
			this("factory.filter.amr.supervised");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public AMRSupervisedFilterFactory create(Parameters params,
				IResourceRepository repo) {
			return new AMRSupervisedFilterFactory(repo.get(
					params.get("ignoreFilter"), PredicateUtils.alwaysTrue()));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return new ResourceUsage.Builder(type,
					AMRSupervisedFilterFactory.class)
							.addParam("ignoreFilter", Predicate.class,
									"Filter to ignore constants during pruning")
							.setDescription(
									"Joint inference filter for AMR parsing conditioned on a given label")
							.build();
		}
	}

}
