#!/bin/bash -e
PATH=/usr/local/Cellar/icu4c/55.1/bin:$PATH
export JAMR_HOME=utils/jamr
. utils/jamr/scripts/config.sh
for i in corpus/amr_anno_1.0/data/split/*/*.txt; do python utils/pysrc/strip_hyphens.py -o corpus/`basename ${i%.*}`.nohyp $i; done
for i in corpus/*.nohyp; do echo $i; utils/jamr/scripts/ALIGN.sh < $i > corpus/`basename ${i%.*}`.nomt; done
rm corpus/*.nohyp
for i in corpus/*.nomt; do
  python utils/pysrc/augment_jamr.py resources/alignments.mt.txt $i corpus/`basename ${i%.*}`.jamr;
done
rm corpus/*.nomt
for i in corpus/*.jamr; do
	python utils/pysrc/txt2amr.py -o corpus/`basename ${i%.*}`.amr $i;
	rm $i;
done
for i in corpus/*.amr; do
	echo $i;
	java -Xmx8g -jar dist/amr-1.0.jar amr2lam resources/amr.types $i ${i%.*}.lam;
	rm $i;
done
# Create one big file for LDC data to create GIZA scores
cat corpus/amr-release-1.0-training-bolt.lam corpus/amr-release-1.0-training-dfa.lam corpus/amr-release-1.0-training-mt09sdl.lam corpus/amr-release-1.0-training-proxy.lam corpus/amr-release-1.0-training-xinhua.lam > corpus/amr-release-1.0-training-ldc.lam
# Copy data to resources
cp corpus/*.lam resources/data
# Re-compute GIZA scores
cd utils/giza
./getAllGizaFiles.sh
cd -
# Split the training data into folds
java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.SplitFolds resources/amr.types resources/data corpus/amr-release-1.0-training-bolt.lam
java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.SplitFolds resources/amr.types resources/data corpus/amr-release-1.0-training-dfa.lam
java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.SplitFolds resources/amr.types resources/data corpus/amr-release-1.0-training-mt09sdl.lam
java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.SplitFolds resources/amr.types resources/data corpus/amr-release-1.0-training-proxy.lam
java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.SplitFolds resources/amr.types resources/data corpus/amr-release-1.0-training-xinhua.lam
# Refresh the seed data -- uncomment only if you wish to try and update the seed if the pre-processing changed -- delicate operation!
# java -Xmx8g -cp dist/amr-1.0.jar edu.uw.cs.lil.amr.util.dataprep.RefreshSeed -t resources/amr.types -s resources/data/seed resources/data/amr-release-1.0-training-proxy.lam resources/data/amr-release-1.0-dev-proxy.lam
