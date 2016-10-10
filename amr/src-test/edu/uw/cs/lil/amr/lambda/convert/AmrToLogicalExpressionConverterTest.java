package edu.uw.cs.lil.amr.lambda.convert;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class AmrToLogicalExpressionConverterTest {
	public AmrToLogicalExpressionConverterTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final String amr = "(n2 / name :op1 \"Abu\" :op2 \"Ali\" :op3 \"al-Harithi\"\n"
				+ "      :domain (a / alias\n"
				+ "            :poss (p2 / person :name (n / name :op1 \"Ali\" :op2 \"Qaed\" :op3 \"Sunian\" :op4 \"al-Harithi\"))))";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "	(name:<e,t> $0)\n"
								+ "	(c_op:<e,<txt,t>> $0 Abu++Ali++al-Harithi:txt)\n"
								+ "	(c_domain:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "			(alias:<e,t> $1)\n"
								+ "			(c_poss:<e,<e,t>> $1 \n"
								+ "				(a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "					(person:<e,t> $2)\n"
								+ "					(c_name:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "							(name:<e,t> $3)\n"
								+ "							(c_op:<e,<txt,t>> $3 Ali++Qaed++Sunian++al-Harithi:txt))))))))))))))))");
		final AmrToLogicalExpressionConverter converter = new AmrToLogicalExpressionConverter();
		try {
			final LogicalExpression actual = converter.read(amr);
			Assert.assertEquals(expected, actual);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}
