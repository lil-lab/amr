package edu.uw.cs.lil.amr.lambda;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class SortConjunctionsTest {

	public SortConjunctionsTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression exp = TestServices.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (and:<t*,t> (name:<e,<txt,t>> $0 BOO:txt) (person:<e,t> $0)))");
		final String expected = "(lambda $0:e (and:<t*,t> (person:<e,t> $0) (name:<e,<txt,t>> $0 BOO:txt)))";

		final LogicalExpression result = SortConjunctions.of(exp);
		Assert.assertEquals(exp, result);
		Assert.assertEquals(expected, result.toString());
	}

	@Test
	public void test2() {
		final LogicalExpression exp = TestServices.getCategoryServices()
				.readSemantics(
						"(lambda $1:<e,t> (lambda $0:e (and:<t*,t> (name:<e,<txt,t>> $0 BOO:txt) (person:<e,t> $0) ($1 $0))))");
		final String expected = "(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (person:<e,t> $1) (name:<e,<txt,t>> $1 BOO:txt))))";

		final LogicalExpression result = SortConjunctions.of(exp);
		Assert.assertEquals(exp, result);
		Assert.assertEquals(expected, result.toString());
	}

	@Test
	public void test3() {
		final LogicalExpression exp = TestServices.getCategoryServices()
				.readSemantics(
						"(lambda $1:<e,t> (lambda $0:e (and:<t*,t> (name:<e,<txt,t>> $0 BOO:txt) (person:<e,t> $0) ($1 $0) (room:<e,<txt,t>> $0 BOO:txt))))");
		final String expected = "(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (person:<e,t> $1) (name:<e,<txt,t>> $1 BOO:txt) (room:<e,<txt,t>> $1 BOO:txt))))";

		final LogicalExpression result = SortConjunctions.of(exp);
		Assert.assertEquals(exp, result);
		Assert.assertEquals(expected, result.toString());
	}

}
