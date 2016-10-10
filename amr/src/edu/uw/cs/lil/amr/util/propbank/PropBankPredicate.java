package edu.uw.cs.lil.amr.util.propbank;

import java.util.LinkedList;

/**
 * PropBank's extra layer of abstraction that does not appear to be useful.
 *
 * @author Kenton Lee
 * @author Yoav Artzi
 */
public class PropBankPredicate extends LinkedList<PropBankFrame> {

	private static final long	serialVersionUID	= -5303373602036044955L;

	public PropBankPredicate() {
		super();
	}

	public String getLemma() {
		return stream().findFirst().map(f -> f.getLemma()).orElse(null);
	}

	@Override
	public String toString() {
		return String.format("lemma=%s:%s", getLemma(), super.toString());
	}
}
