/*******************************************************************************
 * UW SPF - The University of Washington Semantic Parsing Framework
 * <p>
 * Copyright (C) 2013 Yoav Artzi
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 ******************************************************************************/
package edu.uw.cs.lil.amr.exp;

import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.FactoredLexicon;
import edu.cornell.cs.nlp.spf.ccg.lexicon.factored.lambda.partial.PartiallyFactoredLexicon;
import edu.cornell.cs.nlp.spf.data.collection.CompositeDataCollection;
import edu.cornell.cs.nlp.spf.data.collection.FilteredDataCollection;
import edu.cornell.cs.nlp.spf.data.sentence.Sentence;
import edu.cornell.cs.nlp.spf.data.sentence.SentenceCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentence;
import edu.cornell.cs.nlp.spf.data.singlesentence.SingleSentenceCollection;
import edu.cornell.cs.nlp.spf.data.singlesentence.lex.SingleSentenceLexDataset;
import edu.cornell.cs.nlp.spf.data.situated.labeled.LabeledSituatedSentence;
import edu.cornell.cs.nlp.spf.data.situated.sentence.SituatedSentence;
import edu.cornell.cs.nlp.spf.data.utils.LabeledValidator;
import edu.cornell.cs.nlp.spf.explat.resources.ResourceCreatorRepository;
import edu.cornell.cs.nlp.spf.explat.resources.SerializedObjectCreator;
import edu.cornell.cs.nlp.spf.genlex.ccg.CompositeLexiconGenerator;
import edu.cornell.cs.nlp.spf.genlex.ccg.template.TemplateSupervisedGenlex;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;
import edu.cornell.cs.nlp.spf.mr.lambda.ccg.SimpleFullParseFilter;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.genlex.MultiCKYParserWithMarkingCreator;
import edu.cornell.cs.nlp.spf.parser.ccg.cky.multi.MultiCKYParser;
import edu.cornell.cs.nlp.spf.parser.ccg.factoredlex.features.SyntaxAttributeFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.factoredlex.features.scorers.LexemeCooccurrenceScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.factoredlex.features.scorers.OriginLexemeScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.DynamicWordSkippingFeatures;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.LexicalFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.LexicalFeaturesInit;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.RuleUsageFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.scorer.ExpLengthLexicalEntryScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.scorer.OriginLexicalEntryScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.scorer.SkippingSensitiveLexicalEntryScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.features.basic.scorer.UniformScorer;
import edu.cornell.cs.nlp.spf.parser.ccg.features.lambda.LogicalExpressionCoordinationFeatureSet;
import edu.cornell.cs.nlp.spf.parser.ccg.joint.cky.JointInferenceChartLogger;
import edu.cornell.cs.nlp.spf.parser.ccg.model.IModelImmutable;
import edu.cornell.cs.nlp.spf.parser.ccg.model.LexiconModelInit;
import edu.cornell.cs.nlp.spf.parser.ccg.model.ModelLogger;
import edu.cornell.cs.nlp.spf.parser.ccg.model.WeightInit;
import edu.cornell.cs.nlp.spf.parser.ccg.normalform.hb.HBNormalFormCreator;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.PluralExistentialTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.ThatlessRelative;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.application.ReversibleApplicationCreator;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeraising.ForwardTypeRaisedComposition;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.AdjectiveTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.AdverbialTopicalisationTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.AdverbialTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.PrepositionTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.ReversibleApplicationTypeShifting;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.lambda.typeshifting.SimpleReversibleApplicationTypeShiftingCreator;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.primitivebinary.composition.CompositionCreator;
import edu.cornell.cs.nlp.spf.parser.ccg.rules.primitivebinary.punctuation.PunctuationRule;
import edu.cornell.cs.nlp.spf.parser.joint.model.JointModel;
import edu.cornell.cs.nlp.spf.reliabledist.EnslavedLocalManager;
import edu.cornell.cs.nlp.spf.reliabledist.ReliableManager;
import edu.cornell.cs.nlp.spf.test.exec.distributed.DistributedExecTester;
import edu.cornell.cs.nlp.spf.test.stats.ExactMatchTestingStatistics;
import edu.uw.cs.lil.amr.ccgbank.easyccg.EasyCCGWrapper;
import edu.uw.cs.lil.amr.data.AMRMeta;
import edu.uw.cs.lil.amr.data.AmrSentenceCollection;
import edu.uw.cs.lil.amr.data.LabeledAmrSentence;
import edu.uw.cs.lil.amr.data.LabeledAmrSentenceCollection;
import edu.uw.cs.lil.amr.data.LabeledAmrSentenceLexCollection;
import edu.uw.cs.lil.amr.data.QuickFilter;
import edu.uw.cs.lil.amr.data.RefFilter;
import edu.uw.cs.lil.amr.data.Tokenizer;
import edu.uw.cs.lil.amr.exec.Exec;
import edu.uw.cs.lil.amr.features.AmrLexicalFeatures;
import edu.uw.cs.lil.amr.features.AttachmentFeatures;
import edu.uw.cs.lil.amr.features.AttributePOSTagFeatures;
import edu.uw.cs.lil.amr.features.CrossingRuleFeatureSet;
import edu.uw.cs.lil.amr.features.DynamicLexicalGeneratorFeatures;
import edu.uw.cs.lil.amr.features.ParseStepSyntaxFeatures;
import edu.uw.cs.lil.amr.features.SemanticShiftingFeatureSet;
import edu.uw.cs.lil.amr.features.ShiftingRuleFeatureSet;
import edu.uw.cs.lil.amr.features.SloppyLexiconFeatures;
import edu.uw.cs.lil.amr.learn.batch.HybridBatchLearner;
import edu.uw.cs.lil.amr.learn.batch.distributed.DistributeMiniBatchLearner;
import edu.uw.cs.lil.amr.learn.estimators.AdaGradEstimator;
import edu.uw.cs.lil.amr.learn.filter.AMRSupervisedFilterFactory;
import edu.uw.cs.lil.amr.learn.genlex.AlignmentGenlex;
import edu.uw.cs.lil.amr.learn.genlex.AmrLexicalGenerationFilter;
import edu.uw.cs.lil.amr.learn.genlex.SplittingGenlex;
import edu.uw.cs.lil.amr.learn.genlex.TextEntitiesGenlex;
import edu.uw.cs.lil.amr.learn.gradient.GradientChecker;
import edu.uw.cs.lil.amr.learn.gradient.SimpleGradient;
import edu.uw.cs.lil.amr.learn.postprocessing.AddAllAlignments;
import edu.uw.cs.lil.amr.learn.tasks.LearningStateSnapshotTask;
import edu.uw.cs.lil.amr.learn.tasks.LoggingLearningTask;
import edu.uw.cs.lil.amr.learn.tasks.SaveLearningTask;
import edu.uw.cs.lil.amr.learn.tasks.TestLearningTask;
import edu.uw.cs.lil.amr.parser.GraphAmrParser;
import edu.uw.cs.lil.amr.parser.constraints.UnaryLexicalConstraint;
import edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen.AssignmentGeneratorFactory;
import edu.uw.cs.lil.amr.parser.factorgraph.assignmentgen.AssignmentGeneratorFactoryStub;
import edu.uw.cs.lil.amr.parser.factorgraph.features.ClosureFeature;
import edu.uw.cs.lil.amr.parser.factorgraph.features.RefControlFeatureSet;
import edu.uw.cs.lil.amr.parser.factorgraph.features.RelationSelectionalPreference;
import edu.uw.cs.lil.amr.parser.factorgraph.features.SurfaceFormFeature;
import edu.uw.cs.lil.amr.parser.factorgraph.features.UnaryBiasFeatures;
import edu.uw.cs.lil.amr.parser.genlex.DatesGenerator;
import edu.uw.cs.lil.amr.parser.genlex.NamedEntityGenerator;
import edu.uw.cs.lil.amr.parser.genlex.NumeralGenerator;
import edu.uw.cs.lil.amr.parser.lexicon.PseudoFactoredLexiconCreator;
import edu.uw.cs.lil.amr.parser.lexicon.UnderspecifiedLexiconCreator;
import edu.uw.cs.lil.amr.parser.rules.LexConstants;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.DetermineNamedEntity;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.NamedEntityKeywordCoordination;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.dummyref.NounPhraseWithDummy;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.dummyref.SentenceWithDummy;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords.DateStamp;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords.KeywordCoordinationInit;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords.NamedEntityStamp;
import edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords.SingleKeyword;
import edu.uw.cs.lil.amr.parser.rules.coordination.CoordinationRuleSet;
import edu.uw.cs.lil.amr.test.AmrDistributedExecTester;
import edu.uw.cs.lil.amr.test.AmrExecTester;
import edu.uw.cs.lil.amr.test.SmatchStats;

