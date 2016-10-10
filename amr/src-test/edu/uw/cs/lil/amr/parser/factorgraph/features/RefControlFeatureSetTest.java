package edu.uw.cs.lil.amr.parser.factorgraph.features;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.joint.model.IJointDataItemModel;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.utils.composites.Pair;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.parser.EvaluationResult;
import edu.uw.cs.lil.amr.parser.factorgraph.FactorGraph;
import edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen.AssignmentGeneratorFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.inference.BeamSearch;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.CreateFactorGraph;

public class RefControlFeatureSetTest {

	public RefControlFeatureSetTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression exp = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t> (want-01:<e,t> $0) "
								+ "(c_ARG0:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !2 (lambda $0:e (i:<e,t> $0)))) "
								+ "(c_ARG1:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !3 (lambda $0:e (and:<t*,t> (buy-01:<e,t> $0) "
								+ "(c_ARG0:<e,<e,t>> $0 (ref:<id,e> na:id)) "
								+ "(c_ARG1:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !4 (lambda $0:e (ticket:<e,t> $0)))))))))))");

		final AssignmentGeneratorFactory factory = new AssignmentGeneratorFactory();
		final JointModel<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression> model = new JointModel.Builder<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>()
				.build();
		model.getTheta().set("REFCTRL", "want-01", "c_ARG0", "c_ARG1", "c_ARG0",
				1.0);

		final Sentence sentence = new Sentence("I want to buy a ticket");
		final AMRMeta meta = new AMRMeta(sentence);
		final IJointDataItemModel<LogicalExpression, LogicalExpression> dim = model
				.createJointDataItemModel(
						new SituatedSentence<AMRMeta>(sentence, meta));

		final FactorGraph graph = CreateFactorGraph.of(exp, factory.create(exp),
				false);

		final List<Runnable> jobs = new RefControlFeatureSet()
				.createFactorJobs(graph, meta, dim);
		jobs.stream().forEach(r -> r.run());

		final Pair<List<EvaluationResult>, Boolean> results = BeamSearch
				.of(graph, 100);
		Assert.assertEquals(4, results.first().size());
		final EvaluationResult max = results.first().stream()
				.max((r1, r2) -> Double.compare(r1.getScore(), r2.getScore()))
				.get();
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t> (want-01:<e,t> $0) (c_ARG0:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !2 (lambda $1:e (i:<e,t> $1)))) (c_ARG1:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t> (buy-01:<e,t> $2) (c_ARG0:<e,<e,t>> $2 (ref:<id,e> !2)) (c_ARG1:<e,<e,t>> $2 (a:<id,<<e,t>,e>> !4 (lambda $3:e (ticket:<e,t> $3)))))))))))");
		Assert.assertEquals(expected, max.getResult());
	}

}
