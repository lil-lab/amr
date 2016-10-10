# Data folds
## Seed data
type=data.single.lex id=seed-raw-lex file=../../resources/data/seed.lamlex tokenizer=tokenizer origin=SEED
type=data.amr.labeled.lex id=seed-lex data=seed-raw-lex tagger=easyccg bankParser=easyccg cache=../../resources/data/seed.amrlex.cache
## Training data
type=data.single id=proxy-train-all-raw file=../../resources/data/amr-release-1.0-training-proxy.lam tokenizer=tokenizer
type=data.amr.labeled id=proxy-train data=proxy-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-proxy.lam.cache
## Development data
type=data.single id=proxy-dev-all-raw file=../../resources/data/amr-release-1.0-dev-proxy.lam tokenizer=tokenizer
type=data.amr.labeled id=proxy-dev data=proxy-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-dev-proxy.lam.cache
## Held-out evaluation set
type=data.single id=proxy-heldout-all-raw file=../../resources/data/amr-release-1.0-test-proxy.lam tokenizer=tokenizer
type=data.amr.labeled id=proxy-heldout data=proxy-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-test-proxy.lam.cache
