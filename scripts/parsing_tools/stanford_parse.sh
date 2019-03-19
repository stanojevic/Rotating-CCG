#!/bin/bash

CORE_NLP_DIR=~/bin/stanford-corenlp-full-2018-01-31

IN_FILE=$1

java -mx50g -cp "$CORE_NLP_DIR/*" edu.stanford.nlp.pipeline.StanfordCoreNLP \
     -ssplit.eolonly true \
     -tokenize.whitespace true \
     -escaper edu.stanford.nlp.process.PTBEscapingProcessor \
     -annotators tokenize,ssplit,pos,depparse \
     -outputFormat conll   \
     -outputDirectory $(dirname $IN_FILE) \
     -file $IN_FILE 2> ${IN_FILE}.parse.log

NEW_NAME=$(echo $IN_FILE | sed "s/\.sents$/.conll/")
mv ${IN_FILE}.conll ${NEW_NAME}
