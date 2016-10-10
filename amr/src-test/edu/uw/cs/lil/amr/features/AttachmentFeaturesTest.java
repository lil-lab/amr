package edu.uw.cs.lil.amr.features;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.base.hashvector.HashVectorFactory;
import edu.cornell.cs.nlp.spf.base.hashvector.IHashVector;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.parser.ccg.IParseStep;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.RuleName.Direction;
import edu.uw.cs.lil.amr.TestServices;

public class AttachmentFeaturesTest {

	public AttachmentFeaturesTest() {
		TestServices.init();
	}

	@SafeVarargs
	private static IParseStep<LogicalExpression> createParseStep(
			Category<LogicalExpression> root,
			Category<LogicalExpression>... children) {
		return new IParseStep<LogicalExpression>() {

			@Override
			public Category<LogicalExpression> getChild(int i) {
				return children[i];
			}

			@Override
			public int getEnd() {
				return 1;
			}

			@Override
			public Category<LogicalExpression> getRoot() {
				return root;
			}

			@Override
			public RuleName getRuleName() {
				return RuleName.create("dummy", Direction.FORWARD);
			}

			@Override
			public int getStart() {
				return 0;
			}

			@Override
			public boolean isFullParse() {
				return false;
			}

			@Override
			public int numChildren() {
				return children.length;
			}

			@Override
			public String toString(boolean verbose, boolean recursive) {
				return "dummy";
			}
		};
	}

