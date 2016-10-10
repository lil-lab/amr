## Base resources
include=../resources.proxy.inc
include=../model.inc
## Data
type=data.composite id=seed sets=seed-lex
type=data.composite id=train sets=seed-lex
type=data.composite id=test sets=seed-lex
## Learning and testing resources
include=../init.inc
include=../test.inc
include=../learn.inc
