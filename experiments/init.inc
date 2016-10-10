# Initialization resources
## Seed lexicon
type=lexicon.underspec id=seedLexicon origin=SEED files=../../resources/seed.lex:../../resources/generic.lex
type=lexicon.factored.pseudo id=seedLexiconFactored entries=seedLexicon
## Model initializers
type=init.lex id=initSeedLex lexicon=seedLexicon
type=scorer.lenexp id=expScorer coef=1.0 exp=1.1
type=init.lex.weights id=initSeedLexWeights lexicon=seedLexiconFactored key=AMRLEX#LEX scorer=expScorer
type=init.lex.weights id=initSeedXemeWeights lexicon=seedLexiconFactored key=AMRLEX#XEME scorer=expScorer
type=init.weights id=initWeights file=../../resources/init.weights
