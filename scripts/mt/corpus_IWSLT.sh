#!/bin/bash

SCRIPT_DIR=$(dirname $0)

ZIP_FILE=$1
SRC=$(basename $ZIP_FILE | sed "s/\(..\)-\(..\)\.tgz/\1/")
TGT=$(basename $ZIP_FILE | sed "s/\(..\)-\(..\)\.tgz/\2/")
LP=$SRC-$TGT
CORP_NAME=corpus_IWSLT_$LP

tar -xvzf $ZIP_FILE
mkdir $CORP_NAME

D=$CORP_NAME/train
echo $D
mkdir $D
cat ${LP}/train.tags.*.${SRC} | sed "s/\\s*$//" | sed "s/^\\s*//" | grep -v "^<.*>$" > ${D}/${SRC}.sents
cat ${LP}/train.tags.*.${TGT} | sed "s/\\s*$//" | sed "s/^\\s*//" | grep -v "^<.*>$" > ${D}/${TGT}.sents

D=$CORP_NAME/dev
IWSLT_D=dev
echo $D
mkdir $D
cat $LP/IWSLT17.TED.${IWSLT_D}2010.${LP}.${SRC}.xml | grep "<seg id=" | $SCRIPT_DIR/strip_sgml.py > $D/${SRC}.sents
cat $LP/IWSLT17.TED.${IWSLT_D}2010.${LP}.${TGT}.xml | grep "<seg id=" | $SCRIPT_DIR/strip_sgml.py > $D/${TGT}.sents

D=$CORP_NAME/test
IWSLT_D=tst
echo $D
mkdir $D
cat $LP/IWSLT17.TED.${IWSLT_D}2010.${LP}.${SRC}.xml | grep "<seg id=" | $SCRIPT_DIR/strip_sgml.py > $D/${SRC}.sents
cat $LP/IWSLT17.TED.${IWSLT_D}2010.${LP}.${TGT}.xml | grep "<seg id=" | $SCRIPT_DIR/strip_sgml.py > $D/${TGT}.sents

wc -l $CORP_NAME/*/*

rm -rf $LP
