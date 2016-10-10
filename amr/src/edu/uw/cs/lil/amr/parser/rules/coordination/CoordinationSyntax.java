package edu.uw.cs.lil.amr.parser.rules.coordination;

import java.io.ObjectStreamException;

import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax.SimpleSyntax;

/**
 * @author Yoav Artzi
 */
public class CoordinationSyntax extends SimpleSyntax {
	private static final long	serialVersionUID	= 3822623067780514909L;
	private final Syntax		coordinatedSyntax;

	public CoordinationSyntax(Syntax coordinatedSyntax) {
		super(Syntax.C.getLabel() + "{" + coordinatedSyntax.toString() + "}");
		this.coordinatedSyntax = coordinatedSyntax;
	}

	public Syntax getCoordinatedSyntax() {
		return coordinatedSyntax;
	}

	/**
	 * Override the readResolve() in {@link SimpleSyntax} to allow for
	 * serialization.
	 */
	@Override
	protected Object readResolve() throws ObjectStreamException {
		return this;
	}

}
