package edu.uw.cs.lil.amr.parser.filter;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.SkolemServices;
import edu.cornell.cs.nlp.spf.mr.lambda.visitor.StripSkolemIds;
import edu.cornell.cs.nlp.spf.parser.ParsingOp;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName.Direction;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.lambda.AMRServices;
import edu.uw.cs.lil.amr.learn.filter.AMRSupervisedFilter;
import edu.uw.cs.lil.amr.learn.filter.AMRSupervisedFilterFactory;

public class AMRSupervisedFilterFactoryTest {

	private final AMRSupervisedFilterFactory factory;

	public AMRSupervisedFilterFactoryTest() {
		TestServices.init();
		this.factory = new AMRSupervisedFilterFactory((c) -> true);
	}

	@Test
	public void test() {
		final LabeledAmrSentence dataItem = new LabeledAmrSentence(
				new SituatedSentence<>(new Sentence(""),
						new AMRMeta(new Sentence(""))),
				TestServices.getCategoryServices().readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (sponsor-01:<e,t> $0)\n"
								+ "  (c_ARG0:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (it:<e,t> $1))))\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "      (workshop:<e,t> $2)\n"
								+ "      (c_beneficiary:<e,<e,t>> $2\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "          (person:<e,t> $3)\n"
								+ "          (c_quant:<e,<i,t>> $3 50:i)\n"
								+ "          (c_ARG1-of:<e,<e,t>> $3\n"
								+ "            (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "              (expert-41:<e,t> $4)\n"
								+ "              (c_ARG2:<e,<e,t>> $4\n"
								+ "                (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "                  (oppose-01:<e,t> $5)\n"
								+ "                  (c_ARG1:<e,<e,t>> $5\n"
								+ "                    (a:<id,<<e,t>,e>> !7 (lambda $6:e (terrorism:<e,t> $6))))))))))))))))\n"
								+ "      (c_duration:<e,<e,t>> $2\n"
								+ "        (a:<id,<<e,t>,e>> !8 (lambda $7:e (and:<t*,t>\n"
								+ "          (temporal-quantity:<e,t> $7)\n"
								+ "          (c_quant:<e,<i,t>> $7 2:i)\n"
								+ "          (c_unit:<e,<e,t>> $7\n"
								+ "            (a:<id,<<e,t>,e>> !9 (lambda $8:e (week:<e,t> $8)))))))))))))))"),
				Collections.<String, String> emptyMap(),
				TestServices.getCategoryServices(), null, null);

		final AMRSupervisedFilter filter = factory.create(dataItem);

		final LogicalExpression semantics = TestServices.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:i (lambda $2:e (c_ARGX:<e,<e,t>> $2 (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t> (it:<e,t> $3) (c_ARGX:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (c_op1:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t> ($0 $5) (c_REL:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $6:e (week:<e,t> $6)))) (c_REL:<e,<i,t>> $5 2:i) (c_ARGX-of:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t> (expert-41:<e,t> $7) (c_ARGX:<e,<e,t>> $7 (a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t> (oppose-01:<e,t> $8) (c_ARGX:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (terrorism:<e,t> $9)))))))))))) (c_REL:<e,<i,t>> $5 $1))))) (workshop:<e,t> $4))))))))))))");

		Assert.assertFalse(filter.test(new ParsingOp<>(
				Category.create(Syntax.N, semantics), new SentenceSpan(1, 1, 2),
				RuleName.create("dummy", Direction.FORWARD))));
	}

	@Test
	public void test2() {
		final LabeledAmrSentence dataItem = new LabeledAmrSentence(
				new SituatedSentence<>(new Sentence(""),
						new AMRMeta(new Sentence(""))),
				TestServices.getCategoryServices().readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (sponsor-01:<e,t> $0)\n"
								+ "  (c_ARG0:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (it:<e,t> $1))))\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "      (workshop:<e,t> $2)\n"
								+ "      (c_beneficiary:<e,<e,t>> $2\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "          (person:<e,t> $3)\n"
								+ "          (c_quant:<e,<i,t>> $3 50:i)\n"
								+ "          (c_ARG1-of:<e,<e,t>> $3\n"
								+ "            (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "              (expert-41:<e,t> $4)\n"
								+ "              (c_ARG2:<e,<e,t>> $4\n"
								+ "                (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "                  (oppose-01:<e,t> $5)\n"
								+ "                  (c_ARG1:<e,<e,t>> $5\n"
								+ "                    (a:<id,<<e,t>,e>> !7 (lambda $6:e (terrorism:<e,t> $6))))))))))))))))\n"
								+ "      (c_duration:<e,<e,t>> $2\n"
								+ "        (a:<id,<<e,t>,e>> !8 (lambda $7:e (and:<t*,t>\n"
								+ "          (temporal-quantity:<e,t> $7)\n"
								+ "          (c_quant:<e,<i,t>> $7 2:i)\n"
								+ "          (c_unit:<e,<e,t>> $7\n"
								+ "            (a:<id,<<e,t>,e>> !9 (lambda $8:e (week:<e,t> $8)))))))))))))))"),
				Collections.<String, String> emptyMap(),
				TestServices.getCategoryServices(), null, null);

		final AMRSupervisedFilter filter = factory.create(dataItem);

		final LogicalExpression semantics = StripSkolemIds.of(
				AMRServices.underspecify(
						TestServices.getCategoryServices().readSemantics(
								"(a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
										+ "					(person:<e,t> $3)\n"
										+ "					(c_quant:<e,<i,t>> $3 50:i)\n"
										+ "					(c_ARG1-of:<e,<e,t>> $3 \n"
										+ "						(a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
										+ "							(expert-41:<e,t> $4)\n"
										+ "							(c_ARG2:<e,<e,t>> $4 \n"
										+ "								(a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
										+ "									(oppose-01:<e,t> $5)\n"
										+ "									(c_ARG1:<e,<e,t>> $5 \n"
										+ "										(a:<id,<<e,t>,e>> !7 (lambda $6:e (terrorism:<e,t> $6)))))))))))))))")),
				SkolemServices.getIdPlaceholder());

		Assert.assertTrue(filter.test(new ParsingOp<>(
				Category.create(Syntax.N, semantics), new SentenceSpan(1, 1, 2),
				RuleName.create("dummy", Direction.FORWARD))));

	}
}
