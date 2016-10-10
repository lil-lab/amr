package edu.uw.cs.lil.amr.parser.rules.amrspecials;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.SentenceSpan;
import edu.cornell.cs.nlp.utils.collections.SetUtils;
import edu.uw.cs.lil.amr.TestServices;

public class DetermineNamedEntityTest {

	@Test
	public void test() {
		final Category<LogicalExpression> input = TestServices
				.getCategoryServices()
				.read("N[sg] : (lambda $0:e (and:<t*,t> (person:<e,t> $0) (c_name:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (name:<e,t> $0) (c_op:<e,<txt,t>> $0 Abdullah:txt)))))))");
		final Category<LogicalExpression> expected = TestServices
				.getCategoryServices()
				.read("NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (person:<e,t> $0) (c_name:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (name:<e,t> $0) (c_op:<e,<txt,t>> $0 Abdullah:txt)))))))");
		final DetermineNamedEntity rule = new DetermineNamedEntity(
				Syntax.read("N[sg]"), Syntax.read("NP[sg]"));
		final SentenceSpan span = new SentenceSpan(0, 1, 2);
		Assert.assertEquals(expected, rule.apply(input, span)
				.getResultCategory());
		Assert.assertEquals(SetUtils.createSingleton(input),
				rule.reverseApply(expected, span));
	}

}
