# Cornell AMR Semantic Parser

[TOC]

## Quick Start

You can get started and parse a few simple sentences in under five minutes using our AMI image (`ami-bdc8d8d7`). Launch an EC2 instance (recommended `r3.2xlarge` at least) with our AMI image. The code in the image is fully complied and ready to run. The `~/amr/do.sh` command will quickly parse a few example sentences. The AMI is based on Ubuntu and the user name is `ubuntu`. The code repository is available [here](http://yoavartzi.com/amr).

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
