package edu.uw.cs.lil.amr.lambda;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class GetAmrSubExpressionsTest {

	@Test
	public void test() {
		final LogicalExpression amr = TestServices.getCategoryServices()
				.readSemantics("(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3)))))))))))");
		final Set<LogicalExpression> result = GetAmrSubExpressions.of(amr);

		Assert.assertEquals(28, result.size());
		Assert.assertTrue(result.contains(amr));

		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (person:<e,t> $0))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3))))))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:e (lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0 $1)\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3)))))))))))")));
		Assert.assertFalse(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:e (lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                $1))))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:e (lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))\n"
						+ "    (c_age:<e,<e,t>> $0 $1))))")));

		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:<e,t> (lambda $0:e (and:<t*,t>\n"
						+ "    ($1 $0)\n" + "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3))))))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3))))))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:<e,t> (lambda $0:e (and:<t*,t>\n"
						+ "    ($1 $0)\n" + "    (c_age:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3))))))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (and:<t*,t>\n"
						+ "    (person:<e,t> $0)\n"
						+ "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:<e,t> (lambda $0:e (and:<t*,t>\n"
						+ "    ($1 $0)\n" + "    (c_name:<e,<e,t>> $0\n"
						+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (year:<e,t> $0))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3)))")));

		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (name:<e,t> $0))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $1:e (and:<t*,t>\n"
						+ "            (name:<e,t> $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt))))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:<e,t> (lambda $1:e (and:<t*,t>\n"
						+ "            ($0 $1)\n"
						+ "            (c_op:<e,<txt,t>> $1 Raghad++Hussein:txt)))))")));

		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (temporal-quantity:<e,t> $0))")));
		Assert.assertTrue(result.contains(TestServices.getCategoryServices()
				.readSemantics("(a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
						+ "            (temporal-quantity:<e,t> $2)\n"
						+ "            (c_quant:<e,<i,t>> $2 38:i)\n"
						+ "            (c_unit:<e,<e,t>> $2\n"
						+ "                (a:<id,<<e,t>,e>> !4 (lambda $3:e (year:<e,t> $3)))))))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $0:e (and:<t*,t> (temporal-quantity:<e,t> $0) (c_quant:<e,<i,t>> $0 38:i) (c_unit:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !1 (lambda $1:e (year:<e,t> $1))))))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $1:<e,t> (lambda $0:e (and:<t*,t> ($1 $0) (c_quant:<e,<i,t>> $0 38:i) (c_unit:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !1 (lambda $1:e (year:<e,t> $1))))))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $0:e (and:<t*,t> (temporal-quantity:<e,t> $0) (c_unit:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !1 (lambda $1:e (year:<e,t> $1))))))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $1:<e,t> (lambda $0:e (and:<t*,t> ($1 $0) (c_unit:<e,<e,t>> $0 (a:<id,<<e,t>,e>> !1 (lambda $1:e (year:<e,t> $1)))))))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $0:e (and:<t*,t> (temporal-quantity:<e,t> $0) (c_quant:<e,<i,t>> $0 38:i)))")));
		Assert.assertTrue(result
				.contains(TestServices.getCategoryServices().readSemantics(
						"(lambda $1:<e,t> (lambda $0:e (and:<t*,t> ($1 $0) (c_quant:<e,<i,t>> $0 38:i)))")));

	}
}
