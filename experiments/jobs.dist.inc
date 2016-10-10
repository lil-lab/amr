# Jobs
## Initialize
type=tinydist.master id=tinydist manager=manager
type=init model=model init=initSeedLex,initSeedLexWeights,initWeights,initSeedXemeWeights id=init
type=listen id=listen model=model dep=init listeners=genlexComposite
## Log initial model
type=save model=model file=%{outputDir}/amr.pre.sp id=save.pre dep=init,listen
type=log id=log.pre model=model logger=modelLogger dep=init,listen
## Learning
type=train learner=miniBatchDist id=train dep=log.pre,save.pre,tinydist model=model
## Log and test final model
type=log id=log.post model=model logger=modelLogger dep=train
type=test id=test exec=exec tester=testerDist data=test stats=statsExact,statsSmatch dep=train
type=save model=model file=%{outputDir}/amr.sp id=save dep=train
