## Tokenizer
type=tokenizer id=tokenizer
## EasyCCG parser to get CCGBank spans and super tags
type=easyccg id=easyccg model=../../resources/easyccg-model-rebank unaryRules=../../resources/easyccg-model-rebank/unaryRules extraCombinators=../../resources/easyccg-model-rebank/binaryRules seenRules=../../resources/easyccg-model-rebank/seenRules nBest=5
