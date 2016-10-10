package edu.uw.cs.lil.amr.ner;

import java.io.Serializable;
import java.util.Set;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;

/**
 * @author Yoav Artzi
 */
public class RecognizedNamedEntity implements Serializable {

	private static final long			serialVersionUID	= 8819305808920350055L;

	private final String				rawTag;

	private final LogicalConstant		textConstant;

	private final TokenSeq				tokens;

	private final Set<LogicalConstant>	typingConstants;

	public RecognizedNamedEntity(LogicalConstant textConstant,
			Set<LogicalConstant> typingConstants, String rawTag, TokenSeq tokens) {
		assert textConstant != null;
		assert typingConstants != null;
		assert rawTag != null;
		assert tokens != null;
		this.tokens = tokens;
		this.typingConstants = typingConstants;
		this.textConstant = textConstant;
		this.rawTag = rawTag;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final RecognizedNamedEntity other = (RecognizedNamedEntity) obj;
		if (rawTag == null) {
			if (other.rawTag != null) {
				return false;
			}
		} else if (!rawTag.equals(other.rawTag)) {
			return false;
		}
		if (textConstant == null) {
			if (other.textConstant != null) {
				return false;
			}
		} else if (!textConstant.equals(other.textConstant)) {
			return false;
		}
		if (tokens == null) {
			if (other.tokens != null) {
				return false;
			}
		} else if (!tokens.equals(other.tokens)) {
			return false;
		}
		if (typingConstants == null) {
			if (other.typingConstants != null) {
				return false;
			}
		} else if (!typingConstants.equals(other.typingConstants)) {
			return false;
		}
		return true;
	}

	public String getRawTag() {
		return rawTag;
	}

	public LogicalConstant getTextConstant() {
		return textConstant;
	}

	public TokenSeq getTokens() {
		return tokens;
	}

	public Set<LogicalConstant> getTypingConstants() {
		return typingConstants;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (rawTag == null ? 0 : rawTag.hashCode());
		result = prime * result
				+ (textConstant == null ? 0 : textConstant.hashCode());
		result = prime * result + (tokens == null ? 0 : tokens.hashCode());
		result = prime * result
				+ (typingConstants == null ? 0 : typingConstants.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "RecognizedNamedEntity [rawTag=" + rawTag + ", textConstant="
				+ textConstant + ", tokens=" + tokens + ", typingConstants="
				+ typingConstants + "]";
	}

}
