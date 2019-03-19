#!/bin/bash
SCRIPT_DIR=$(dirname $0)
CORP_NAME=$1
THREADS=1

for X in $CORP_NAME/*/*.sents ; do
    L=$(basename $X | sed "s/\..*//")
    perl $SCRIPT_DIR/tokenizer.perl -penn -threads $THREADS -l $L < $X | perl -ne 'print lc' > ${X}.tok
    mv ${X}.tok $X
done