	@Test
	public void test() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:e (and:<t*,t> (pred:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id type:<e,t>)) (boo:<e,t> $0)))"),
						TestServices.getCategoryServices().read(
								"N : (lambda $0:e (boo:<e,t> $0))")), features,
				new Sentence("dummy"));
		Assert.assertEquals(
				"{ATTACH#boo#pred=1.000, ATTACH#boo#pred#type=1.000, ATTACH#pred#type=1.000}",
				features.toString());
	}

	@Test
	public void test2() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $1:<e,t> (lambda $0:e (and:<t*,t> (pred:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id $1)) (boo:<e,t> $0))))"),
						TestServices.getCategoryServices().read(
								"N : (lambda $0:e (boo:<e,t> $0))")), features,
				new Sentence("dummy"));
		Assert.assertEquals("{}", features.toString());
	}

	@Test
	public void test3() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
										+ "  (trample-01:<e,t> $0)\n"
										+ "  (c_ARG0:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (it:<e,t> $1))))\n"
										+ "  (c_ARG1:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
										+ "      (tradition:<e,t> $2)\n"
										+ "      (c_poss:<e,<e,t>> $2\n"
										+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
										+ "          (country:<e,t> $3)\n"
										+ "          (c_quant:<e,<i,t>> $3 1:i))))))))))))"),
						TestServices.getCategoryServices().read(
								"N : (lambda $0:e (boo:<e,t> $0))")), features,
				new Sentence("dummy"));
		Assert.assertEquals("{ATTACH#c_ARG0#it=1.000, "
				+ "ATTACH#c_ARG1#tradition=1.000, "
				+ "ATTACH#c_poss#country=1.000, " + "ATTACH#c_quant#i=1.000, "
				+ "ATTACH#country#c_quant=1.000, "
				+ "ATTACH#country#c_quant#i=1.000, "
				+ "ATTACH#tradition#c_poss=1.000, "
				+ "ATTACH#tradition#c_poss#country=1.000, "
				+ "ATTACH#trample-01#c_ARG0=1.000, "
				+ "ATTACH#trample-01#c_ARG0#it=1.000, "
				+ "ATTACH#trample-01#c_ARG1=1.000, "
				+ "ATTACH#trample-01#c_ARG1#tradition=1.000}",
				features.toString());
	}

	@Test
	public void test4() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
										+ "  (trample-01:<e,t> $0)\n"
										+ "  (c_ARG0:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (it:<e,t> $1))))\n"
										+ "  (c_ARG1:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
										+ "      (tradition:<e,t> $2)\n"
										+ "      (c_poss:<e,<e,t>> $2\n"
										+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
										+ "          (country:<e,t> $3)\n"
										+ "          (c_quant:<e,<i,t>> $3 1:i))))))))))))"),
						TestServices.getCategoryServices().read(
								"N : (lambda $0:e (boo:<e,t> $0))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $1:<e,t> (a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
										+ "  ($1 $0)\n"
										+ "  (c_ARG0:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (it:<e,t> $1))))\n"
										+ "  (c_ARG1:<e,<e,t>> $0\n"
										+ "    (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
										+ "      (tradition:<e,t> $2)\n"
										+ "      (c_poss:<e,<e,t>> $2\n"
										+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
										+ "          (country:<e,t> $3)\n"
										+ "          (c_quant:<e,<i,t>> $3 1:i)))))))))))))")),
				features, new Sentence("dummy"));
		Assert.assertEquals(
				"{ATTACH#trample-01#c_ARG0=1.000, ATTACH#trample-01#c_ARG0#it=1.000, ATTACH#trample-01#c_ARG1=1.000, ATTACH#trample-01#c_ARG1#tradition=1.000}",
				features.toString());
	}

	@Test
	public void test5() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (a:<id,<<e,t>,e>> na:id (lambda $0:e (and:<t*,t> (c_:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (name:<e,t> $1) (c_op:<e,<txt,t>> $1 Saudi++Arabia:txt))))) (country:<e,t> $0))))"),
						TestServices.getCategoryServices().read(
								"N : (lambda $0:e (boo:<e,t> $0))")), features,
				new Sentence("dummy"));
		Assert.assertEquals(
				"{ATTACH#c_#name=1.000, ATTACH#c_op#txt=1.000, ATTACH#country#c_=1.000, ATTACH#country#c_#name=1.000, ATTACH#name#c_op=1.000, ATTACH#name#c_op#txt=1.000}",
				features.toString());
	}

	@Test
	public void test6() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:e (and:<t*,t> (sponsor-01:<e,t> $1) (c_ARGX:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t> ($0 (lambda $3:e (expert-41:<e,t> $3)) $2) (c_REL:<e,<i,t>> $2 50:i))))))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:<e,t> (lambda $2:e (and:<t*,t> ($0 (lambda $3:e ($1 $3)) $2) (c_REL:<e,<i,t>> $2 50:i)))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:e (and:<t*,t> (sponsor-01:<e,t> $1) (c_ARGX:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $2:e ($0 (lambda $3:e (expert-41:<e,t> $3)) $2)))))))")),
				features, new Sentence("dummy"));
		Assert.assertEquals("{}", features.toString());
	}

	@Test
	public void test7() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<e,t> (lambda $1:e (and:<t*,t> ($0 $1) (c_ARG1-of:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id "
										+ "(lambda $2:e (and:<t*,t> (base-01:<e,t> $2) (c_location:<e,<e,t>> $2 (a:<id,<<e,t>,e>> na:id (lambda $2:e (type:<e,t> $2)))))))))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:<e,t> (lambda $2:e (and:<t*,t> ($0 (lambda $3:e ($1 $3)) $2) (c_REL:<e,<i,t>> $2 50:i)))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:e (and:<t*,t> (sponsor-01:<e,t> $1) (c_ARGX:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $2:e ($0 (lambda $3:e (expert-41:<e,t> $3)) $2)))))))")),
				features, new Sentence("dummy"));
		Assert.assertEquals(
				"{ATTACH#base-01#c_location=1.000, ATTACH#base-01#c_location#type=1.000, ATTACH#c_ARG1-of#base-01=1.000, ATTACH#c_location#type=1.000}",
				features.toString());
	}

	@Test
	public void test8() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<e,t> (lambda $1:e (and:<t*,t> (c_ARG1-of:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id "
										+ "(lambda $2:e (and:<t*,t> (base-01:<e,t> $2) (c_location:<e,<e,t>> $2 (a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t> (type:<e,t> $2) ($0 $2))))))))))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:<e,t> (lambda $2:e (and:<t*,t> ($0 (lambda $3:e ($1 $3)) $2) (c_REL:<e,<i,t>> $2 50:i)))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:<<e,t>,<e,t>> (lambda $1:e (and:<t*,t> (sponsor-01:<e,t> $1) (c_ARGX:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $2:e ($0 (lambda $3:e (expert-41:<e,t> $3)) $2)))))))")),
				features, new Sentence("dummy"));
		Assert.assertEquals(
				"{ATTACH#base-01#c_location=1.000, ATTACH#base-01#c_location#type=1.000, ATTACH#c_ARG1-of#base-01=1.000, ATTACH#c_location#type=1.000}",
				features.toString());
	}

	@Test
	public void test9() {
		final AttachmentFeatures<Sentence> fs = new AttachmentFeatures<>(1.0,
				LogicLanguageServices.getTypeRepository()
						.getTypeCreateIfNeeded("<e,t>"), 1000, 10);

		final IHashVector features = HashVectorFactory.create();
		fs.setFeatures(
				createParseStep(
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:e (and:<t*,t> (name:<e,t> $0) (c_REL:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (and:<e,t> $1) "
										+ "(c_op1:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $2:e (and:<t*,t> (c_op:<e,<txt,t>> $2 Zvezda:txt) (spaceship:<e,t> $2))))) "
										+ "(c_op2:<e,<e,t>> $1 (a:<id,<<e,t>,e>> na:id (lambda $3:e (and:<t*,t> (c_op:<e,<txt,t>> $3 Zarya:txt) (spaceship:<e,t> $3)))))))))))"),
						TestServices
								.getCategoryServices()
								.read("N : (lambda $0:e (and:<t*,t> (name:<e,t> $0) (c_REL:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (c_op:<e,<txt,t>> $1 Zvezda:txt) (spaceship:<e,t> $1)))))))"),
						TestServices
								.getCategoryServices()
								.read("N : (conj:<<e,t>,<e,t>> (lambda $0:e (and:<t*,t> (name:<e,t> $0) (c_REL:<e,<e,t>> $0 (a:<id,<<e,t>,e>> na:id (lambda $1:e (and:<t*,t> (c_op:<e,<txt,t>> $1 Zarya:txt) (spaceship:<e,t> $1))))))))")),
				features, new Sentence("dummy"));
		Assert.assertEquals("{}", features.toString());
	}

}
