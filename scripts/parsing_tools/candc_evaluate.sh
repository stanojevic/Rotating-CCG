#!/bin/bash

if [ $# -ne 3 ]; then
    echo usage: $0 GOLD_AUTO GOLD_PARG PRED_AUTO
    exit 1
fi

[ ! -z "${CANDC}" ] || { echo 'finding CandC failed' ; exit 1; }

GOLD_AUTO=$1
GOLD_PARG=$2
PRED_AUTO=$3

SCRIPTS=${CANDC}/src/scripts/ccg
CATS=${CANDC}/src/data/ccg/cats
MARKEDUP=${CANDC}/src/data/ccg/cats/markedup


##### NOW FOR THE ALL SENTENCES ######
echo ; echo ; echo "***************************" NOW FOR THE ALL SENTENCES "***************************" ; echo ; echo ;

cp ${GOLD_AUTO} tmp.unfiltered.gold.auto
cp ${GOLD_PARG} tmp.unfiltered.gold.parg
cp ${PRED_AUTO} tmp.unfiltered.pred.auto

${SCRIPTS}/convert_auto tmp.unfiltered.gold.auto | sed -f ${SCRIPTS}/convert_brackets > tmp.gold.pipe
${SCRIPTS}/extract_sequences -s tmp.gold.pipe > tmp.gold.stagged

${SCRIPTS}/parg2ccgbank_deps tmp.unfiltered.gold.parg > tmp.gold.deps

sed "s/GLUE\( . 2> (<.\) \([^ ]*\)/\2\1 \2/g" ${PRED_AUTO} > tmp.pred.auto
${SCRIPTS}/convert_auto tmp.pred.auto | sed -f ${SCRIPTS}/convert_brackets > tmp.pred.pipe
CATS=${CANDC}/src/data/ccg/cats
MARKEDUP=${CANDC}/src/data/ccg/cats/markedup
${CANDC}/bin/generate -j ${CATS} ${MARKEDUP} tmp.pred.pipe > tmp.pred.gen_deps
sed -i -e 's/^$/<c>\n/' tmp.pred.gen_deps

${SCRIPTS}/evaluate2 tmp.gold.stagged tmp.gold.deps tmp.pred.gen_deps


##### NOW FOR THE NON-GLUED SENTENCES ONLY ######
echo ; echo ; echo  "***************************" NOW FOR THE NON-GLUED SENTENCES ONLY  "***************************" ; echo ; echo ;

cat ${GOLD_AUTO} | grep -v "^ID" > tmp.unfiltered.gold.auto
cat ${GOLD_PARG} | tr "\n" "\v" | sed "s/<\\\\s>\v/<\\\\s>\n/g" > tmp.unfiltered.gold.parg
cat ${PRED_AUTO} | grep -v "^ID" > tmp.unfiltered.pred.auto

paste tmp.unfiltered.gold.auto tmp.unfiltered.gold.parg tmp.unfiltered.pred.auto | grep -v GLUE > tmp.unfiltered.pasted

cat tmp.unfiltered.pasted | sed "s/^\([^\t]*\)\t\(.*\)\t\([^\t]*\)\$/\1/" > tmp.unfiltered.gold.auto
cat tmp.unfiltered.pasted | sed "s/^\([^\t]*\)\t\(.*\)\t\([^\t]*\)\$/\2/" | sed "s/\v/\n/g" > tmp.unfiltered.gold.parg
cat tmp.unfiltered.pasted | sed "s/^\([^\t]*\)\t\(.*\)\t\([^\t]*\)\$/\3/" > tmp.unfiltered.pred.auto

${SCRIPTS}/convert_auto tmp.unfiltered.gold.auto | sed -f ${SCRIPTS}/convert_brackets > tmp.gold.pipe
${SCRIPTS}/extract_sequences -s tmp.gold.pipe > tmp.gold.stagged

${SCRIPTS}/parg2ccgbank_deps tmp.unfiltered.gold.parg > tmp.gold.deps

sed "s/GLUE\( . 2> (<.\) \([^ ]*\)/\2\1 \2/g" tmp.unfiltered.pred.auto > tmp.pred.auto
${SCRIPTS}/convert_auto tmp.pred.auto | sed -f ${SCRIPTS}/convert_brackets > tmp.pred.pipe
CATS=${CANDC}/src/data/ccg/cats
MARKEDUP=${CANDC}/src/data/ccg/cats/markedup
${CANDC}/bin/generate -j ${CATS} ${MARKEDUP} tmp.pred.pipe > tmp.pred.gen_deps
sed -i -e 's/^$/<c>\n/' tmp.pred.gen_deps

${SCRIPTS}/evaluate2 tmp.gold.stagged tmp.gold.deps tmp.pred.gen_deps

##### NOW CLEANUP ######

rm -f tmp.gold.* tmp.pred.* tmp.unfiltered.* errors.log