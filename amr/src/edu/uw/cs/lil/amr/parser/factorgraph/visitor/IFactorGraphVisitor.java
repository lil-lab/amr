package edu.uw.cs.lil.amr.parser.factorgraph.visitor;

import edu.uw.cs.lil.amr.parser.factorgraph.nodes.IBaseNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LambdaNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LiteralNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.LogicalConstantNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.SkolemIdNode;
import edu.uw.cs.lil.amr.parser.factorgraph.nodes.VariableNode;

public interface IFactorGraphVisitor {

	default void visit(IBaseNode node) {
		node.accept(this);
	}

	default void visit(LambdaNode node) {
		node.getArgument().accept(this);
		node.getBody().accept(this);
	}

	default void visit(LiteralNode node) {
		node.getPredicate().accept(this);
		node.getArgs().stream().forEach(a -> a.accept(this));
	}

	default void visit(@SuppressWarnings("unused") LogicalConstantNode node) {
		// Nothing to do by default.
	}

	default void visit(@SuppressWarnings("unused") SkolemIdNode node) {
		// Nothing to do by default.
	}

	default void visit(@SuppressWarnings("unused") VariableNode node) {
		// Nothing to do by default.
	}

}
