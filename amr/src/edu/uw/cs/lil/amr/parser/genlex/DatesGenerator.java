package edu.uw.cs.lil.amr.parser.genlex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jregex.Matcher;
import jregex.Pattern;
import edu.cornell.cs.nlp.spf.base.token.TokenSeq;
import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.ccg.categories.syntax.Syntax;
import edu.cornell.cs.nlp.spf.ccg.lexicon.LexicalEntry;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.explat.IResourceRepository;
import edu.cornell.cs.nlp.spf.explat.ParameterizedExperiment.Parameters;
import edu.cornell.cs.nlp.spf.explat.resources.IResourceObjectCreator;
import edu.cornell.cs.nlp.spf.explat.resources.usage.ResourceUsage;
import edu.cornell.cs.nlp.spf.mr.lambda.Lambda;
import edu.cornell.cs.nlp.spf.mr.lambda.Literal;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalConstant;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.Variable;
import edu.cornell.cs.nlp.spf.parser.ISentenceLexiconGenerator;
import edu.cornell.cs.nlp.utils.collections.ArrayUtils;
import edu.cornell.cs.nlp.utils.collections.MapUtils;
import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.lambda.AMRServices;

/**
 * Rule based dynamic lexical entry generator for dates.
 *
 * @author Yoav Artzi
 */
