#!/bin/bash
SCRIPT_DIR=$(dirname $0)
SRC=en
TGT=sr
LP=${SRC}-${TGT}
CORP_NAME=corpus_SETIMES2_$LP

get_seeded_random()
{
  seed="$1"
  openssl enc -aes-256-ctr -pass pass:"$seed" -nosalt \
    </dev/zero 2>/dev/null
}

mkdir $CORP_NAME
cd $CORP_NAME
wget http://opus.nlpl.eu/download.php?f=SETIMES2/${LP}.txt.zip -O ${LP}.txt.zip
unzip ${LP}.txt.zip
rm ${LP}.txt.zip

echo randomizing
L=sr ; paste SETIMES2.${LP}.${L} SETIMES2.${LP}.en | shuf --random-source=<(get_seeded_random 42) > all_tmp
cut all_tmp -f 1 > all_tmp.${L}
cut all_tmp -f 2 > all_tmp.en

D=test
echo $D
mkdir $D
L=sr ; head -2000 all_tmp.${L} > ${D}/${L}.sents
L=en ; head -2000 all_tmp.${L} > ${D}/${L}.sents

D=dev
echo $D
mkdir $D
L=sr ; head -4000 all_tmp.${L} | tail -2000 > ${D}/${L}.sents
L=en ; head -4000 all_tmp.${L} | tail -2000 > ${D}/${L}.sents

D=train
echo $D
mkdir $D
L=sr ; tail -n +4000 all_tmp.${L} > ${D}/${L}.sents
L=en ; tail -n +4000 all_tmp.${L} > ${D}/${L}.sents

rm all_tmp*
rm SETIMES2.*

wc -l */*

cd -

