# Distributed resources
type=tinydist.reliable id=manager port=4444 pingFreq=5000 pingTimeout=15000 summary=%{outputDir}/tinydist.summary
type=tester.exec.dist.amr id=testerDist manager=manager
type=learn.task.test id=learningDistTester data=test tester=testerDist exec=exec smatch=%{smatchPath} smatchRounds=20
type=learner.amr.minibatch.dist id=miniBatchDist data=train genlex=genlexSplit maxSentenceLength=%{trainingMaxSentenceLength} iter=%{gIter} filterFactory=filterFactory parseLogger=logger postIteration=learningSnapshot,learningSaver,learningLogger,learningDistTester sortData=true prune=true manager=manager batch=40 estimator=%{learningEstimator} gradient=gradient conditionedBeam=%{conditionedBeam} alignGenlex=genlexAlign keepEntries=seedLexiconFactored
