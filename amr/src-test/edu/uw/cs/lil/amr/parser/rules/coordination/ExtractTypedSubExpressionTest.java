package edu.uw.cs.lil.amr.parser.rules.coordination;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.parser.rules.coordination.ExtractTypedSubExpression.Result;

public class ExtractTypedSubExpressionTest {

	public ExtractTypedSubExpressionTest() {
		TestServices.init();
	}

	@Test
	public void test1() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:e"), LogicLanguageServices.getTypeRepository()
				.getEntityType(), true);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = TestServices
				.getCategoryServices().readSemantics("goo:e");
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (boo:<e,<e,t>> $1 p:e))))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

	@Test
	public void test2() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:<e,<e,t>>"), LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded("<e,<e,t>>"), false);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = LogicalConstant
				.read("boo:<e,<e,t>>");
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (p:<e,<e,t>> $1 goo:e))))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

	@Test
	public void test3() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> (do:<e,<e,<e,t>>> roo:e $1 (a:<<e,t>,e> (lambda $0:e (boo:<e,<e,t>> $0 too:e)))) ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:<e,<e,t>>"), LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded("<e,<e,t>>"), false);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = LogicalConstant
				.read("boo:<e,<e,t>>");
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> (do:<e,<e,<e,t>>> roo:e $1 (a:<<e,t>,e> (lambda $0:e (boo:<e,<e,t>> $0 too:e)))) ($0 $1) (p:<e,<e,t>> $1 goo:e))))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

	@Test
	public void test4() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> (do:<e,<e,<e,t>>> roo:e $1 (a:<<e,t>,e> (lambda $0:e (boo:<e,<e,t>> $0 too:e)))) ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:t"), LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded("t"), false);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = ((Lambda) ((Lambda) exp)
				.getBody()).getBody();
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices().readSemantics(
						"(lambda $0:<e,t> (lambda $1:e p:t))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

	@Test
	public void test5() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> (do:<e,<e,<e,t>>> roo:e $1 (a:<<e,t>,e> (lambda $0:e (boo:<e,<e,t>> $0 too:e)))) ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:t"), LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded("t"), true);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = ((Literal) ((Lambda) ((Lambda) exp)
				.getBody()).getBody()).getArg(0);
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> p:t ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

	@Test
	public void test6() {
		final LogicalExpression exp = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (boo:<e,<e,t>> $1 goo:e))))");
		final Result result = ExtractTypedSubExpression.of(exp, LogicalConstant
				.read("p:t"), LogicLanguageServices.getTypeRepository()
				.getTypeCreateIfNeeded("t"), true);
		Assert.assertNotNull(result);
		final LogicalExpression expectedSubExp = ((Literal) ((Lambda) ((Lambda) exp)
				.getBody()).getBody()).getArg(1);
		final LogicalExpression expectedRemainder = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) p:t)))");
		Assert.assertEquals(expectedRemainder,
				result.getExpressionWithPlaceholder());
		Assert.assertEquals(expectedSubExp, result.getExtractedSubExpression());
	}

}
