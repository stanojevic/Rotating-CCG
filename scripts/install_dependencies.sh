#!/bin/bash

# PREREQUISITES
# - g++ with c++11 support (gcc4.8.1+) and pthreads
# - cmake
# - JDK 8 (Scala requires JDK >=8 but DyNet at the moment has problem with JDK >=9) you MUST set JAVA_HOME variable
# - git
# - python3 and python3-dev with numpy

# OPTIONAL
# - Intel MKL      -- speeds up computation on Intel  CPU (you would need to modify relevant variables bellow)
# - CUDA and cuDNN -- speeds up computation on NVidia GPU (you would need to modify relevant variables bellow)
# - pip3 install allennlp thrift  -- if you want to use ELMo embeddings
# - pip2 install jnius            -- if you want to use supertagger from python2

START_DIR=$PWD

SCALA_VER=2.12.6
CORES=4   # Modify this variable to speed up installation of dependencies on multi-core machine

realpath() {
  [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}
if [[ $(basename $(dirname $(realpath "$0"))) == "scripts" ]]; then
  PROJECT_DIR=$(dirname $(dirname $(realpath $0)))
else
  PROJECT_DIR=$PWD
fi

echo PROJECT DIR IS ${PROJECT_DIR}

DEPENDENCIES_DIR=${PROJECT_DIR}/dependencies
DYNET_COMMIT=51b528c7013c3efa42c7a7bc04959995700e7a77
LIB_DIR=${PROJECT_DIR}/lib

##########################    CUDA    #########################

CUDA="-DBACKEND=eigen" # no CUDA

# export CUDNN_ROOT="/opt/cuDNN-5.1_7.5" # this DyNet version can use only the older CUDNN
# export CUDA_TOOLKIT_ROOT_DIR="/opt/cuda-8.0.44"
# CUDA="-DBACKEND=cuda -DCUDA_TOOLKIT_ROOT_DIR=$CUDA_TOOLKIT_ROOT_DIR -DCUDNN_ROOT=$CUDNN_ROOT" # this is to use GPU

##########################  Intel MKL #########################

# MKL_ROOT=`ls -1d $HOME/intel/compilers_and_libraries_20*.*/linux/mkl/ | head -1`   # this is to use MKL
# [ ! -z "$MKL_ROOT" ] || { echo 'finding Intel MKL failed' ; exit 1; }
# echo FOUND MKL AT $MKL_ROOT
# MKL="-DMKL=TRUE -DMKL_ROOT=$MKL_ROOT"
# export MKL_NUM_THREADS=$CORES


########################  installation ########################

rm -rf ${DEPENDENCIES_DIR} ${LIB_DIR} ${PROJECT_DIR}/target
mkdir -p ${DEPENDENCIES_DIR}
cd ${DEPENDENCIES_DIR}

##########################    installing sbt    #########################

SBT_VERSION=1.1.6
SBT_DIR=${DEPENDENCIES_DIR}/sbt
rm -rf ${SBT_DIR}
wget --no-check-certificate https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz || { echo 'SBT download failed' ; exit 1; }
tar xfvz sbt-${SBT_VERSION}.tgz
rm sbt-${SBT_VERSION}.tgz
mv sbt ${SBT_DIR}
export PATH=${SBT_DIR}/bin:$PATH

##########################    installing swig    #########################

SWIG_DIR=${DEPENDENCIES_DIR}/swig
# svn checkout https://svn.code.sf.net/p/swig/code/trunk swig-3.0.12
wget --no-check-certificate 'https://downloads.sourceforge.net/project/swig/swig/swig-3.0.12/swig-3.0.12.tar.gz?r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fswig%2Ffiles%2Flatest%2Fdownload&ts=1529975837' -O swig-3.0.12.tar.gz
tar xfvz swig-3.0.12.tar.gz
rm swig-3.0.12.tar.gz
cd swig-3.0.12
./configure --prefix=${SWIG_DIR} || { echo 'SWIG install failed' ; exit 1; }
make -j ${CORES}                 || { echo 'SWIG install failed' ; exit 1; }
make install                     || { echo 'SWIG install failed' ; exit 1; }
cd ..
rm -rf swig-3.0.12
export PATH=${SWIG_DIR}/bin:$PATH

##########################    installing DyNet    #########################

git clone https://github.com/clab/dynet.git           || { echo 'DyNet install failed' ; exit 1; }
# getting eigen
mkdir eigen
cd eigen
wget https://github.com/clab/dynet/releases/download/2.1/eigen-b2e267dc99d4.zip || { echo 'Eigen download failed' ; exit 1; }
unzip eigen-b2e267dc99d4.zip
cd ..

cd dynet
git checkout ${DYNET_COMMIT}
rm -rf contrib/swig/src/test
mkdir -p build
cd build
cmake .. -DEIGEN3_INCLUDE_DIR=${DEPENDENCIES_DIR}/eigen -DENABLE_SWIG=ON  -DSCALA_VERSION=${SCALA_VER} ${CUDA} ${MKL} || { echo 'DyNet install failed' ; exit 1; }

make -j ${CORES} || { echo 'DyNet install failed' ; exit 1; }


mkdir -p ${LIB_DIR}
cd ${LIB_DIR}

rm -f dynet*.jar


# mv ${DEPENDENCIES_DIR}/dynet/build/contrib/swig/dynet_swigJNI.jar . # java lib
mv ${DEPENDENCIES_DIR}/dynet/build/contrib/swig/dynet_swigJNI_dylib.jar . # native lib
mv ${DEPENDENCIES_DIR}/dynet/build/contrib/swig/dynet_swigJNI_scala*.jar . # scala lib

#########################################################################

rm -rf ${DEPENDENCIES_DIR}/swig
rm -rf ${DEPENDENCIES_DIR}/eigen

cd ${PROJECT_DIR}

${SBT_DIR}/bin/sbt assembly || { echo 'sbt assembly failed' ; exit 1; }

cd ${START_DIR}

