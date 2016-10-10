package edu.uw.cs.lil.amr.lambda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class InstanceCloneTest {

	public InstanceCloneTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression target = TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (boo:<e,t> $0))");
		final LogicalExpression source = TestServices.getCategoryServices()
				.readSemantics("(lambda $0:e (boo:<e,t> $0))");
		final LogicalExpression result = InstanceClone.of(target, source);

		assertEquals(source, result);
		assertNotEquals(((Lambda) source).getArgument(),
				((Lambda) target).getArgument());
		assertNotEquals(((Lambda) source).getArgument(),
				((Lambda) result).getArgument());
		assertEquals(((Lambda) result).getArgument(),
				((Lambda) target).getArgument());
	}

	@Test
	public void test2() {
		final LogicalExpression target = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (and:<t*,t> (foo:<e,<e,t>> (ref:<id,e> na:id) $0) (boo:<e,t> $0)))");
		final LogicalExpression source = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (and:<t*,t> (boo:<e,t> $0) (foo:<e,<e,t>> (ref:<id,e> !1) $0)))");
		final LogicalExpression result = InstanceClone.of(target, source);

		assertEquals(source, result);
		assertNotEquals(((Lambda) source).getArgument(),
				((Lambda) target).getArgument());
		assertNotEquals(((Lambda) source).getArgument(),
				((Lambda) result).getArgument());
		assertEquals(((Lambda) result).getArgument(),
				((Lambda) target).getArgument());
	}

	@Test
	public void test3() {
		final LogicalExpression target = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (boo:<e,<e,t>> $0 (ref:<id,e> na:id))))");
		final LogicalExpression source = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (boo:<e,<e,t>> $0 (ref:<id,e> !1))))");
		final LogicalExpression result = InstanceClone.of(target, source);

		assertEquals(source, result);
	}
}