public class AmrResourceRepo extends ResourceCreatorRepository {
	public AmrResourceRepo() {
		// CCG parsing rules.
		registerResourceCreator(new CoordinationRuleSet.Creator());
		registerResourceCreator(new ReversibleApplicationCreator());
		registerResourceCreator(new CompositionCreator<LogicalExpression>());
		registerResourceCreator(new PrepositionTypeShifting.Creator());
		registerResourceCreator(new AdverbialTypeShifting.Creator());
		registerResourceCreator(new AdjectiveTypeShifting.Creator());
		registerResourceCreator(
				new AdverbialTopicalisationTypeShifting.Creator());
		registerResourceCreator(
				new ReversibleApplicationTypeShifting.Creator());
		registerResourceCreator(new ForwardTypeRaisedComposition.Creator());
		registerResourceCreator(new ThatlessRelative.Creator());
		registerResourceCreator(new PluralExistentialTypeShifting.Creator());
		registerResourceCreator(
				new PunctuationRule.Creator<LogicalExpression>());
		registerResourceCreator(new DetermineNamedEntity.Creator());
		registerResourceCreator(new DateStamp.Creator());
		registerResourceCreator(new NamedEntityStamp.Creator());
		registerResourceCreator(new NamedEntityKeywordCoordination.Creator());
		registerResourceCreator(new KeywordCoordinationInit.Creator());
		registerResourceCreator(new SingleKeyword.Creator());
		registerResourceCreator(
				new SimpleReversibleApplicationTypeShiftingCreator());
		registerResourceCreator(new NounPhraseWithDummy.Creator());
		registerResourceCreator(new SentenceWithDummy.Creator());

		// Heuristic dynamic lexical generators.
		registerResourceCreator(new DatesGenerator.Creator());
		registerResourceCreator(new NamedEntityGenerator.Creator());
		registerResourceCreator(new NumeralGenerator.Creator());

		// Parsing creators.
		registerResourceCreator(new GraphAmrParser.Creator());
		registerResourceCreator(new AssignmentGeneratorFactory.Creator());
		registerResourceCreator(new HBNormalFormCreator());
		registerResourceCreator(
				new MultiCKYParser.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new SimpleFullParseFilter.Creator());
		registerResourceCreator(
				new MultiCKYParserWithMarkingCreator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new UnaryLexicalConstraint.Creator());
		registerResourceCreator(new LexConstants.Creator());
		registerResourceCreator(new AssignmentGeneratorFactoryStub.Creator());

		// Data creators.
		registerResourceCreator(new SingleSentenceCollection.Creator());
		registerResourceCreator(
				new CompositeDataCollection.Creator<SingleSentence>());
		registerResourceCreator(
				new JointModel.Creator<SituatedSentence<AMRMeta>, LogicalExpression, LogicalExpression>());
		registerResourceCreator(new SingleSentenceLexDataset.Creator());
		registerResourceCreator(new LabeledAmrSentenceCollection.Creator());
		registerResourceCreator(
				new FilteredDataCollection.Creator<LabeledAmrSentence>());
		registerResourceCreator(new SentenceCollection.Creator());
		registerResourceCreator(new LabeledAmrSentenceLexCollection.Creator());
		registerResourceCreator(new AmrSentenceCollection.Creator());

		// Features.
		registerResourceCreator(
				new RuleUsageFeatureSet.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new PseudoFactoredLexiconCreator());
		registerResourceCreator(
				new LexicalFeaturesInit.Creator<Sentence, LogicalExpression>());
		registerResourceCreator(
				new LexicalFeatureSet.Creator<Sentence, LogicalExpression>());
		registerResourceCreator(
				new LogicalExpressionCoordinationFeatureSet.Creator<Sentence>());
		registerResourceCreator(new UnaryBiasFeatures.Creator());
		registerResourceCreator(new ClosureFeature.Creator());
		registerResourceCreator(
				new ParseStepSyntaxFeatures.Creator<LogicalExpression>());
		registerResourceCreator(new DynamicLexicalGeneratorFeatures.Creator());
		registerResourceCreator(
				new CrossingRuleFeatureSet.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(
				new AttachmentFeatures.Creator<SituatedSentence<AMRMeta>>());
		registerResourceCreator(
				new SyntaxAttributeFeatureSet.Creator<SituatedSentence<AMRMeta>>());
		registerResourceCreator(new AttributePOSTagFeatures.Creator());
		registerResourceCreator(
				new ShiftingRuleFeatureSet.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new SurfaceFormFeature.Creator());
		registerResourceCreator(new RelationSelectionalPreference.Creator());
		registerResourceCreator(
				new SemanticShiftingFeatureSet.Creator<SituatedSentence<AMRMeta>>());
		registerResourceCreator(new AmrLexicalFeatures.Creator());
		registerResourceCreator(new RefControlFeatureSet.Creator());
		registerResourceCreator(new DynamicWordSkippingFeatures.Creator<>());

		// Scorers and initializers.
		registerResourceCreator(
				new ExpLengthLexicalEntryScorer.Creator<LogicalExpression>());
		registerResourceCreator(new UniformScorer.Creator<LogicalExpression>());
		registerResourceCreator(
				new SkippingSensitiveLexicalEntryScorer.Creator<LogicalExpression>());
		registerResourceCreator(new LexemeCooccurrenceScorer.Creator());
		registerResourceCreator(
				new OriginLexicalEntryScorer.Creator<LogicalExpression>());
		registerResourceCreator(new OriginLexemeScorer.Creator());
		registerResourceCreator(
				new WeightInit.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());

		// Model creators.
		registerResourceCreator(new FactoredLexicon.Creator());
		registerResourceCreator(
				new SloppyLexiconFeatures.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new PartiallyFactoredLexicon.Creator());

		// Learning creators.
		registerResourceCreator(new AMRSupervisedFilterFactory.Creator());
		registerResourceCreator(new UnderspecifiedLexiconCreator());
		registerResourceCreator(
				new LabeledValidator.Creator<SingleSentence, LogicalExpression>());
		registerResourceCreator(
				new TemplateSupervisedGenlex.Creator<SituatedSentence<AMRMeta>, LabeledSituatedSentence<AMRMeta, LogicalExpression>>());
		registerResourceCreator(
				new TextEntitiesGenlex.Creator<SituatedSentence<AMRMeta>, LabeledSituatedSentence<AMRMeta, LogicalExpression>>());
		registerResourceCreator(
				new LexiconModelInit.Creator<SituatedSentence<AMRMeta>, LogicalExpression>());
		registerResourceCreator(new AdaGradEstimator.Creator());
		registerResourceCreator(new SimpleGradient.Creator());
		registerResourceCreator(new GradientChecker.Creator());
		registerResourceCreator(new DistributeMiniBatchLearner.Creator());
		registerResourceCreator(new HybridBatchLearner.Creator());
		registerResourceCreator(new SplittingGenlex.Creator());
		registerResourceCreator(
				new CompositeLexiconGenerator.Creator<LabeledSituatedSentence<AMRMeta, LogicalExpression>, LogicalExpression, IModelImmutable<Sentence, LogicalExpression>>());
		registerResourceCreator(new AlignmentGenlex.Creator());
		registerResourceCreator(new AmrLexicalGenerationFilter.Creator());

		// Distributed framework.
		registerResourceCreator(new EnslavedLocalManager.Creator());
		registerResourceCreator(new ReliableManager.Creator());

		// Test.
		registerResourceCreator(
				new ExactMatchTestingStatistics.Creator<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence>());
		registerResourceCreator(new AmrDistributedExecTester.Creator());
		registerResourceCreator(new Exec.Creator());
		registerResourceCreator(new AmrExecTester.Creator());
		registerResourceCreator(
				new DistributedExecTester.Creator<SituatedSentence<AMRMeta>, LogicalExpression, LabeledAmrSentence>());
		registerResourceCreator(new SmatchStats.Creator());

		// Logging.
		registerResourceCreator(new ModelLogger.Creator());
		registerResourceCreator(
				new JointInferenceChartLogger.Creator<LogicalExpression, LogicalExpression>());

		// Expdist (the internal experiments framework) tasks.
		registerResourceCreator(new LoggingLearningTask.Creator());
		registerResourceCreator(new SaveLearningTask.Creator());
		registerResourceCreator(new TestLearningTask.Creator());
		registerResourceCreator(new LearningStateSnapshotTask.Creator());
		registerResourceCreator(new SerializedObjectCreator());

		// Others.
		registerResourceCreator(new AddAllAlignments.Creator());
		registerResourceCreator(new EasyCCGWrapper.Creator());
		registerResourceCreator(new RefFilter.Creator());
		registerResourceCreator(new Tokenizer.Creator());
		registerResourceCreator(new QuickFilter.Creator());

	}
}