public class DatesGenerator implements
		ISentenceLexiconGenerator<SituatedSentence<AMRMeta>, LogicalExpression> {

	public static final ILogger			LOG					= LoggerFactory
																	.create(DatesGenerator.class);

	private static final Pattern		COMPACT_DATESTAMP	= new Pattern(
																	"({year}\\d\\d|\\d\\d\\d\\d)(-|)({mon}[0-1]\\d)(-|)({day}[0-3]\\d)");

	private static final long			serialVersionUID	= -5029477188859662272L;

	private final String				compactDatastampLabel;

	private final String				compactDatastampLabel1900;

	private final String				compactDatastampLabel2000;

	private final LogicalConstant		dateEntityPredicate;
	private final String				datestampLabel;

	private final String				datestampLabelMonth;

	private final String				datestampLabelVerbose;

	private final String				datestampLabelYear;

	private final LogicalConstant		dayRelation;

	private final Syntax				entitySyntax;

	private final LogicalConstant		monthRelation;

	private final Map<String, Integer>	months;

	private final LogicalConstant		yearRelation;

	public DatesGenerator(String baseLabel, LogicalConstant dayRelation,
			LogicalConstant yearRelation, LogicalConstant monthRelation,
			LogicalConstant dateEntityPredicate, Syntax entitySyntax) {
		this.dayRelation = dayRelation;
		this.yearRelation = yearRelation;
		this.monthRelation = monthRelation;
		this.dateEntityPredicate = dateEntityPredicate;
		this.entitySyntax = entitySyntax;
		this.datestampLabel = baseLabel + "-datestamp";
		this.datestampLabelVerbose = baseLabel + "-datestamp-verbose";
		this.datestampLabelYear = baseLabel + "-datestamp-year";
		this.datestampLabelMonth = baseLabel + "-datestamp-month";
		this.compactDatastampLabel = baseLabel + "-cdatestamp";
		this.compactDatastampLabel2000 = baseLabel + "-cdatestamp2000";
		this.compactDatastampLabel1900 = baseLabel + "-cdatestamp1900";

		final Map<String, Integer> m = new HashMap<>();
		m.put("january", 1);
		m.put("jan", 1);
		m.put("february", 2);
		m.put("feb", 2);
		m.put("march", 3);
		m.put("mar", 3);
		m.put("april", 4);
		m.put("apr", 4);
		m.put("may", 5);
		m.put("june", 6);
		m.put("jun", 6);
		m.put("july", 7);
		m.put("jul", 7);
		m.put("august", 8);
		m.put("aug", 8);
		m.put("september", 9);
		m.put("sep", 9);
		m.put("october", 10);
		m.put("oct", 10);
		m.put("november", 11);
		m.put("nov", 11);
		m.put("december", 12);
		m.put("dec", 12);
		this.months = Collections.unmodifiableMap(m);
	}

	@Override
	public Set<LexicalEntry<LogicalExpression>> generateLexicon(
			SituatedSentence<AMRMeta> sample) {
		final TokenSeq tokens = sample.getTokens();
		final int length = tokens.size();
		final Set<LexicalEntry<LogicalExpression>> entries = new HashSet<>();
		final List<Integer> integers = new ArrayList<>(tokens.size());
		for (int i = 0; i < tokens.size(); ++i) {
			integers.add(NumeralGenerator.stringToInt(tokens.get(i)));
		}

		// 2002 06 29
		// (a:<id,<<e,t>,e>> !1 (lambda $0:e (and:<t*,t>
		// (date-entity:<e,t> $0)
		// (c_year:<e,<i,t>> $0 2002:i)
		// (c_month:<e,<i,t>> $0 6:i)
		// (c_day:<e,<i,t>> $0 29:i))))
		if (length >= 3) {
			for (int i = 0; i < length - 2; ++i) {
				if (tokens.get(i).length() == 4 && integers.get(i) != null
						&& integers.get(i + 1) != null
						&& integers.get(i + 2) != null) {
					final Category<LogicalExpression> dateEntity = createDateEntity(
							integers.get(i), integers.get(i + 1),
							integers.get(i + 2));
					if (dateEntity != null) {
						entries.add(new LexicalEntry<>(tokens.sub(i, i + 3),
								dateEntity, true, MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY,
										datestampLabel)));
					}
				}

			}
		}

		// March (month only).
		for (int i = 0; i < length; ++i) {
			if (months.containsKey(tokens.get(i).toLowerCase())) {
				entries.add(new LexicalEntry<>(tokens.sub(i, i + 1),
						createDateEntity(null,
								months.get(tokens.get(i).toLowerCase()), null),
						true, MapUtils.createSingletonMap(
								LexicalEntry.ORIGIN_PROPERTY,
								datestampLabelMonth)));
			}
		}

		// 2007-02-27
		// 20030106
		// 03-02-10 or 030210 (dash is optional)
		// 03-02-10 :- NP : (a:<id,<<e,t>,e>> na:id (lambda $20:e (and:<t*,t>
		// (date-entity:<e,t> $20) (c_day:<e,<i,t>> $20 10:i) (c_month:<e,<i,t>>
		// $20 2:i) (c_year:<e,<i,t>> $20 2003:i))))
		for (int i = 0; i < tokens.size(); ++i) {
			final TokenSeq token = tokens.sub(i, i + 1);
			final Matcher matcher = COMPACT_DATESTAMP.matcher(token.toString());
			if (matcher.matches()) {
				final Integer dayNum = NumeralGenerator.stringToInt(matcher
						.group("day"));
				final Integer monNum = NumeralGenerator.stringToInt(matcher
						.group("mon"));
				final Integer yearNum = NumeralGenerator.stringToInt(matcher
						.group("year"));

				if (dayNum != null && monNum != null && yearNum != null) {

					if (yearNum < 100) {
						// 1900s.
						final Category<LogicalExpression> dateEntity = createDateEntity(
								yearNum + 1900, monNum, dayNum);
						if (dateEntity != null) {
							entries.add(new LexicalEntry<>(token, dateEntity,
									true, MapUtils.createSingletonMap(
											LexicalEntry.ORIGIN_PROPERTY,
											compactDatastampLabel1900)));
						}

						// 2000s.
						final Category<LogicalExpression> dateEntity2 = createDateEntity(
								yearNum + 2000, monNum, dayNum);
						if (dateEntity2 != null) {
							entries.add(new LexicalEntry<>(token, dateEntity2,
									true, MapUtils.createSingletonMap(
											LexicalEntry.ORIGIN_PROPERTY,
											compactDatastampLabel2000)));
						}
					} else {
						// Case fully specified year.
						final Category<LogicalExpression> dateEntity = createDateEntity(
								yearNum, monNum, dayNum);
						if (dateEntity != null) {
							entries.add(new LexicalEntry<>(token, dateEntity,
									true, MapUtils.createSingletonMap(
											LexicalEntry.ORIGIN_PROPERTY,
											compactDatastampLabel)));
						}
					}

				}
			}
		}

		// November 27 , 2001 :- NP : (a:<id,<<e,t>,e>> na:id (lambda $0:e
		// (and:<t*,t> (date-entity:<e,t> $0) (c_month:<e,<i,t>> $0 11:i)
		// (c_day:<e,<i,t>> $0 27:i) (c_year:<e,<i,t>> $0 2001:i))))
		if (length >= 4) {
			for (int i = 0; i < length - 3; ++i) {
				final Integer monthNum = getMonth(tokens.get(i));
				if (monthNum != null && tokens.get(i + 3).length() == 4
						&& tokens.get(i + 2).equals(",")
						&& integers.get(i + 3) != null
						&& integers.get(i + 1) != null) {
					final Integer dayNum = integers.get(i + 1);
					final Integer yearNum = integers.get(i + 3);
					final Category<LogicalExpression> dateEntity = createDateEntity(
							yearNum, monthNum, dayNum);
					if (dateEntity != null) {
						entries.add(new LexicalEntry<>(tokens.sub(i, i + 4),
								dateEntity, true, MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY,
										datestampLabelVerbose)));
					}
				}
			}
		}

		// November 2001.
		if (length >= 2) {
			for (int i = 0; i < length - 1; ++i) {
				final Integer monNum = getMonth(tokens.get(i));
				if (monNum != null && tokens.get(i + 1).length() == 4
						&& integers.get(i + 1) != null) {
					final Category<LogicalExpression> dateEntity = createDateEntity(
							integers.get(i + 1), monNum, null);
					if (dateEntity != null) {
						entries.add(new LexicalEntry<>(tokens.sub(i, i + 2),
								dateEntity, true, MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY,
										datestampLabelVerbose)));
					}
				}

			}
		}

		// 2001.
		if (length >= 1) {
			for (int i = 0; i < length; ++i) {
				if (tokens.get(i).length() == 4 && integers.get(i) != null) {
					final Category<LogicalExpression> dateEntity = createDateEntity(
							integers.get(i), null, null);
					if (dateEntity != null) {
						entries.add(new LexicalEntry<>(tokens.sub(i, i + 1),
								dateEntity, true, MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY,
										datestampLabelYear)));
					}
				}

			}
		}

		// 27 November 2001.
		if (length >= 3) {
			for (int i = 0; i < length - 2; ++i) {
				final Integer monNum = getMonth(tokens.get(i + 1));
				if (monNum != null && tokens.get(i + 2).length() == 4
						&& integers.get(i + 2) != null
						&& integers.get(i) != null) {
					final Category<LogicalExpression> dateEntity = createDateEntity(
							integers.get(i + 2), monNum, integers.get(i));
					if (dateEntity != null) {
						entries.add(new LexicalEntry<>(tokens.sub(i, i + 3),
								dateEntity, true, MapUtils.createSingletonMap(
										LexicalEntry.ORIGIN_PROPERTY,
										datestampLabelVerbose)));
					}
				}

			}
		}

		return entries;
	}

	private Category<LogicalExpression> createDateEntity(Integer year,
			Integer month, Integer day) {
		// Create the entity variable.
		final Variable variable = new Variable(LogicLanguageServices
				.getTypeRepository().getEntityType());

		final ArrayList<Literal> dateConjucts = new ArrayList<>();

		// Year, if given.
		if (year != null) {
			final LogicalConstant yearConstant = NumeralGenerator
					.getIntegerConstant(Integer.toString(year), y -> y >= 1900
							&& y <= 2100);
			if (yearConstant != null) {
				dateConjucts.add(new Literal(yearRelation, ArrayUtils
						.<LogicalExpression> create(variable, yearConstant)));
			} else {
				return null;
			}
		}

		// Month, if given.
		if (month != null && month != 0) {
			final LogicalConstant monthConstant = NumeralGenerator
					.getIntegerConstant(Integer.toString(month), n -> n > 0
							&& n <= 12);
			if (monthConstant != null) {
				dateConjucts.add(new Literal(monthRelation, ArrayUtils
						.<LogicalExpression> create(variable, monthConstant)));
			} else {
				return null;
			}
		}

		// Day, if given.
		if (day != null && day != 0) {
			final LogicalConstant dayConstant = NumeralGenerator
					.getIntegerConstant(Integer.toString(day), n -> n > 0
							&& n <= 31);
			if (dayConstant != null) {
				dateConjucts.add(new Literal(dayRelation, ArrayUtils
						.<LogicalExpression> create(variable, dayConstant)));
			} else {
				return null;
			}
		}

		if (dateConjucts.isEmpty()) {
			return null;
		}

		// Add date entity literal.
		dateConjucts.add(new Literal(dateEntityPredicate, ArrayUtils
				.<LogicalExpression> create(variable)));

		return Category
				.create(entitySyntax, AMRServices.skolemize(new Lambda(
						variable, new Literal(LogicLanguageServices
								.getConjunctionPredicate(), dateConjucts
								.toArray(new LogicalExpression[dateConjucts
										.size()])))));

	}

	private Integer getMonth(String monthName) {
		if (months.containsKey(monthName.toLowerCase())) {
			return months.get(monthName.toLowerCase());
		} else {
			return null;
		}
	}

	public static class Creator implements
			IResourceObjectCreator<DatesGenerator> {

		private final String	type;

		public Creator() {
			this("dyngen.amr.dates");
		}

		public Creator(String type) {
			this.type = type;
		}

		@Override
		public DatesGenerator create(Parameters params, IResourceRepository repo) {
			return new DatesGenerator(params.get("label", "dyn-dates"),
					LogicalConstant.read(params.get("day")),
					LogicalConstant.read(params.get("year")),
					LogicalConstant.read(params.get("month")),
					LogicalConstant.read(params.get("entityType")),
					Syntax.read(params.get("syntax")));
		}

		@Override
		public String type() {
			return type;
		}

		@Override
		public ResourceUsage usage() {
			return ResourceUsage
					.builder(type, DatesGenerator.class)
					.addParam("label", String.class,
							"Base origin label (default: dyn-dates)")
					.setDescription(
							"Rule base dynamic lexical entry generator for dates")
					.build();
		}

	}

}
