## Base resources
include=../resources.proxy.inc
include=../model.inc
## Data
type=filter.ref id=refFilter
type=data.filter id=seed data=seed-lex filter=refFilter
type=data.filter id=train data=proxy-train filter=refFilter
type=data.filter id=test data=proxy-dev filter=refFilter
## Learning and testing resources
include=../init.inc
include=../test.inc
include=../learn.inc
