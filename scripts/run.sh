#!/bin/bash


realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

PROJECT_DIR=$(dirname $(dirname $(realpath "$0")))

# how much memory to dedicate to JVM
JVM_MEM=6G

# use 3 cores if DyNet is compiled with Intel MKL support
export MKL_NUM_THREADS=3

#relevant exporting variables for .so files LD_LIBRARY_PATH, DYLD_LIBRARY_PATH, LD_PRELOAD, DYLD_INSERT_LIBRARIES

# run the thing
java -Xmx${JVM_MEM} -cp ${PROJECT_DIR}/target/scala-2.12/*assembly*.jar $@
