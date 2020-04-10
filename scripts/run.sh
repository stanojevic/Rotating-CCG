#!/bin/bash


realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

PROJECT_DIR=$(dirname $(dirname $(realpath "$0")))
LIB_DIR=${PROJECT_DIR}/lib

# how much memory to dedicate to JVM
JVM_MEM=6G

# use 3 cores if DyNet is compiled with Intel MKL support
export MKL_NUM_THREADS=3

# setting up dynamic .so libraries
# JAVA_SO=${JAVA_HOME}/jre/lib/amd64/server/libjvm.so
# export LD_PRELOAD=${ALL_SO}:${LD_PRELOAD}
# export LD_LIBRARY_PATH=${ALL_SO}:${LD_LIBRARY_PATH}
# export DYLD_LIBRARY_PATH=${ALL_SO}:${DYLD_LIBRARY_PATH}
# export DYLD_INSERT_LIBRARIES=${ALL_SO}:${DYLD_INSERT_LIBRARIES}

# DEBUGGING="-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y" \

# run the thing
exec java \
  ${DEBUGGING} \
  -Djava.library.path=${LIB_DIR} \
  -Xmx${JVM_MEM} \
  -cp ${PROJECT_DIR}/target/scala-2.12/*assembly*.jar \
  $@

