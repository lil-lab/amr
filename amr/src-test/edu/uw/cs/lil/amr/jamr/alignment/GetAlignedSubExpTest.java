package edu.uw.cs.lil.amr.jamr.alignment;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.uw.cs.lil.amr.TestServices;

public class GetAlignedSubExpTest {

	public GetAlignedSubExpTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final LogicalExpression semantics = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (date-entity:<e,t> $0)\n"
								+ "    (c_year:<e,<i,t>> $0 2002:i)\n"
								+ "    (c_month:<e,<i,t>> $0 1:i)\n"
								+ "    (c_day:<e,<i,t>> $0 5:i))))");
		final String indexSet = "0+0.0+0.1+0.2";
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (date-entity:<e,t> $0)\n"
								+ "    (c_year:<e,<i,t>> $0 2002:i)\n"
								+ "    (c_month:<e,<i,t>> $0 1:i)\n"
								+ "    (c_day:<e,<i,t>> $0 5:i))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test10() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "	  (state-01:<e,t> $0)\n"
								+ "	  (c_ARG0:<e,<e,t>> $0 \n"
								+ "	    (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "	      (government-organization:<e,t> $1)\n"
								+ "	      (c_name:<e,<e,t>> $1 \n"
								+ "	        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "	          (name:<e,t> $2)\n"
								+ "	          (c_op:<e,<txt,t>> $2 U.S.++State++Department:txt)))))))))\n"
								+ "	  (c_ARG1:<e,<e,t>> $0 \n"
								+ "	    (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "	      (safe:<e,t> $3)\n"
								+ "	      (c_polarity:<e,<e,t>> $3 -:e)\n"
								+ "	      (c_domain:<e,<e,t>> $3 \n"
								+ "	        (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "	          (travel-01:<e,t> $4)\n"
								+ "	          (c_ARG1:<e,<e,t>> $4 \n"
								+ "	            (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "	              (area:<e,t> $5)\n"
								+ "	              (c_quant:<e,<e,t>> $5 \n"
								+ "	                (a:<id,<<e,t>,e>> !7 (lambda $6:e (all:<e,t> $6))))\n"
								+ "	              (c_location:<e,<e,t>> $5 \n"
								+ "	                (a:<id,<<e,t>,e>> !8 (lambda $7:e (and:<t*,t>\n"
								+ "	                  (country:<e,t> $7)\n"
								+ "	                  (c_name:<e,<e,t>> $7 \n"
								+ "	                    (a:<id,<<e,t>,e>> !9 (lambda $8:e (and:<t*,t>\n"
								+ "	                      (name:<e,t> $8)\n"
								+ "	                      (c_op:<e,<txt,t>> $8 Afghanistan:txt)))))))))\n"
								+ "	              (c_ARG2-of:<e,<e,t>> $5 \n"
								+ "	                (a:<id,<<e,t>,e>> !10 (lambda $9:e (and:<t*,t>\n"
								+ "	                  (include-91:<e,t> $9)\n"
								+ "	                  (c_ARG1:<e,<e,t>> $9 \n"
								+ "	                    (a:<id,<<e,t>,e>> !11 (lambda $10:e (and:<t*,t>\n"
								+ "	                      (capital:<e,t> $10)\n"
								+ "	                      (c_name:<e,<e,t>> $10 \n"
								+ "	                        (a:<id,<<e,t>,e>> !12 (lambda $11:e (and:<t*,t>\n"
								+ "	                          (name:<e,t> $11)\n"
								+ "	                          (c_op:<e,<txt,t>> $11 Kabul:txt))))))))))))))))))))))))))))");
		final String indexSet = "0.1.1.0.2.0.0+0.1.1.0.2.0.0.0";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !12 (lambda $11:e (and:<t*,t>\n"
								+ "	                          (name:<e,t> $11)\n"
								+ "	                          (c_op:<e,<txt,t>> $11 Kabul:txt))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test11() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "  (state-01:<e,t> $0)\n"
								+ "  (c_ARG0:<e,<e,t>> $0 \n"
								+ "    (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "      (government-organization:<e,t> $1)\n"
								+ "      (c_name:<e,<e,t>> $1 \n"
								+ "        (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "          (name:<e,t> $2)\n"
								+ "          (c_op:<e,<txt,t>> $2 U.S.++State++Department:txt)))))))))\n"
								+ "  (c_ARG1:<e,<e,t>> $0 \n"
								+ "    (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "      (safe:<e,t> $3)\n"
								+ "      (c_polarity:<e,<e,t>> $3 -:e)\n"
								+ "      (c_domain:<e,<e,t>> $3 \n"
								+ "        (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "          (travel-01:<e,t> $4)\n"
								+ "          (c_ARG1:<e,<e,t>> $4 \n"
								+ "            (a:<id,<<e,t>,e>> !6 (lambda $5:e (and:<t*,t>\n"
								+ "              (area:<e,t> $5)\n"
								+ "              (c_quant:<e,<e,t>> $5 \n"
								+ "                (a:<id,<<e,t>,e>> !7 (lambda $6:e (all:<e,t> $6))))\n"
								+ "              (c_location:<e,<e,t>> $5 \n"
								+ "                (a:<id,<<e,t>,e>> !8 (lambda $7:e (and:<t*,t>\n"
								+ "                  (country:<e,t> $7)\n"
								+ "                  (c_name:<e,<e,t>> $7 \n"
								+ "                    (a:<id,<<e,t>,e>> !9 (lambda $8:e (and:<t*,t>\n"
								+ "                      (name:<e,t> $8)\n"
								+ "                      (c_op:<e,<txt,t>> $8 Afghanistan:txt)))))))))\n"
								+ "              (c_ARG2-of:<e,<e,t>> $5 \n"
								+ "                (a:<id,<<e,t>,e>> !10 (lambda $9:e (and:<t*,t>\n"
								+ "                  (include-91:<e,t> $9)\n"
								+ "                  (c_ARG1:<e,<e,t>> $9 \n"
								+ "                    (a:<id,<<e,t>,e>> !11 (lambda $10:e (and:<t*,t>\n"
								+ "                      (capital:<e,t> $10)\n"
								+ "                      (c_name:<e,<e,t>> $10 \n"
								+ "                        (a:<id,<<e,t>,e>> !12 (lambda $11:e (and:<t*,t>\n"
								+ "                          (name:<e,t> $11)\n"
								+ "                          (c_op:<e,<txt,t>> $11 Kabul:txt)))))))))))))))))))))\n"
								+ "      (c_ARG1-of:<e,<e,t>> $3 \n"
								+ "        (a:<id,<<e,t>,e>> !13 (lambda $12:e (and:<t*,t>\n"
								+ "          (cause-01:<e,t> $12)\n"
								+ "          (c_ARG0:<e,<e,t>> $12 \n"
								+ "            (a:<id,<<e,t>,e>> !14 (lambda $13:e (and:<t*,t>\n"
								+ "              (and:<e,t> $13)\n"
								+ "              (c_op1:<e,<e,t>> $13 \n"
								+ "                (a:<id,<<e,t>,e>> !15 (lambda $14:e (and:<t*,t>\n"
								+ "                  (operation:<e,t> $14)\n"
								+ "                  (c_mod:<e,<e,t>> $14 \n"
								+ "                    (a:<id,<<e,t>,e>> !16 (lambda $15:e (military:<e,t> $15))))))))\n"
								+ "              (c_op2:<e,<e,t>> $13 \n"
								+ "                (a:<id,<<e,t>,e>> !17 (lambda $16:e (and:<t*,t>\n"
								+ "                  (mine:<e,t> $16)\n"
								+ "                  (c_location:<e,<e,t>> $16 \n"
								+ "                    (a:<id,<<e,t>,e>> !18 (lambda $17:e (land:<e,t> $17))))))))\n"
								+ "              (c_op3:<e,<e,t>> $13 \n"
								+ "                (a:<id,<<e,t>,e>> !19 (lambda $18:e (banditry:<e,t> $18))))\n"
								+ "              (c_op4:<e,<e,t>> $13 \n"
								+ "                (a:<id,<<e,t>,e>> !20 (lambda $19:e (and:<t*,t>\n"
								+ "                  (rival-01:<e,t> $19)\n"
								+ "                  (c_ARG0:<e,<e,t>> $19 \n"
								+ "                    (a:<id,<<e,t>,e>> !21 (lambda $20:e (and:<t*,t>\n"
								+ "                      (and:<e,t> $20)\n"
								+ "                      (c_op1:<e,<e,t>> $20 \n"
								+ "                        (a:<id,<<e,t>,e>> !22 (lambda $21:e (and:<t*,t>\n"
								+ "                          (group:<e,t> $21)\n"
								+ "                          (c_mod:<e,<e,t>> $21 \n"
								+ "                            (a:<id,<<e,t>,e>> !23 (lambda $22:e (politics:<e,t> $22))))))))\n"
								+ "                      (c_op2:<e,<e,t>> $20 \n"
								+ "                        (a:<id,<<e,t>,e>> !24 (lambda $23:e (and:<t*,t>\n"
								+ "                          (group:<e,t> $23)\n"
								+ "                          (c_mod:<e,<e,t>> $23 \n"
								+ "                            (a:<id,<<e,t>,e>> !25 (lambda $24:e (tribe:<e,t> $24))))))))\n"
								+ "                      (c_ARG1-of:<e,<e,t>> $20 \n"
								+ "                        (a:<id,<<e,t>,e>> !26 (lambda $25:e (arm-01:<e,t> $25))))))))))))\n"
								+ "              (c_op5:<e,<e,t>> $13 \n"
								+ "                (a:<id,<<e,t>,e>> !27 (lambda $26:e (and:<t*,t>\n"
								+ "                  (possible:<e,t> $26)\n"
								+ "                  (c_domain:<e,<e,t>> $26 \n"
								+ "                    (a:<id,<<e,t>,e>> !28 (lambda $27:e (and:<t*,t>\n"
								+ "                      (attack-01:<e,t> $27)\n"
								+ "                      (c_ARG0:<e,<e,t>> $27 \n"
								+ "                        (a:<id,<<e,t>,e>> !29 (lambda $28:e (terrorist:<e,t> $28))))\n"
								+ "                      (c_ARG2-of:<e,<e,t>> $27 \n"
								+ "                        (a:<id,<<e,t>,e>> !30 (lambda $29:e (and:<t*,t>\n"
								+ "                          (include-91:<e,t> $29)\n"
								+ "                          (c_ARG1:<e,<e,t>> $29 \n"
								+ "                            (a:<id,<<e,t>,e>> !31 (lambda $30:e (and:<t*,t>\n"
								+ "                              (attack-01:<e,t> $30)\n"
								+ "                              (c_ARG2-of:<e,<e,t>> $30 \n"
								+ "                                (a:<id,<<e,t>,e>> !32 (lambda $31:e (and:<t*,t>\n"
								+ "                                  (use-01:<e,t> $31)\n"
								+ "                                  (c_ARG1:<e,<e,t>> $31 \n"
								+ "                                    (a:<id,<<e,t>,e>> !33 (lambda $32:e (and:<t*,t>\n"
								+ "                                      (or:<e,t> $32)\n"
								+ "                                      (c_op1:<e,<e,t>> $32 \n"
								+ "                                        (a:<id,<<e,t>,e>> !34 (lambda $33:e (and:<t*,t>\n"
								+ "                                          (bomb:<e,t> $33)\n"
								+ "                                          (c_mod:<e,<e,t>> $33 \n"
								+ "                                            (a:<id,<<e,t>,e>> !35 (lambda $34:e (vehicle:<e,t> $34))))))))\n"
								+ "                                      (c_op2:<e,<e,t>> $32 \n"
								+ "                                        (a:<id,<<e,t>,e>> !36 (lambda $35:e (and:<t*,t>\n"
								+ "                                          (bomb:<e,t> $35)\n"
								+ "                                          (c_mod:<e,<e,t>> $35 \n"
								+ "                                            (a:<id,<<e,t>,e>> !37 (lambda $36:e (other:<e,t> $36))))))))))))))))))))))))))))))))))))))))))))\n"
								+ "  (c_time:<e,<e,t>> $0 \n"
								+ "    (a:<id,<<e,t>,e>> !38 (lambda $37:e (and:<t*,t>\n"
								+ "      (advise-01:<e,t> $37)\n"
								+ "      (c_ARG0:<e,<e,t>> $37 \n"
								+ "        (ref:<id,e> !2))\n"
								+ "      (c_topic:<e,<e,t>> $37 \n"
								+ "        (a:<id,<<e,t>,e>> !39 (lambda $38:e (travel-01:<e,t> $38)))))))))))");
		final String indexSet = "0.1.1.0.2.0.0+0.1.1.0.2.0.0.0";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !12 (lambda $11:e (and:<t*,t>\n"
								+ "	                          (name:<e,t> $11)\n"
								+ "	                          (c_op:<e,<txt,t>> $11 Kabul:txt))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test2() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (country:<e,t> $0)\n"
								+ "    (c_name:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (name:<e,t> $1)\n"
								+ "            (c_op:<e,<txt,t>> $1 Saudi++Arabia:txt))))))))");
		final String indexSet = "0+0.0+0.0.0+0.0.1";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (country:<e,t> $0)\n"
								+ "    (c_name:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (name:<e,t> $1)\n"
								+ "            (c_op:<e,<txt,t>> $1 Saudi++Arabia:txt))))))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test3() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.1.0.4";
		Assert.assertNull(GetAlignedSubExp.of(semantics, indexSet));
	}

	@Test
	public void test4() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.1";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (university:<e,t> $0)))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test5() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)"
								+ "                    (pred:<e,<txt,t>> $2 Aaa++Bbb:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.0+0.0.1";
		final LogicalExpression expected = null;
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test6() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)"
								+ "                    (pred:<e,<txt,t>> $2 Aaa++Bbb:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.0+0.0.0";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e \n"
								+ "                    (name:<e,t> $2)\n"
								+ "))))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test7() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)"
								+ "                    (pred:<e,<txt,t>> $2 Aaa++Bbb:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.0+0.0.0+0.0.0.4";
		Assert.assertNull(GetAlignedSubExp.of(semantics, indexSet));
	}

	@Test
	public void test8() {
		final LogicalExpression semantics = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (run-01:<e,t> $0)\n"
								+ "    (c_ARG0:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "                    (c_op:<e,<txt,t>> $2 Arab++Interior++Ministers'++Council:txt)"
								+ "                    (pred:<e,<txt,t>> $2 Aaa++Bbb:txt)))))))))\n"
								+ "    (c_ARG1:<e,<e,t>> $0\n"
								+ "        (a:<id,<<e,t>,e>> !4 (lambda $3:e (and:<t*,t>\n"
								+ "            (university:<e,t> $3)\n"
								+ "            (c_name:<e,<e,t>> $3\n"
								+ "                (a:<id,<<e,t>,e>> !5 (lambda $4:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $4)\n"
								+ "                    (c_op:<e,<txt,t>> $4 Naif++Arab++Academy++for++Security++Sciences:txt))))))))))))");
		final String indexSet = "0.0+0.0.0+0.0.0.4+0.0.0.5";
		final LogicalExpression expected = TestServices
				.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !2 (lambda $1:e (and:<t*,t>\n"
								+ "            (organization:<e,t> $1)\n"
								+ "            (c_name:<e,<e,t>> $1\n"
								+ "                (a:<id,<<e,t>,e>> !3 (lambda $2:e (and:<t*,t>\n"
								+ "                    (name:<e,t> $2)\n"
								+ "(pred:<e,<txt,t>> $2 Aaa++Bbb:txt))))))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void test9() {
		final LogicalExpression semantics = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (date-entity:<e,t> $0)"
								+ "    (pred:<e,<e,t>> $0 (ref:<id,e> !1))\n"
								+ "    (c_year:<e,<i,t>> $0 2002:i)\n"
								+ "    (c_month:<e,<i,t>> $0 1:i)\n"
								+ "    (c_day:<e,<i,t>> $0 5:i))))");
		final String indexSet = "0+0.0+0.1+0.2";
		final LogicalExpression expected = TestServices.getCategoryServices()
				.readSemantics(
						"(a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>\n"
								+ "    (date-entity:<e,t> $0)\n"
								+ "    (c_year:<e,<i,t>> $0 2002:i)\n"
								+ "    (c_month:<e,<i,t>> $0 1:i)\n"
								+ "    (c_day:<e,<i,t>> $0 5:i))))");
		final LogicalExpression actual = GetAlignedSubExp.of(semantics,
				indexSet);
		Assert.assertEquals(expected, actual);
	}

}
