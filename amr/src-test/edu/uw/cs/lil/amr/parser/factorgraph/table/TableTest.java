package edu.uw.cs.lil.amr.parser.factorgraph.table;

import org.junit.Assert;
import org.junit.Test;

import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.uw.cs.lil.amr.TestServices;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;

public class TableTest {

	public TableTest() {
		TestServices.init();
	}

	@Test
	public void test() {
		final ColumnHeader h1 = new ColumnHeader(new LogicalConstantNode(
				LogicalConstant.read("boo:e"), ArrayUtils.create(
						LogicalConstant.read("boo1:e"),
						LogicalConstant.read("boo2:e")), 1));
		final ColumnHeader h2 = new ColumnHeader(new LogicalConstantNode(
				LogicalConstant.read("bo:e"), ArrayUtils.create(
						LogicalConstant.read("bo1:e"),
						LogicalConstant.read("bo2:e"),
						LogicalConstant.read("bo3:e")), 2));
		final Table table = new Table(false, h1, h2);
		table.setAll(1.0);
		Assert.assertEquals(
				3.0,
				table.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo1:e"))), 0.0);
		Assert.assertEquals(
				3.0,
				table.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo2:e"))), 0.0);
		Assert.assertEquals(
				2.0,
				table.get(MappingPair.of(h2.getNode(),
						LogicalConstant.read("bo2:e"))), 0.0);
		for (final LogicalExpression h1Assignment : h1.getNode()
				.slowGetAssignments()) {
			for (final LogicalExpression h2Assignment : h2.getNode()
					.slowGetAssignments()) {
				Assert.assertEquals(1.0, table.get(
						MappingPair.of(h1.getNode(), h1Assignment),
						MappingPair.of(h2.getNode(), h2Assignment)), 0.0);
			}
		}

		final Table table2 = new Table(false, h1);
		table2.setAll(2.0);
		table2.set(10.0,
				MappingPair.of(h1.getNode(), LogicalConstant.read("boo1:e")));
		table2.set(3.0,
				MappingPair.of(h1.getNode(), LogicalConstant.read("boo2:e")));
		for (final LogicalExpression assignment : h1.getNode()
				.slowGetAssignments()) {
			final MappingPair pair = MappingPair.of(h1.getNode(), assignment);
			table2.multiply(table.get(pair), pair);
		}
		Assert.assertEquals(
				30.0,
				table2.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo1:e"))), 0.0);
		Assert.assertEquals(
				9.0,
				table2.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo2:e"))), 0.0);
	}

	@Test
	public void test2() {
		final ColumnHeader h1 = new ColumnHeader(new LogicalConstantNode(
				LogicalConstant.read("boo:e"), ArrayUtils.create(
						LogicalConstant.read("boo1:e"),
						LogicalConstant.read("boo2:e")), 1));
		final ColumnHeader h2 = new ColumnHeader(new LogicalConstantNode(
				LogicalConstant.read("bo:e"), ArrayUtils.create(
						LogicalConstant.read("bo1:e"),
						LogicalConstant.read("bo2:e"),
						LogicalConstant.read("bo3:e")), 2));
		final Table table = new Table(true, h1, h2);
		table.setAll(1.0);
		Assert.assertEquals(
				2.0986122886681096,
				table.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo1:e"))), 0.1);
		Assert.assertEquals(
				2.0986122886681096,
				table.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo2:e"))), 0.1);
		Assert.assertEquals(
				1.6931471805599452,
				table.get(MappingPair.of(h2.getNode(),
						LogicalConstant.read("bo2:e"))), 0.1);
		for (final LogicalExpression h1Assignment : h1.getNode()
				.slowGetAssignments()) {
			for (final LogicalExpression h2Assignment : h2.getNode()
					.slowGetAssignments()) {
				Assert.assertEquals(1.0, table.get(
						MappingPair.of(h1.getNode(), h1Assignment),
						MappingPair.of(h2.getNode(), h2Assignment)), 0.0);
			}
		}

		final Table table2 = new Table(true, h1);
		table2.setAll(2.0);
		table2.set(10.0,
				MappingPair.of(h1.getNode(), LogicalConstant.read("boo1:e")));
		table2.set(3.0,
				MappingPair.of(h1.getNode(), LogicalConstant.read("boo2:e")));
		for (final LogicalExpression assignment : h1.getNode()
				.slowGetAssignments()) {
			final MappingPair pair = MappingPair.of(h1.getNode(), assignment);
			table2.multiply(table.get(pair), pair);
		}
		Assert.assertEquals(
				10.0 * 2.0986122886681096,
				table2.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo1:e"))), 0.1);
		Assert.assertEquals(
				3.0 * 2.0986122886681096,
				table2.get(MappingPair.of(h1.getNode(),
						LogicalConstant.read("boo2:e"))), 0.1);
	}

}
