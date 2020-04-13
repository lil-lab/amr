# Cornell AMR Semantic Parser

## Requirements

Java 8.

## Preparing the Repository

- Get all required resources: `./getres.sh` (form the root of the repository)
- Compile: `ant dist`

## Pre-trained Models

A pre-trained model is available to download [here](https://bitbucket.org/yoavartzi/amr-resources/downloads/amr.sp). 

## Parsing

Given a file `sentences.txt`, which contains a sentence on each line, and a model file `amr.sp`, both located in the root of the repository:

```
java -Xmx8g -jar dist/amr-1.0.jar parse rootDir=`pwd` modelFile=`pwd`/amr.sp sentences=`pwd`/sentences.txt
```

The output files will be in `experiments/parse/logs`. To see the full set of options (including increasing the logging level), run:

`java -jar dist/amr-1.0.jar`

## Preparing the data (required only for training and testing)

To re-create our experiments, obtain the AMR Bank release 1.0 ([LDC2014T12](https://catalog.ldc.upenn.edu/LDC2014T12)) form LDC. Extract the corpus to the directory `corpus/amr_anno_1.0`. 

Then run the following:

- Compile the code: `ant dist`
- Prepare the environment: `utils/config.sh`
- Prepare the data: `utils/prepdata-ldc.sh` 

## Attribution

```
@InProceedings{artzi-lee-zettlemoyer:2015:EMNLP,
  author    = {Artzi, Yoav  and  Lee, Kenton  and  Zettlemoyer, Luke},
  title     = {Broad-coverage CCG Semantic Parsing with AMR},
  booktitle = {Proceedings of the 2015 Conference on Empirical Methods in Natural Language Processing},
  month     = {September},
  year      = {2015},
  address   = {Lisbon, Portugal},
  publisher = {Association for Computational Linguistics},
  pages     = {1699--1710},
  url       = {http://aclweb.org/anthology/D15-1198}
}
```
