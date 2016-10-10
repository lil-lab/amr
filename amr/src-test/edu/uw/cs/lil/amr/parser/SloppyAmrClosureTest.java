package edu.uw.cs.lil.amr.parser;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class SloppyAmrClosureTest {

	public SloppyAmrClosureTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t>\n"
								+ "	($0 $1)\n"
								+ "	(c_ARGX:<e,<e,t>> $1 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(c_REL:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(country:<e,t> $3)\n"
								+ "					(c_op:<e,<txt,t>> $3 United++States:txt)))))\n"
								+ "			(name:<e,t> $2))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(c_REL:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(country:<e,t> $3)\n"
								+ "					(c_op:<e,<txt,t>> $3 United++States:txt)))))\n"
								+ "			(name:<e,t> $2))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test10() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:i (lambda $2:<e,<e,t>> (lambda $3:e ($2 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (facility:<e,t> $4) (c_op1:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t> ($0 $5) (c_REL:<e,<i,t>> $5 $1)))))))) $3)))))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test11() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:<<<e,t>,<e,t>>,<e,t>> (lambda $2:<e,t> (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t> ($2 $3) (c_REL:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e ($1 (lambda $5:<e,t> (lambda $6:e (and:<t*,t> ($5 $6) (c_ARGX:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t> (c_op1:<e,<e,t>> $7 (a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t> ($0 $8) (c_ARGX:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (and:<t*,t> (c_REL:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $10:e (and:<t*,t> (c_op:<e,<txt,t>> $10 Korea:txt) (name:<e,t> $10))))) (missile:<e,t> $9))))) (deliver-01:<e,t> $8) (c_REL:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $11:e (and:<t*,t> (airport:<e,t> $11) (c_REL:<e,<e,t>> $11 (a:<id,<<e,t>,e>> na:id (lambda $12:e (and:<t*,t> (date-entity:<e,t> $12) (c_year:<e,<i,t>> $12 2000:i))))))))))))) (arrest-01:<e,t> $7)))))))) $4))))))))))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test12() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<<<e,t>,<e,t>>,<e,t>> (a:<id,<<e,t>,e>> na:id (lambda $1:e ($0 (lambda $2:<e,t> (lambda $3:e (and:<t*,t> ($2 $3) (c_ARGX:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (c_ARGX-of:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (have-org-role-91:<e,t> $5)))) (person:<e,t> $4)))))))) $1))))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test13() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,<e,t>> (lambda $1:e (lambda $2:e (lambda $3:e (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "	(c_ARG1:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "		(c_ARG0:<e,<e,t>> $5 $3)\n"
								+ "		(state-01:<e,t> $5)\n"
								+ "		(c_ARG1:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "			(c_REL:<e,<e,t>> $6 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $7:e (there:<e,t> $7))))\n"
								+ "			(and:<e,t> $6)\n"
								+ "			(c_op1:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "				(have-03:<e,t> $8)\n"
								+ "				(c_ARG0:<e,<e,t>> $8 $2)\n"
								+ "				(c_ARG1:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (and:<t*,t>\n"
								+ "					(individual:<e,t> $9)\n"
								+ "					(c_REL:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $10:e (and:<t*,t>\n"
								+ "						(c_op1:<e,<i,t>> $10 2:i)\n"
								+ "						(c_REL:<e,<e,t>> $10 (a:<id,<<e,t>,e>> na:id (lambda $11:e ($0 $1 $11))))))))))))))))\n"
								+ "			(c_op2:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $12:e (and:<t*,t>\n"
								+ "				(find-02:<e,t> $12)\n"
								+ "				(c_ARG1:<e,<e,t>> $12 (a:<id,<<e,t>,e>> na:id (lambda $13:e (and:<t*,t>\n"
								+ "					($0 $1 $13)\n"
								+ "					(c_ARG0:<e,<e,t>> $13 $2)))))))))\n"
								+ "			(c_REL:<e,<e,t>> $6 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $14:e (man:<e,t> $14))))))))))))\n"
								+ "	(court:<e,t> $4))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(c_ARG1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t>\n"
								+ "			(state-01:<e,t> $1)\n"
								+ "			(c_ARG1:<e,<e,t>> $1 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "					(c_REL:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $3:e (there:<e,t> $3))))\n"
								+ "					(and:<e,t> $2)\n"
								+ "					(c_REL:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $4:e (man:<e,t> $4))))\n"
								+ "					(c_op1:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "							(have-03:<e,t> $5)\n"
								+ "							(c_ARG1:<e,<e,t>> $5 \n"
								+ "								(a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "									(individual:<e,t> $6)\n"
								+ "									(c_REL:<e,<e,t>> $6 \n"
								+ "										(a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "											(UNK:<e,t> $7)\n"
								+ "											(c_op1:<e,<i,t>> $7 2:i)))))))))))))\n"
								+ "					(c_op2:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $8:e (find-02:<e,t> $8))))))))))))\n"
								+ "	(court:<e,t> $0))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test14() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (lambda $1:<e,<e,t>> (lambda $2:e (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "	(c_op1:<e,<i,t>> $3 60:i)\n"
								+ "	($1 $2 $3)\n"
								+ "	(c_ARG0:<e,<e,t>> $3 $0)\n"
								+ "	(c_REL:<e,<e,t>> $3 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $4:e (also:<e,t> $4))))\n"
								+ "	(c_REL-of:<e,<e,t>> $3 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "			(blacklist-01:<e,t> $5)\n"
								+ "			(c_REL-of:<e,<e,t>> $5 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "					(organization:<e,t> $6)\n"
								+ "					(c_REL:<e,<e,t>> $6 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "							(c_op:<e,<txt,t>> $7 European++Union:txt)\n"
								+ "							(name:<e,t> $7)))))))))))))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(UNK:<e,t> $0)\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (also:<e,t> $1))))\n"
								+ "	(c_REL-of:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(blacklist-01:<e,t> $2)\n"
								+ "			(c_REL-of:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(organization:<e,t> $3)\n"
								+ "					(c_REL:<e,<e,t>> $3 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "							(c_op:<e,<txt,t>> $4 European++Union:txt)\n"
								+ "							(name:<e,t> $4)))))))))))))\n"
								+ "	(c_op1:<e,<i,t>> $0 60:i))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test15() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (lambda $1:<e,t> (lambda $2:e (lambda $3:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<e,t>> $3 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $4:e (already:<e,t> $4))))\n"
								+ "	(purchase-01:<e,t> $3)\n"
								+ "	(c_ARG1:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "		(c_REL:<e,<i,t>> $5 150000000:i)\n"
								+ "		(person:<e,t> $5)\n"
								+ "		(c_REL:<e,<e,t>> $5 $0)\n"
								+ "		(c_REL:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "			($1 $6)\n"
								+ "			(c_op1:<e,<i,t>> $6 1000000:i)))))\n"
								+ "		(c_REL:<e,<e,t>> $5 \n"
								+ "			(a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "				(c_REL:<e,<e,t>> $7 \n"
								+ "					(a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "						(name:<e,t> $8)\n"
								+ "						(c_op:<e,<txt,t>> $8 US:txt)))))\n"
								+ "				(country:<e,t> $7)))))\n"
								+ "		(c_REL:<e,<e,t>> $5 \n"
								+ "			(a:<id,<<e,t>,e>> na:id (lambda $9:e (deal-01:<e,t> $9))))))))\n"
								+ "	(c_ARG0:<e,<e,t>> $3 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $10:e (foreign:<e,t> $10))))\n"
								+ "	(c_REL:<e,<e,t>> $3 $2))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (already:<e,t> $1))))\n"
								+ "	(purchase-01:<e,t> $0)\n"
								+ "	(c_ARG1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(c_REL:<e,<i,t>> $2 150000000:i)\n"
								+ "			(person:<e,t> $2)\n"
								+ "			(c_REL:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(UNK:<e,t> $3)\n"
								+ "					(c_op1:<e,<i,t>> $3 1000000:i)))))\n"
								+ "			(c_REL:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "					(c_REL:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "							(name:<e,t> $5)\n"
								+ "							(c_op:<e,<txt,t>> $5 US:txt)))))\n"
								+ "					(country:<e,t> $4)))))\n"
								+ "			(c_REL:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $6:e (deal-01:<e,t> $6))))))))\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $7:e (foreign:<e,t> $7)))))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test16() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:i (lambda $2:<e,t> (lambda $3:e (and:<t*,t>\n"
								+ "	($2 $3)\n"
								+ "	(c_REL:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "		(c_op1:<e,<i,t>> $4 2:i)\n"
								+ "		(country:<e,t> $4)\n"
								+ "		(c_REL:<e,<e,t>> $4 \n"
								+ "			(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "				(date-entity:<e,t> $5)\n"
								+ "				(c_year:<e,<i,t>> $5 2008:i)\n"
								+ "				(c_month:<e,<i,t>> $5 8:i)))))\n"
								+ "		(c_REL:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "			(center:<e,t> $6)\n"
								+ "			(c_op1:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "				($0 $7)\n"
								+ "				(c_REL:<e,<i,t>> $7 $1)))))))))\n"
								+ "		(c_ARGX-of:<e,<e,t>> $4 \n"
								+ "			(a:<id,<<e,t>,e>> na:id (lambda $8:e (include-91:<e,t> $8)))))))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(country:<e,t> $0)\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t>\n"
								+ "			(date-entity:<e,t> $1)\n"
								+ "			(c_year:<e,<i,t>> $1 2008:i)\n"
								+ "			(c_month:<e,<i,t>> $1 8:i)))))\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (center:<e,t> $2))))\n"
								+ "	(c_ARGX-of:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $3:e (include-91:<e,t> $3))))\n"
								+ "	(c_op1:<e,<i,t>> $0 2:i))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test17() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (and:<t*,t>\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t>\n"
								+ "			(organization:<e,t> $1)\n"
								+ "			(c_REL~~united-nations:<e,<e,t>> $1 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "					(name:<e,t> $2)\n"
								+ "					(c_op:<e,<txt,t>> $2 United++Nations:txt)))))))))\n"
								+ "	(c_REL~~order^^A1R1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "			(oblige-01:<e,t> $3)\n"
								+ "			(c_op1:<e,<e,t>> $3 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "					(and:<e,t> $4)\n"
								+ "					(c_op1:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $5:e (member:<e,t> $5))))\n"
								+ "					(c_op2:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "							(state:<e,t> $6)\n"
								+ "							(c_REL~~to^^A1R1:<e,<e,t>> $6 \n"
								+ "								(a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "									(freeze-02:<e,t> $7)\n"
								+ "									(c_ARG1:<e,<e,t>> $7 \n"
								+ "										(a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "											(asset:<e,t> $8)\n"
								+ "											(c_REL~~of^^A1R1:<e,<e,t>> $8 \n"
								+ "												(a:<id,<<e,t>,e>> na:id (lambda $9:e (person:<e,t> $9))))))))))))))))\n"
								+ "					(c_op3:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $10:e (entity:<e,t> $10))))\n"
								+ "					(c_REL-of:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $11:e (suspect-01:<e,t> $11))))\n"
								+ "					(c_REL~~united-nations:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $12:e (and:<t*,t>\n"
								+ "							(c_REL~~united-nations:<e,<e,t>> $12 \n"
								+ "								(a:<id,<<e,t>,e>> na:id (lambda $13:e (and:<t*,t>\n"
								+ "									(name:<e,t> $13)\n"
								+ "									(c_op:<e,<txt,t>> $13 United++Nations:txt)))))\n"
								+ "							(organization:<e,t> $12)))))))))))))\n"
								+ "	(c_REL~~of^^A1R1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $14:e (and:<t*,t>\n"
								+ "			(fund-01:<e,t> $14)\n"
								+ "			(c_ARG1:<e,<e,t>> $14 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $15:e (and:<t*,t>\n"
								+ "					(group:<e,t> $15)\n"
								+ "					(c_REL:<e,<e,t>> $15 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $16:e (terror:<e,t> $16))))))))))))\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $17:e (and:<t*,t>\n"
								+ "			(conflict-01:<e,t> $17)\n"
								+ "			(c_REL-of:<e,<e,t>> $17 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $18:e (rule-03:<e,t> $18))))))))))");
		Assert.assertTrue(IsValidAmr.of(semantics, false, true));
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (UNK:<e,t> $0) \n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t>\n"
								+ "			(organization:<e,t> $1)\n"
								+ "			(c_REL~~united-nations:<e,<e,t>> $1 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "					(name:<e,t> $2)\n"
								+ "					(c_op:<e,<txt,t>> $2 United++Nations:txt)))))))))\n"
								+ "	(c_REL~~order^^A1R1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "			(oblige-01:<e,t> $3)\n"
								+ "			(c_op1:<e,<e,t>> $3 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t>\n"
								+ "					(and:<e,t> $4)\n"
								+ "					(c_op1:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $5:e (member:<e,t> $5))))\n"
								+ "					(c_op2:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "							(state:<e,t> $6)\n"
								+ "							(c_REL~~to^^A1R1:<e,<e,t>> $6 \n"
								+ "								(a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "									(freeze-02:<e,t> $7)\n"
								+ "									(c_ARG1:<e,<e,t>> $7 \n"
								+ "										(a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "											(asset:<e,t> $8)\n"
								+ "											(c_REL~~of^^A1R1:<e,<e,t>> $8 \n"
								+ "												(a:<id,<<e,t>,e>> na:id (lambda $9:e (person:<e,t> $9))))))))))))))))\n"
								+ "					(c_op3:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $10:e (entity:<e,t> $10))))\n"
								+ "					(c_REL-of:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $11:e (suspect-01:<e,t> $11))))\n"
								+ "					(c_REL~~united-nations:<e,<e,t>> $4 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $12:e (and:<t*,t>\n"
								+ "							(c_REL~~united-nations:<e,<e,t>> $12 \n"
								+ "								(a:<id,<<e,t>,e>> na:id (lambda $13:e (and:<t*,t>\n"
								+ "									(name:<e,t> $13)\n"
								+ "									(c_op:<e,<txt,t>> $13 United++Nations:txt)))))\n"
								+ "							(organization:<e,t> $12)))))))))))))\n"
								+ "	(c_REL~~of^^A1R1:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $14:e (and:<t*,t>\n"
								+ "			(fund-01:<e,t> $14)\n"
								+ "			(c_ARG1:<e,<e,t>> $14 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $15:e (and:<t*,t>\n"
								+ "					(group:<e,t> $15)\n"
								+ "					(c_REL:<e,<e,t>> $15 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $16:e (terror:<e,t> $16))))))))))))\n"
								+ "	(c_ARG0:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $17:e (and:<t*,t>\n"
								+ "			(conflict-01:<e,t> $17)\n"
								+ "			(c_REL-of:<e,<e,t>> $17 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $18:e (rule-03:<e,t> $18)))))))))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertNotNull(actual);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test2() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:<e,t> (lambda $2:<<e,t>,<e,t>> (lambda $3:<e,t> (lambda $4:<e,t> (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "	($4 $5)\n"
								+ "	(c_ARGX:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t>\n"
								+ "		(c_REL:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $7:e ($2 (lambda $8:e (and:<t*,t>\n"
								+ "			(c_op1:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (and:<t*,t>\n"
								+ "				($1 $9)\n"
								+ "				(c_ARGX:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $10:e (and:<t*,t>\n"
								+ "					($0 $10)\n"
								+ "					(c_REL:<e,<e,t>> $10 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $11:e (and:<t*,t>\n"
								+ "							(date-entity:<e,t> $11)\n"
								+ "							(c_year:<e,<i,t>> $11 2007:i)))))))))))))\n"
								+ "			(after:<e,t> $8))) $7))))\n"
								+ "		($3 $6)))))))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "					(c_op1:<e,<e,t>> $2 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "											(date-entity:<e,t> $5)\n"
								+ "											(c_year:<e,<i,t>> $5 2007:i)))))\n"
								+ "					(after:<e,t> $2))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test3() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (cause-01:<e,t> $1) (c_op1:<e,<e,t>> $1 $0)))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $1:e (cause-01:<e,t> $1)))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test4() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<e,t>> $1 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (also:<e,t> $2))))\n"
								+ "	(state-01:<e,t> $1)\n"
								+ "	(c_ARGX:<e,<e,t>> $1 \n"
								+ "		(ref:<id,e> na:id))\n"
								+ "	(c_ARGX:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $3:e ($0 $3))))\n"
								+ "	($0 $1)\n" + "	($0 $1)\n" + "	($0 $1))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (also:<e,t> $1))))\n"
								+ "	(state-01:<e,t> $0)\n"
								+ "	(c_ARGX:<e,<e,t>> $0 \n"
								+ "		(ref:<id,e> na:id)))))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test5() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:<e,t> (lambda $2:<e,t> (lambda $3:<e,t> (lambda $4:e (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<i,t>> $5 1:i)\n"
								+ "	(c_REL:<e,<e,t>> $5 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $6:e (nucleus:<e,t> $6))))\n"
								+ "	($3 $5)\n"
								+ "	(c_REL:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "		($2 $7)\n"
								+ "		(c_REL:<e,<e,t>> $7 (a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "			($1 $8)\n"
								+ "			(c_ARGX:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (and:<t*,t>\n"
								+ "				(and:<e,t> $9)\n"
								+ "				(c_op1:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $10:e ($0 $10))))\n"
								+ "				(c_op2:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $11:e (and:<t*,t>\n"
								+ "					($0 $11)\n"
								+ "					(c_ARGX-of:<e,<e,t>> $11 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $12:e (include-91:<e,t> $12))))))))))))\n"
								+ "			(c_ARGX:<e,<e,t>> $8 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $13:e (missile:<e,t> $13))))))))))))\n"
								+ "	(c_ARGX:<e,<e,t>> $5 DUMMY:e)\n"
								+ "	(c_REL:<e,<e,t>> $5 $4)))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(UNK:<e,t> $0)\n"
								+ "	(c_REL:<e,<i,t>> $0 1:i)\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (nucleus:<e,t> $1))))\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(UNK:<e,t> $2)\n"
								+ "			(c_ARGX:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(and:<e,t> $3)\n"
								+ "					(c_op1:<e,<e,t>> $3 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $4:e (include-91:<e,t> $4))))))))\n"
								+ "			(c_ARGX:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $5:e (missile:<e,t> $5))))))))\n"
								+ ")))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test6() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:<e,t> (lambda $2:<e,t> (lambda $3:<e,t> (lambda $4:e (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t>\n"
								+ "	(c_REL:<e,<i,t>> $5 1:i)\n"
								+ "	(c_REL:<e,<e,t>> $5 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $6:e (nucleus:<e,t> $6))))\n"
								+ "	($3 $5)\n"
								+ "	(c_REL:<e,<e,t>> $5 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t>\n"
								+ "		($2 $7)\n"
								+ "		(c_REL:<e,<e,t>> $7 (a:<id,<<e,t>,e>> na:id (lambda $8:e (and:<t*,t>\n"
								+ "			($1 $8)\n"
								+ "			(c_ARGX:<e,<e,t>> $8 (a:<id,<<e,t>,e>> na:id (lambda $9:e (and:<t*,t>\n"
								+ "				(and:<e,t> $9)\n"
								+ "				(c_op5:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id book:<e,t>))\n"
								+ "				(c_op1:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $10:e ($0 $10))))\n"
								+ "				(c_op2:<e,<e,t>> $9 (a:<id,<<e,t>,e>> na:id (lambda $11:e (and:<t*,t>\n"
								+ "					($0 $11)\n"
								+ "					(c_ARGX-of:<e,<e,t>> $11 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $12:e (include-91:<e,t> $12))))))))))))\n"
								+ "			(c_ARGX:<e,<e,t>> $8 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $13:e (missile:<e,t> $13))))))))))))\n"
								+ "	(c_ARGX:<e,<e,t>> $5 DUMMY:e)\n"
								+ "	(c_REL:<e,<e,t>> $5 $4)))))))))");
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t>\n"
								+ "	(UNK:<e,t> $0)\n"
								+ "	(c_REL:<e,<i,t>> $0 1:i)\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $1:e (nucleus:<e,t> $1))))\n"
								+ "	(c_REL:<e,<e,t>> $0 \n"
								+ "		(a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t>\n"
								+ "			(UNK:<e,t> $2)\n"
								+ "			(c_ARGX:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t>\n"
								+ "					(and:<e,t> $3)\n"
								+ "				(c_op2:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id book:<e,t>))\n"
								+ "					(c_op1:<e,<e,t>> $3 \n"
								+ "						(a:<id,<<e,t>,e>> na:id (lambda $4:e (include-91:<e,t> $4))))))))\n"
								+ "			(c_ARGX:<e,<e,t>> $2 \n"
								+ "				(a:<id,<<e,t>,e>> na:id (lambda $5:e (missile:<e,t> $5))))))))\n"
								+ ")))");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test7() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,<e,t>> (lambda $1:e ($0 (a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t> (project:<e,t> $2) (c_REL:<e,<e,t>> $2 (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t> (c_REL:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (c_REL:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t> (c_op:<e,<txt,t>> $5 U.S.:txt) (name:<e,t> $5))))) (country:<e,t> $4))))) (country:<e,t> $3)))))))) $1)))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test8() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:<e,t> (lambda $1:<e,<e,t>> (lambda $2:e ($1 (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t> (blacklist-01:<e,t> $3) (c_ARGX:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (c_decade:<e,<i,t>> $4 60:i) (c_REL:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (also:<e,t> $5)))) ($0 $4))))) (c_ARGX:<e,<e,t>> $3 (a:<id,<<e,t>,e>> na:id (lambda $6:e (and:<t*,t> (organization:<e,t> $6) (c_REL:<e,<e,t>> $6 (a:<id,<<e,t>,e>> na:id (lambda $7:e (and:<t*,t> (c_op:<e,<txt,t>> $7 European++Union:txt) (name:<e,t> $7)))))))))))) $2))))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test9() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(lambda $0:e (lambda $1:<e,t> (lambda $2:<e,<e,t>> (lambda $3:e ($2 (a:<id,<<e,t>,e>> na:id (lambda $4:e (and:<t*,t> (c_ARGX:<e,<e,t>> $4 (a:<id,<<e,t>,e>> na:id (lambda $5:e (and:<t*,t> (facility:<e,t> $5) (c_ARGX:<e,<e,t>> $5 $0))))) ($1 $4)))) $3)))))");
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics("boo:e");
		final LogicalExpression actual = SloppyAmrClosure.of(semantics);
		Assert.assertEquals(expected, actual);
	}

}
