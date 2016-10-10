# Data folds
## Proxy data (including seed)
include=../data.proxy.inc
## Training data folds (LDC data only)
[XX=0-4] type=data.single id=bolt-train-foldXX-raw file=../../resources/data/amr-release-1.0-training-bolt.foldXX.lam tokenizer=tokenizer
[XX=0-4] type=data.amr.labeled id=bolt-train-foldXX data=bolt-train-foldXX-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-bolt.foldXX.lam.cache
[XX=0-4] type=data.single id=dfa-train-foldXX-raw file=../../resources/data/amr-release-1.0-training-dfa.foldXX.lam tokenizer=tokenizer
[XX=0-4] type=data.amr.labeled id=dfa-train-foldXX data=dfa-train-foldXX-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-dfa.foldXX.lam.cache
[XX=0-4] type=data.single id=mt09sdl-train-foldXX-raw file=../../resources/data/amr-release-1.0-training-mt09sdl.foldXX.lam tokenizer=tokenizer
[XX=0-4] type=data.amr.labeled id=mt09sdl-train-foldXX data=mt09sdl-train-foldXX-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-mt09sdl.foldXX.lam.cache
[XX=0-4] type=data.single id=proxy-train-foldXX-raw file=../../resources/data/amr-release-1.0-training-proxy.foldXX.lam tokenizer=tokenizer
[XX=0-4] type=data.amr.labeled id=proxy-train-foldXX data=proxy-train-foldXX-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-proxy.foldXX.lam.cache
[XX=0-4] type=data.single id=xinhua-train-foldXX-raw file=../../resources/data/amr-release-1.0-training-xinhua.foldXX.lam tokenizer=tokenizer
[XX=0-4] type=data.amr.labeled id=xinhua-train-foldXX data=xinhua-train-foldXX-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-xinhua.foldXX.lam.cache
## Training data
type=data.single id=bolt-train-all-raw file=../../resources/data/amr-release-1.0-training-bolt.lam tokenizer=tokenizer
type=data.amr.labeled id=bolt-train data=bolt-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-bolt.lam.cache
type=data.single id=dfa-train-all-raw file=../../resources/data/amr-release-1.0-training-dfa.lam tokenizer=tokenizer
type=data.amr.labeled id=dfa-train data=dfa-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-dfa.lam.cache
type=data.single id=mt09sdl-train-all-raw file=../../resources/data/amr-release-1.0-training-mt09sdl.lam tokenizer=tokenizer
type=data.amr.labeled id=mt09sdl-train data=mt09sdl-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-mt09sdl.lam.cache
type=data.single id=xinhua-train-all-raw file=../../resources/data/amr-release-1.0-training-xinhua.lam tokenizer=tokenizer
type=data.amr.labeled id=xinhua-train data=xinhua-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-training-xinhua.lam.cache
type=data.single id=amrbank-train-all-raw file=../../resources/data/amr-bank-struct-v1.4-training.lam tokenizer=tokenizer
type=data.amr.labeled id=amrbank-train data=amrbank-train-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-bank-struct-v1.4-training.lam.cache
type=data.composite id=ldc-train sets=bolt-train,dfa-train,mt09sdl-train,proxy-train,xinhua-train
## Development data
type=data.single id=bolt-dev-all-raw file=../../resources/data/amr-release-1.0-dev-bolt.lam tokenizer=tokenizer
type=data.amr.labeled id=bolt-dev data=bolt-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-dev-bolt.lam.cache
type=data.single id=dfa-dev-all-raw file=../../resources/data/amr-release-1.0-dev-dfa.lam tokenizer=tokenizer
type=data.amr.labeled id=dfa-dev data=dfa-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-dev-dfa.lam.cache
type=data.single id=consensus-dev-all-raw file=../../resources/data/amr-release-1.0-dev-consensus.lam tokenizer=tokenizer
type=data.amr.labeled id=consensus-dev data=consensus-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-dev-consensus.lam.cache
type=data.single id=xinhua-dev-all-raw file=../../resources/data/amr-release-1.0-dev-xinhua.lam tokenizer=tokenizer
type=data.amr.labeled id=xinhua-dev data=xinhua-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-dev-xinhua.lam.cache
type=data.single id=amrbank-dev-all-raw file=../../resources/data/amr-bank-struct-v1.4-dev.lam tokenizer=tokenizer
type=data.amr.labeled id=amrbank-dev data=amrbank-dev-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-bank-struct-v1.4-dev.lam.cache
type=data.composite id=ldc-dev sets=bolt-dev,dfa-dev,consensus-dev,proxy-dev,xinhua-dev
## Held-out evaluation set
type=data.single id=bolt-heldout-all-raw file=../../resources/data/amr-release-1.0-test-bolt.lam tokenizer=tokenizer
type=data.amr.labeled id=bolt-heldout data=bolt-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-test-bolt.lam.cache
type=data.single id=dfa-heldout-all-raw file=../../resources/data/amr-release-1.0-test-dfa.lam tokenizer=tokenizer
type=data.amr.labeled id=dfa-heldout data=dfa-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-test-dfa.lam.cache
type=data.single id=consensus-heldout-all-raw file=../../resources/data/amr-release-1.0-test-consensus.lam tokenizer=tokenizer
type=data.amr.labeled id=consensus-heldout data=consensus-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-test-consensus.lam.cache
type=data.single id=xinhua-heldout-all-raw file=../../resources/data/amr-release-1.0-test-xinhua.lam tokenizer=tokenizer
type=data.amr.labeled id=xinhua-heldout data=xinhua-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-release-1.0-test-xinhua.lam.cache
type=data.single id=amrbank-heldout-all-raw file=../../resources/data/amr-bank-struct-v1.4-test.lam tokenizer=tokenizer
type=data.amr.labeled id=amrbank-heldout data=amrbank-heldout-all-raw tagger=easyccg bankParser=easyccg cache=../../resources/data/amr-bank-struct-v1.4-test.lam.cache
type=data.composite id=ldc-heldout sets=bolt-heldout,dfa-heldout,consensus-heldout,proxy-heldout,xinhua-heldout
