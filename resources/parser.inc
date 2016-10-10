# Parsing rules
## Lexical rule
type=rule.lex.amr id=lexRule
## Binary rules
type=rule.composition id=ruleComp maxOrder=3 crossing=true
type=rule.application.reversible id=ruleApp maxDepth=9 attributes=sg,pl
type=rule.punctuation id=punctuation
### Coordination
type=rule.coordination.amr id=coordination 
## Unary rules
### Topicalized adverbial phrase
type=rule.shifting.generic.application.reversible id=topicAdverbial name=topic_ap function=S[x]/S[x]/(S[x]\S[x])\ :\ (lambda\ $0:<<e,t>,<e,t>>\ $0) startOnly=true
### Bare plurals: N[pl]->NP (i.e., 'apples')
type=rule.shifting.generic.application.reversible id=barePlural matchSyntax=true name=bare_pl function=NP[pl]/N[pl]\ :\ (lambda\ $0:<e,t>\ (a:<id,<<e,t>,e>>\ na:id\ $0))
### Bare nouns: N[nb]->NP (i.e., mass nouns like 'water')
type=rule.shifting.generic.application.reversible id=bareNoun matchSyntax=true name=bare_noun function=NP[sg]/N[nb]\ :\ (lambda\ $0:<e,t>\ (a:<id,<<e,t>,e>>\ na:id\ $0))
### Bare named entities
type=rule.shifting.amr.determine.ne id=bareNE source=N[sg] target=NP[sg]
### Declarative sentences: S->A
type=rule.shifting.generic.application.reversible id=sentToA name=s_a completeOnly=true function=A/S\ :\ (lambda\ $0:<e,t>\ (a:<id,<<e,t>,e>>\ na:id\ $0))
### Sentences that are only noun phrases
type=rule.shifting.generic.application.reversible id=pluralToA name=n_pl_a completeOnly=true function=A/N[pl]\ :\ (lambda\ $0:<e,t>\ (a:<id,<<e,t>,e>>\ na:id\ $0))
type=rule.shifting.generic.application.reversible id=massToA name=n_nb_a completeOnly=true function=A/N[nb]\ :\ (lambda\ $0:<e,t>\ (a:<id,<<e,t>,e>>\ na:id\ $0))
type=rule.shifting.generic.application.reversible id=npToA name=np_a completeOnly=true function=A/NP\ :\ (lambda\ $0:e\ $0)
### Simple category-based reversible type-shifting rules.
type=rule.shifting.generic.reversible.set id=shifts file=%{resDir}/amr.shifts
### Date stamps: NP[date]->A
type=rule.shift.amr.datestamp id=dateStamp
### NE key words
type=rule.shift.amr.nestamp id=neStamp
type=rule.shifting.amr.necoord id=neStampCoord syntax=NP[sg]
### Keywords (incl. coordination)
type=rule.shifting.amr.keywordc1 id=keywordC1
type=rule.shifting.generic.application.reversible id=keywordS name=keyword_s function=A/KEY\ :\ (lambda\ $0:e\ $0) completeOnly=true matchSyntax=true
type=rule.shift.amr.keyword.single id=singleKeyword
# NF constraints
type=constraint.unary.lex id=unaryLexConstraint rules=np_adj_rel,np_adj_arg0,np_adj_arg1,np_adj_rel_of
type=nf.hb id=nfValidator extraConstraints=unaryLexConstraint
# Dynamic lexical generators.
type=dyngen.amr.dates id=dyngenDates day=c_day:<e,<i,t>> year=c_year:<e,<i,t>> month=c_month:<e,<i,t>> entityType=date-entity:<e,t> syntax=NP[sg]
type=dyngen.amr.numeral id=dyngenNumeral
type=dyngen.amr.ner id=dyngenNER
# CKY Parser
type=parser.cky.multi.marked id=baseParser wordSkipping=aggressive markPruning=true generators=dyngenDates,dyngenNumeral,dyngenNER nfValidator=nfValidator beam=%{gBeam} preChartPruning=true pruneLexicalCells=true executor=executor rules=ruleComp,ruleApp,punctuation,coordination,topicAdverbial,barePlural,bareNoun,sentToA,npToA,pluralToA,massToA,dateStamp,bareNE,neStamp,neStampCoord,keywordS,singleKeyword,keywordC1,shifts lex=lexRule breakTies=true
# Factor graph assignment factory
type=fg.assignment.factory id=assignmentFactory
#  Factor graph feature sets
type=factor.feat.relpref id=factorFeatRelPref
type=factor.feat.unary id=factorFeatUnary
type=factor.feat.lex id=factorFeatLex
type=factor.feat.closure id=factorFeatClosure
# Joint parser
type=parser.amr id=parser baseParser=baseParser assignmentFactory=assignmentFactory graphFeatures=factorFeatRelPref,factorFeatLex,factorFeatUnary,factorFeatClosure infer=BEAM beam=100
