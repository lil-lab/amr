package edu.uw.cs.lil.amr.data;

import java.io.StringReader;
import java.util.stream.Collectors;

import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.data.sentence.ITokenizer;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Stanford CoreNLP PennTreeBank tokenizer wrapper.
 *
 * @author Yoav Artzi
 */
public class Tokenizer implements ITokenizer {

	public static final ILogger	LOG	= LoggerFactory.create(Tokenizer.class);

	private final boolean		dehyphenate;

	public Tokenizer() {
		this(false);
	}

	public Tokenizer(boolean dehyphenate) {
		this.dehyphenate = dehyphenate;
	}

	@Override
	public TokenSeq tokenize(String sentence) {
		return TokenSeq.of(PTBTokenizer
				.newPTBTokenizer(
						new StringReader(dehyphenate ? sentence.replace("-",
								" ") : sentence)).tokenize().stream()
				.map(w -> w.value()).collect(Collectors.toList()));
	}

	public static class Creator implements IResourceObjectCreator<Tokenizer> {

		private final String	type;

		public Creator() {
			this("tokenizer");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public Tokenizer create(Parameters params, IResourceRepository repo) {
			return new Tokenizer(params.getAsBoolean("dehyphenate", false));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, Tokenizer.class)
					.setDescription(
							"Stanford CoreNLP PennTreeBank tokenizer wrapper.")
					.addParam(
							"dehyphenate",
							Boolean.class,
							"Replace all hyphen characters with spaces prior to tokenization (default: false)")
					.build();
		}

	}

}
