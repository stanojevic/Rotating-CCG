#!/bin/bash

EASYSRL_JAR=~/bin/easy-srl/EasySRL-master/easysrl.jar
EASYSRL_MODEL=~/bin/easy-srl/model

IN_FILE=$1
OUT_FILE=$(echo $IN_FILE | sed "s/\.sents$//" | sed "s/$/.ccgtags/")

cat $IN_FILE | java -jar $EASYSRL_JAR --model $EASYSRL_MODEL --outputFormat supertags > ${OUT_FILE} 2> /dev/null
#cat ${OUT_FILE}.tmp | sed "s/[^ ]*|//g" > $OUT_FILE
#rm ${OUT_FILE}.tmp
chmod -w $OUT_FILE

