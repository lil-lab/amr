#!/bin/bash -e
cd resources
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/alignments.mt.txt.tar.gz
tar xzf alignments.mt.txt.tar.gz
rm alignments.mt.txt.tar.gz
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/easyccg-model-rebank.tar.gz
tar xzf easyccg-model-rebank.tar.gz
rm easyccg-model-rebank.tar.gz
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/IllinoisNERData.tar.gz
tar xzf IllinoisNERData.tar.gz
rm IllinoisNERData.tar.gz
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/propbank.tar.gz
tar xzf propbank.tar.gz
rm propbank.tar.gz
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/stanford-models.tar.gz
tar xzf stanford-models.tar.gz
rm stanford-models.tar.gz
cd ..
wget https://bitbucket.org/yoavartzi/amr-resources/downloads/lib.tar.gz
tar xzf lib.tar.gz
rm lib.tar.gz
