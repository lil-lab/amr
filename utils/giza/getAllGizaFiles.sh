#!/bin/bash -e
run=0
fold=0
language=en

# Compile GIZA++, if needed
cd giza-pp
make
cd -


lib=../../dist/amr-1.0.jar
types=../../resources/amr.types

# All training files.
input_files[0]=../../resources/data/amr-release-1.0-training-proxy.lam
#input_files[4]=../../resources/data/amr-bank-struct-v1.4-training.lam
#input_files[1]=../../resources/data/amr-release-1.0-training-bolt.lam
#input_files[2]=../../resources/data/amr-release-1.0-training-dfa.lam
#input_files[3]=../../resources/data/amr-release-1.0-training-mt09sdl.lam
#input_files[5]=../../resources/data/amr-release-1.0-training-xinhua.lam
#input_files[6]=../../resources/data/amr-release-1.0-training-ldc.lam

src=src
trg=trg
bitxt=bitxt
snt=snt
mkdir giza_outputs

for input_file in ${input_files[@]:0}
do 
	java -jar $lib gizaprep $types $input_file > input.tmp
    python getGizaInputs.py input.tmp $src $trg $bitxt
	rm input.tmp
    giza-pp/GIZA++-v2/snt2cooc.out $src $trg $bitxt > $snt
    giza-pp/GIZA++-v2/GIZA++ giza_config
    giza_out=$input_file.giza_probs
    python translate_ids_to_words.py $src $trg giza_outputs/giza_out.t1.5 $giza_out
    echo "output giza prob file at" $giza_out
    rm $src* $trg* $bitxt* $snt* giza_outputs/*
done
