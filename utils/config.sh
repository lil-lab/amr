#!/bin/bash -e
cd utils/giza/giza-pp
make
cd -
cd utils/jamr
./setup
. scripts/config.sh
./compile
