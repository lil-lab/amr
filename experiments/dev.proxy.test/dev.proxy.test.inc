## Base resources
include=../resources.proxy.inc
include=../model.load.inc
## Data
type=data.composite id=seed sets=seed-lex
type=data.composite id=train sets=proxy-train
type=data.composite id=test sets=proxy-dev
## Learning and testing resources
include=../init.inc
include=../test.inc
include=../learn.inc
