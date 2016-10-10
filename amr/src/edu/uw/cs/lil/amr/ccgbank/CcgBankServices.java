package edu.uw.cs.lil.amr.ccgbank;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import uk.ac.ed.easyccg.syntax.Category;
import uk.ac.ed.easyccg.syntax.SyntaxTreeNode;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.ComplexSyntax;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Slash;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class CcgBankServices {

	public static final ILogger	LOG	= LoggerFactory
											.create(CcgBankServices.class);

	/**
	 * Apply some rewrite rules to translate the syntax from the more
	 * conventional CCGBank syntax.
	 */
	public static Set<Syntax> rewrite(Set<Syntax> originals, String token,
			boolean aggressive) {
		final Set<Syntax> rewrites = new HashSet<>();
		for (final Syntax syntax : originals) {
			rewrites.addAll(rewrite(token, syntax, aggressive));
		}
		return rewrites;
	}

	/**
	 * @param syntax
	 *            Input syntax.
	 * @param aggressive
	 *            Aggressively over-generate rewrites.
	 */
	public static Set<Syntax> rewrite(final Syntax syntax, boolean aggressive) {
		final Set<Syntax> rewrites = new HashSet<>();
		rewrites.add(syntax);

		// Shifting Ns and NPs to adjectives often occurs in the lexicon:
		// N/N -> N and N/N -> NP.
		if (aggressive && syntax.equals(Syntax.read("N/N"))) {
			rewrites.add(Syntax.N);
			rewrites.add(Syntax.NP);
		}

		// NP -> S[x]/S[x], only for single NP.
		if (aggressive && syntax.equals(Syntax.NP)) {
			rewrites.add(Syntax.read("S[x]/S[x]"));
		}

		// N[x] -> N[x]/S and N[x] -> N[x]\S, for nouns that are
		// connected higher than
		// their verb.
		if (aggressive && syntax.unify(Syntax.N) != null) {
			rewrites.add(new ComplexSyntax(syntax, Syntax.S, Slash.FORWARD));
			rewrites.add(new ComplexSyntax(syntax, Syntax.S, Slash.BACKWARD));
		}

		// N -> NP. CCG Bank often assigns Ns to NPs, but later shifts them.
		if (aggressive && syntax.equals(Syntax.N)) {
			rewrites.add(Syntax.NP);
		}

		// S[x]\NP -> S[x], even when S[x]\NP is embedded.
		if (aggressive) {
			rewrites.add(removeAgentiveArgument(syntax));
		}

		// Raised Ns: N[x] -> N[x]\(N[x]/N[x]).
		if (syntax.stripAttributes().equals(Syntax.N)) {
			rewrites.add(syntax.replace(Syntax.read("N"),
					Syntax.read("N[x]\\(N[x]/N[x])")));
			rewrites.add(syntax.replace(Syntax.read("N[pl]"),
					Syntax.read("N[pl]\\(N[pl]/N[pl])")));
			rewrites.add(syntax.replace(Syntax.read("N[sg]"),
					Syntax.read("N[sg]\\(N[sg]/N[sg])")));
			rewrites.add(syntax.replace(Syntax.read("N[nb]"),
					Syntax.read("N[nb]\\(N[nb]/N[nb])")));
		}

		// S\NP/NP -> S, strip bare nouns of their arguments.
		if (aggressive) {
			rewrites.add(syntax.replace(Syntax.read("S\\NP/NP"),
					Syntax.read("S")));
		}

		// PP -> , S for sentence such as "The man is against the machine".
		if (aggressive) {
			rewrites.add(syntax.replace(Syntax.PP, Syntax.read("S")));
		}

		// PP -> N[x]/N[x].
		return rewrites.stream()
				.map(s -> s.replace(Syntax.PP, Syntax.read("N[x]\\N[x]")))
				.collect(Collectors.toSet());
	}

	public static Syntax toSyntax(Category category) {
		try {
			final String syntaxString = category.toString()
					.replace("conj", "C").replace("PR", "(S[x]\\S[x])")
					.replace("NP[nb]", "NP[sg]");
			if (syntaxString.equals(".") || syntaxString.equals(",")
					|| syntaxString.equals(";") || syntaxString.equals(":")
					|| syntaxString.equals("RRB") || syntaxString.equals("LRB")
					|| syntaxString.equals("RQU") || syntaxString.equals("LQU")) {
				return Syntax.PUNCT;
			} else {
				return stripSAttributes(Syntax.read(syntaxString));
			}
		} catch (final RuntimeException e) {
			LOG.error("Failed to convert EasyCCG category: %s", category);
			return null;
		}
	}

	public static Syntax toSyntax(SyntaxTreeNode node) {
		final Syntax syntax = toSyntax(node.getCategory());
		if (syntax == null) {
			LOG.error("Failed to convert EasyCCG leaf: %s :- %s",
					node.getWords(), node.getCategory());
		}
		return syntax;
	}

	/**
	 * S[x]\NP -> S[x], even when S[x]\NP is embedded.
	 */
	private static Syntax removeAgentiveArgument(Syntax syntax) {
		if (syntax instanceof ComplexSyntax) {
			final ComplexSyntax complex = (ComplexSyntax) syntax;
			if (complex.getSlash().equals(Slash.BACKWARD)
					&& complex.getLeft().unify(Syntax.S) != null
					&& complex.getRight().equals(Syntax.NP)) {
				return complex.getLeft();
			} else {
				final Syntax left = removeAgentiveArgument(complex.getLeft());
				final Syntax right = removeAgentiveArgument(complex.getRight());
				if (left == complex.getLeft() && right == complex.getRight()) {
					return syntax;
				} else {
					return new ComplexSyntax(left, right, complex.getSlash());
				}
			}
		} else {
			return syntax;
		}
	}

	private static Set<Syntax> rewrite(String token, final Syntax syntax,
			boolean aggressive) {
		final Set<Syntax> rewrites = new HashSet<>();
		rewrites.addAll(rewrite(syntax, aggressive));

		// ; is used to connect keywords in the AMR newswire corpus.
		if (token.equals(";")) {
			rewrites.add(Syntax.read("KEY\\S[adj]"));
			rewrites.add(Syntax.read("KEY\\N"));
			rewrites.add(Syntax.read("KEY\\NP[sg]"));
		}
		return rewrites;
	}

	private static Syntax stripSAttributes(Syntax syntax) {
		if (syntax instanceof ComplexSyntax) {
			final ComplexSyntax complex = (ComplexSyntax) syntax;
			final Syntax left = stripSAttributes(complex.getLeft());
			final Syntax right = stripSAttributes(complex.getRight());
			return new ComplexSyntax(left, right, complex.getSlash());
		} else {
			if (syntax.stripAttributes().equals(Syntax.S)) {
				return Syntax.S;
			} else {
				return syntax;
			}
		}
	}

}
