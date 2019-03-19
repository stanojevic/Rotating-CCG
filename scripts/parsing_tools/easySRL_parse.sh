#!/bin/bash

EASYSRL_JAR=~/bin/easy-srl/EasySRL-master/easysrl.jar
EASYSRL_MODEL=~/bin/easy-srl/model

IN_FILE=$1
OUT_FILE=$(echo $IN_FILE | sed "s/\.sents$//" | sed "s/$/.ccgbank/")

cat $IN_FILE | java -jar $EASYSRL_JAR --model $EASYSRL_MODEL --outputFormat ccgbank > $OUT_FILE 2> /dev/null

