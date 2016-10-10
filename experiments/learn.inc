# Learning resources
## Logging
type=chart.logger id=logger outputDir=%{chartLogs}
type=chart.logger id=loggerInit outputDir=%{chartLogs} prefix=init
## GENLEX method
type=genlex.template.align id=genlexAlign origin=genlex-align
type=genlex.template.supervised id=genlex maxTokens=2 mark=true
type=genlex.template.text id=genlexText textType=txt generateKeywords=true attributes=sg
type=genlex.composite id=genlexComposite procs=genlexText,genlex
type=genlex.filter.amr id=genlexEntryFilter
type=genlex.split id=genlexSplit beam=%{conditionedBeam} parser=parser filter=filterFactory genlex=genlexComposite sentenceSyntax=A rules=ruleApp,sentToA,bareNoun,barePlural,topicAdverbial,punctuation,bareNE,npToA,massToA,pluralToA logger=logger tokenLimit=1 maxMarking=1 skipReachable=false conservative=true entryFilter=genlexEntryFilter bankspan=true
## Parameter estimator
type=estimator.adagrad id=adagrad initHistory=false rate=0.1
## Gradient function
type=gradient.simple id=gradient hard=true conditionedBeam=%{conditionedBeam}
## Learning tasks (to preform after each iteration)
type=learn.task.test id=learningTester data=test tester=tester exec=exec smatch=%{smatchPath} smatchRounds=20
type=learn.task.log id=learningLogger logger=modelLogger path=%{outputDir} model=model base=model-epoch
type=learn.task.save id=learningSaver model=model dir=%{outputDir} prefix=model-epoch
type=learn.task.snapshot id=learningSnapshot dir=%{outputDir} objects=adagrad
## Learner
### Hybrid batch learner
type=learner.amr.batch.hybrid id=batchHybrid data=train genlex=genlexSplit maxSentenceLength=%{trainingMaxSentenceLength} iter=%{gIter} filterFactory=filterFactory parseLogger=logger postIteration=learningSnapshot,learningSaver,learningLogger,learningTester sortData=true estimator=%{learningEstimator} prune=true gradient=gradient conditionedBeam=%{conditionedBeam} alignGenlex=genlexAlign keepEntries=seedLexiconFactored
