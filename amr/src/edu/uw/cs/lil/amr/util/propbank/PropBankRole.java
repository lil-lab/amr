package edu.uw.cs.lil.amr.util.propbank;

public class PropBankRole {
	private final String	description;
	private final int		n;
	private final String	type;

	public PropBankRole(int n, String description, String type) {
		this.description = description;
		this.type = type;
		this.n = n;
	}

	public String getDescription() {
		return description;
	}

	public String getType() {
		return type;
	}

	@Override
	public String toString() {
		return String.format("n=%d,type=%s", n, type);
	}
}
