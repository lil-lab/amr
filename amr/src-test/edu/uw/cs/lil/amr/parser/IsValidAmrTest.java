package edu.uw.cs.lil.amr.parser;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.lambda.AMRServices;

public class IsValidAmrTest {

	public IsValidAmrTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression amr = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (and:<e,t> $0)\n"
								+ "  (c_op1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (international:<e,t> $1))))\n"
								+ "  (c_op2:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (military:<e,t> $2))))\n"
								+ "  (c_op2:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !4 (lambda $3:e (terrorism:<e,t> $3)))))))");
		Assert.assertFalse(IsValidAmr.of(AMRServices.underspecifyAndStrip(amr),
				false, true));
		Assert.assertFalse(IsValidAmr.of(amr, true, true));
	}

	@Test
	public void test2() {
		final LogicalExpression amr = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "	(say-01:<e,t> $0)\n"
								+ "	(c_ARG1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> !2 (lambda $1:e (this:<e,t> $1))))\n"
								+ "	(c_location:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> !3 (lambda $2:e (statement:<e,t> $2))))\n"
								+ "	(c_time:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "			(after:<e,t> $3)\n"
								+ "			(c_op2:<e,<e,t>> $3 \n"
								+ "				(a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "					(meeting:<e,t> $4)\n"
								+ "					(c_time:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "							(date-entity:<e,t> $5)\n"
								+ "							(c_year:<e,<i,t>> $5 2002:i)\n"
								+ "							(c_month:<e,<i,t>> $5 12:i)\n"
								+ "							(c_day:<e,<i,t>> $5 26:i))))))))))))))))");
		Assert.assertFalse(IsValidAmr.of(AMRServices.underspecifyAndStrip(amr),
				false, true));
		Assert.assertFalse(IsValidAmr.of(amr, true, true));
	}

	@Test
	public void test3() {
		final LogicalExpression amr = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (and:<e,t> $0)\n"
								+ "  (c_op1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (international:<e,t> $1))))\n"
								+ "  (c_op2:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (military:<e,t> $2))))\n"
								+ "  (c_op3:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !4 (lambda $3:e (terrorism:<e,t> $3)))))))");
		Assert.assertTrue(IsValidAmr.of(AMRServices.underspecifyAndStrip(amr),
				false, true));
		Assert.assertTrue(IsValidAmr.of(amr, true, true));
	}

	@Test
	public void test4() {
		final LogicalExpression amr = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (and:<e,t> $0)\n"
								+ "  (c_op1:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (international:<e,t> $1))))\n"
								+ "  (c_op2:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (military:<e,t> $2))))\n"
								+ "  (c_op3:<e,<e,t>> $0\n"
								+ "    (a:<id,<<e,t>,e>> !4 (lambda $3:e (terrorism:<e,t> $0)))))))");
		Assert.assertFalse(IsValidAmr.of(AMRServices.underspecifyAndStrip(amr),
				false, true));
		Assert.assertFalse(IsValidAmr.of(amr, true, true));
	}

	@Test
	public void test5() {
		final LogicalExpression amr = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (and:<t*,t>\n"
								+ "	(give-01:<e,t> $0)\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t>\n"
								+ "			(person:<e,t> $1)\n"
								+ "			(c_REL~~stephane-hadoux:<e,<e,t>> $1 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "					(name:<e,t> $2)\n"
								+ "					(c_op:<e,<txt,t>> $2 Stephane++hadoux:txt)))))))))\n"
								+ "	(c_ARG1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $3:e (sentence-01:<e,t> $3))))\n"
								+ "	(give-01:<e,t> $0)\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "			(person:<e,t> $4)\n"
								+ "			(c_REL~~emmanuel-nieto:<e,<e,t>> $4 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "					(name:<e,t> $5)\n"
								+ "					(c_op:<e,<txt,t>> $5 Emmanuel++Nieto:txt)))))))))\n"
								+ "	(c_ARG1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $6:e (sentence-01:<e,t> $6))))))");
		Assert.assertFalse(IsValidAmr.of(AMRServices.underspecifyAndStrip(amr),
				false, true));
		Assert.assertFalse(IsValidAmr.of(amr, false, false));
	}

}
