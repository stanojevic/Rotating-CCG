#!/usr/bin/env bash

OUT_DIR=$1
MODEL_DIR=$2
CORES=3
SAMPLES_PER_CORE=33
SENTS_FILE="test.words"
ALPHA=1.0

for I in `seq 1 ${CORES}`; do
    CORE_DIR=${OUT_DIR}/core_${I}
    mkdir -p ${CORE_DIR}
    ./run.sh edin.ccg.MainSampleTrees \
               --sents_file ${SENTS_FILE} \
               --samples_dir ${CORE_DIR} \
               --disc_model_dirs ${MODEL_DIR} \
               --samples ${SAMPLES_PER_CORE} \
               --alpha ${ALPHA} \
               1> ${CORE_DIR}/log.std \
               2> ${CORE_DIR}/log.err &
done

wait

for I in `seq 1 ${CORES}`; do
    CORE_DIR=${OUT_DIR}/core_${I}
    for F in ${CORE_DIR}/* ; do
        SENT_ID=`basename $F`
        cat ${F} >> ${OUT_DIR}/${SENT_ID}
    done
done

# rm -rf ${OUT_DIR}/core_*
