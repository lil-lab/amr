package edu.uw.cs.lil.amr.util.propbank;

import java.util.HashMap;

public class PropBankFrame extends HashMap<Integer, PropBankRole> {
	private static final long	serialVersionUID	= 7748549274235762647L;
	private final int			id;
	private final String		lemma;

	public PropBankFrame(String fullId) {
		super();
		this.lemma = fullId.split("\\.")[0];
		this.id = Integer.parseInt(fullId.split("\\.")[1]);
	}

	public String getConstantText() {
		return String.format("%s-%02d", lemma, id);
	}

	public int getId() {
		return id;
	}

	public String getLemma() {
		return lemma;
	}

	@Override
	public String toString() {
		return String.format("%s:%s", getConstantText(), super.toString());
	}
}
