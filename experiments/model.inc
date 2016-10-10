# Joint model resources
## Default lexical entry scorers
type=scorer.uniform id=uniform0Scorer   weight=0.0
type=scorer.uniform id=uniform1Scorer   weight=1.0
type=scorer.uniform id=uniform10Scorer  weight=10.0
type=scorer.lex.origin id=lexScorer default=uniform0Scorer
type=scorer.lexeme.cooc id=coocScorer file=%{gizaProbs} lower=true
type=scorer.lexeme.origin id=lexemeScorer default=coocScorer scorers=genlex-txt:uniform1Scorer
## Features
type=feat.logexp.coordination id=featLogexpCoordination cpp1=false cpap=false rept=true
type=feat.lex.amr id=featLex lexemeScorer=lexemeScorer entryScorer=lexScorer
type=feat.rule.shift id=featShiftRule
type=feat.rule.shift.semantics id=featShiftSemantics
type=feat.rule.cross id=featCrossRule
type=feat.attribute.pos id=featAttribPOS
type=feat.amr.relations id=featAttach
type=feat.lex.dyngen id=featLexDyngen
type=feat.lex.sloppy id=featLexSloppy
type=feat.lex.dynskip id=featDynSkip
## Lexicon
type=lexicon.factored.partial id=lexicon
## Model
type=model.joint id=model lexicon=lexicon features=featLogexpCoordination,featLex,featLexDyngen,featAttach,featAttribPOS,featShiftRule,featCrossRule,featShiftSemantics,featLexSloppy,featDynSkip
