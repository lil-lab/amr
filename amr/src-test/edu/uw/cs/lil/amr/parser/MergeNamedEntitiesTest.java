package edu.uw.cs.lil.amr.parser;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class MergeNamedEntitiesTest {

	@Test
	public void test() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (c_ARG0:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "      (government-organization:<e,t> $1)\n"
								+ "      (c_ARG0-of:<e,<e,t>> $1\n"
								+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "          (govern-01:<e,t> $2)\n"
								+ "          (c_ARG1:<e,<e,t>> $2\n"
								+ "            (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "              (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                  (c_op:<e,<txt,t>> $4 Russia:txt)\n"
								+ "                  (name:<e,t> $4)))))\n"
								+ "              (country:<e,t> $3)))))))))))))\n"
								+ "  (state-01:<e,t> $0)\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "      (c_ARG0:<e,<e,t>> $5\n"
								+ "        (a:<id,<<e,t>,e>> !7 (lambda $6:e (and:<t*,t>\n"
								+ "          (person:<e,t> $6)\n"
								+ "          (c_mod:<e,<e,t>> $6\n"
								+ "            (a:<id,<<e,t>,e>> !8 (lambda $7:e (and:<t*,t>\n"
								+ "              (c_name:<e,<e,t>> $7\n"
								+ "                (a:<id,<<e,t>,e>> !9 (lambda $8:e (and:<t*,t>\n"
								+ "                  (name:<e,t> $8)\n"
								+ "                  (c_op:<e,<txt,t>> $8 Russia:txt)))))\n"
								+ "              (country:<e,t> $7)))))\n"
								+ "          (c_quant:<e,<e,t>> $6\n"
								+ "            (a:<id,<<e,t>,e>> !10 (lambda $9:e (thousands:<e,t> $9))))))))\n"
								+ "      (death:<e,t> $5)\n"
								+ "      (c_source:<e,<e,t>> $5\n"
								+ "        (a:<id,<<e,t>,e>> !11 (lambda $1:e (and:<t*,t>\n"
								+ "          (illness:<e,t> $1)\n"
								+ "          (c_time:<e,<e,t>> $1\n"
								+ "            (a:<id,<<e,t>,e>> !12 (lambda $10:e (and:<t*,t>\n"
								+ "              (year:<e,t> $10)\n"
								+ "              (c_mod:<e,<e,t>> $10\n"
								+ "                (a:<id,<<e,t>,e>> !13 (lambda $11:e (each:<e,t> $11))))))))))))))))\n"
								+ "  (c_ARG1-of:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !14 (lambda $12:e (and:<t*,t>\n"
								+ "      (c_ARG0:<e,<e,t>> $12\n"
								+ "        (a:<id,<<e,t>,e>> !15 (lambda $13:e (and:<t*,t>\n"
								+ "          (needle:<e,t> $13)\n"
								+ "          (c_ARG1-of:<e,<e,t>> $13\n"
								+ "            (a:<id,<<e,t>,e>> !16 (lambda $14:e (infect-01:<e,t> $14))))))))\n"
								+ "      (cause-01:<e,t> $12)))))\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !17 (lambda $15:e (and:<t*,t>\n"
								+ "      (abuse-01:<e,t> $15)\n"
								+ "      (c_ARG1:<e,<e,t>> $15\n"
								+ "        (a:<id,<<e,t>,e>> !18 (lambda $16:e (heroin:<e,t> $16)))))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (c_ARG0:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "      (government-organization:<e,t> $1)\n"
								+ "      (c_ARG0-of:<e,<e,t>> $1\n"
								+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "          (govern-01:<e,t> $2)\n"
								+ "          (c_ARG1:<e,<e,t>> $2\n"
								+ "            (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "              (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                  (c_op:<e,<txt,t>> $4 Russia:txt)\n"
								+ "                  (name:<e,t> $4)))))\n"
								+ "              (country:<e,t> $3)))))))))))))\n"
								+ "  (state-01:<e,t> $0)\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "      (c_ARG0:<e,<e,t>> $5\n"
								+ "        (a:<id,<<e,t>,e>> !7 (lambda $6:e (and:<t*,t>\n"
								+ "          (person:<e,t> $6)\n"
								+ "          (c_mod:<e,<e,t>> $6 (ref:<id,e> !4))\n"
								+ "          (c_quant:<e,<e,t>> $6\n"
								+ "            (a:<id,<<e,t>,e>> !10 (lambda $9:e (thousands:<e,t> $9))))))))\n"
								+ "      (death:<e,t> $5)\n"
								+ "      (c_source:<e,<e,t>> $5\n"
								+ "        (a:<id,<<e,t>,e>> !11 (lambda $1:e (and:<t*,t>\n"
								+ "          (illness:<e,t> $1)\n"
								+ "          (c_time:<e,<e,t>> $1\n"
								+ "            (a:<id,<<e,t>,e>> !12 (lambda $10:e (and:<t*,t>\n"
								+ "              (year:<e,t> $10)\n"
								+ "              (c_mod:<e,<e,t>> $10\n"
								+ "                (a:<id,<<e,t>,e>> !13 (lambda $11:e (each:<e,t> $11))))))))))))))))\n"
								+ "  (c_ARG1-of:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !14 (lambda $12:e (and:<t*,t>\n"
								+ "      (c_ARG0:<e,<e,t>> $12\n"
								+ "        (a:<id,<<e,t>,e>> !15 (lambda $13:e (and:<t*,t>\n"
								+ "          (needle:<e,t> $13)\n"
								+ "          (c_ARG1-of:<e,<e,t>> $13\n"
								+ "            (a:<id,<<e,t>,e>> !16 (lambda $14:e (infect-01:<e,t> $14))))))))\n"
								+ "      (cause-01:<e,t> $12)))))\n"
								+ "  (c_ARG1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !17 (lambda $15:e (and:<t*,t>\n"
								+ "      (abuse-01:<e,t> $15)\n"
								+ "      (c_ARG1:<e,<e,t>> $15\n"
								+ "        (a:<id,<<e,t>,e>> !18 (lambda $16:e (heroin:<e,t> $16)))))))))))");
		final LogicalExpression result = MergeNamedEntities.of(exp);
		Assert.assertEquals(expected, result);

	}

}
