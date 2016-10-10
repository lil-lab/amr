package edu.uw.cs.lil.amr.parser.genlex;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.data.AMRMeta;

public class DatesGeneratorTest {

	private final DatesGenerator	gen;

	public DatesGeneratorTest() {
		TestServices.init();
		this.gen = new DatesGenerator("dyn-dates",
				LogicalConstant.read("c_day:<e,<i,t>>"),
				LogicalConstant.read("c_year:<e,<i,t>>"),
				LogicalConstant.read("c_month:<e,<i,t>>"),
				LogicalConstant.read("date-entity:<e,t>"),
				Syntax.read("NP[sg]"));
	}

	private static SituatedSentence<AMRMeta> create(String... tokens) {
		final Sentence sentence = new Sentence(Arrays.stream(tokens).collect(
				Collectors.joining(" ")));
		return new SituatedSentence<>(sentence, new AMRMeta(sentence));
	}

	@Test
	public void test() {
		final Set<LexicalEntry<LogicalExpression>> lexicon = gen
				.generateLexicon(create("010911"));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("010911 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 1901:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("010911 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
	}

	@Test
	public void test2() {
		final Set<LexicalEntry<LogicalExpression>> lexicon = gen
				.generateLexicon(create("11 September 2001"));
		Assert.assertFalse(lexicon.contains(LexicalEntry
				.read("11 September 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 1901:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("11 September 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("September 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i))))",
						TestServices.getCategoryServices(), "dummy")));
	}

	@Test
	public void test3() {
		final Set<LexicalEntry<LogicalExpression>> lexicon = gen
				.generateLexicon(create("01-09-11"));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("01-09-11 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 1901:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("01-09-11 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
	}

	@Test
	public void test4() {
		final Set<LexicalEntry<LogicalExpression>> lexicon = gen
				.generateLexicon(create("September 11 , 2001"));
		Assert.assertFalse(lexicon.contains(LexicalEntry
				.read("September 11 , 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 1901:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("September 11 , 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i) (c_day:<e,<i,t>> $0 11:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertFalse(lexicon.contains(LexicalEntry
				.read("September 2001 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2001:i) (c_month:<e,<i,t>> $0 9:i))))",
						TestServices.getCategoryServices(), "dummy")));
	}

	@Test
	public void test5() {
		final Set<LexicalEntry<LogicalExpression>> lexicon = gen
				.generateLexicon(create("20021226"));
		Assert.assertEquals(1, lexicon.size());
		Assert.assertFalse(lexicon.contains(LexicalEntry
				.read("20021226 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 1902:i) (c_month:<e,<i,t>> $0 12:i) (c_day:<e,<i,t>> $0 26:i))))",
						TestServices.getCategoryServices(), "dummy")));
		Assert.assertTrue(lexicon.contains(LexicalEntry
				.read("20021226 :- NP[sg] : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (date-entity:<e,t> $0) (c_year:<e,<i,t>> $0 2002:i) (c_month:<e,<i,t>> $0 12:i) (c_day:<e,<i,t>> $0 26:i))))",
						TestServices.getCategoryServices(), "dummy")));
	}

}
