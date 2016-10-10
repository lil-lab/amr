# Paths are relative to the .exp file using the parameters file
resDir=../../resources
types=%{resDir}/amr.types
specmap=%{resDir}/amr.specmap
stanfordModel=%{resDir}/stanford-models/english-bidirectional-distsim.tagger
nerTranslation=%{resDir}/amr.ner
nerConfig=%{resDir}/IllinoisNER.config
chartLogs=%{outputDir}/charts
gBeam=50
conditionedBeam=100
gIter=15
logLevel=INFO
trainingMaxSentenceLength=35
smatchPath=../../utils/smatch/smatch.py
propBank=%{resDir}/propbank
learningEstimator=adagrad
