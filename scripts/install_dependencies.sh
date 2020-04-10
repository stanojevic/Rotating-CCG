#!/bin/bash

# PREREQUISITES
# - g++ with c++11 support (gcc4.8.1+) and pthreads
# - cmake
# - JDK 8 (Scala requires JDK >=8 but DyNet at the moment has problem with JDK >=9) you MUST set JAVA_HOME variable
# - git
# - mercurial
# - python3 and python3-dev with numpy

# OPTIONAL
# - Intel MKL      -- speeds up computation on Intel  CPU (you would need to modify relevant variables bellow)
# - CUDA and cuDNN -- speeds up computation on NVidia GPU (you would need to modify relevant variables bellow)
# - pip3 install allennlp         -- if you want to use ELMo embeddings
# - pip3 install tensorflow       -- if you want to use BERT embeddings
# - pip2 install jnius            -- if you want to use supertagger from python2

START_DIR=$PWD

SCALA_VER=2.12.8
CORES=4   # Modify this variable to speed up installation of dependencies on multi-core machine

realpath() {
  [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}
if [[ $(basename $(dirname $(realpath "$0"))) == "scripts" ]]; then
  PROJECT_DIR=$(dirname $(dirname $(realpath $0)))
elif [[ -e "$PWD/scripts" ]]; then
  PROJECT_DIR=$PWD
else
  echo you are probably running this script from a wrong directory
  exit 1
fi

echo PROJECT DIR IS ${PROJECT_DIR}

DEPENDENCIES_DIR=${PROJECT_DIR}/dependencies
DYNET_COMMIT=d65bd5e0f921087f165a44b18c1f65369c9f517d
LIB_DIR=${PROJECT_DIR}/lib

if [[ -e "${DEPENDENCIES_DIR}" ]] ; then
  echo please delete the dependencies dir ${DEPENDENCIES_DIR} before installing new one with this script
  if [[ -e "${LIB_DIR}" ]] ; then
    echo you should probably also delete ${LIB_DIR}
  fi
  if [[ -e "${PROJECT_DIR}/target" ]] ; then
    echo you should probably also delete ${PROJECT_DIR}/target
  fi
  exit 1
fi

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

SBT_VERSION=1.2.8
SBT_DIR=${DEPENDENCIES_DIR}/sbt
rm -rf ${SBT_DIR}
wget https://piccolo.link/sbt-${SBT_VERSION}.tgz || { echo 'SBT download failed' ; exit 1; }
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

##########################    installing Jep    #########################
PYTHON_VER=3
JEP_DIR=${DEPENDENCIES_DIR}/jep
mkdir -p ${LIB_DIR}
rm -rf jep_tmp_gitclone jep
git clone https://github.com/ninia/jep.git jep_tmp_gitclone
cd jep_tmp_gitclone
# git checkout 05ec104d7aa77e1cbd80750372b8abc8dbc6f3c4 # version 3.6.4  # works with python2
# git checkout 7fc93a6319de8503d8a50e75afdeb30ad19e6fac # version 3.7.1
git checkout b94909e4b02f18e45f4cccdd73cea19caad0dfe9 # version 3.8
python${PYTHON_VER} setup.py install  --prefix=temporary || { echo 'Jep install failed' ; exit 1; }
mv temporary/*/*/*/*/*.jar ${LIB_DIR}
mv temporary/lib*/python*/site-packages/jep ${JEP_DIR}
ln -s ${JEP_DIR}/jep.*.so      ${LIB_DIR}/libjep.so
if [[ -e "${JEP_DIR}/libjep.jnilib" ]] ; then
    ln -s ${JEP_DIR}/libjep.jnilib ${LIB_DIR}/libjep.jnilib
fi
cd ..
rm -rf jep_tmp_gitclone

##########################    installing DyNet    #########################

git clone https://github.com/clab/dynet.git           || { echo 'DyNet install failed' ; exit 1; }
hg clone https://bitbucket.org/eigen/eigen -r b2e267d || { echo 'DyNet install failed' ; exit 1; }
cd dynet
git checkout ${DYNET_COMMIT}
rm -rf contrib/swig/src/test
perl -pi -e 's/~ComputationGraph\(\);/$&\n  void set_immediate_compute(bool b);/' contrib/swig/dynet_swig.i
perl -pi -e 's/~ComputationGraph\(\);/$&\n  void set_check_validity(bool b);/'    contrib/swig/dynet_swig.i
perl -pi -e 's/private.dynet. //' contrib/swig/src/main/scala/edu/cmu/dynet/ComputationGraph.scala
mkdir -p build
cd build
cmake .. -DEIGEN3_INCLUDE_DIR=${DEPENDENCIES_DIR}/eigen -DENABLE_SWIG=ON -DSCALA_VERSION=${SCALA_VER} ${CUDA} ${MKL} || { echo 'DyNet install failed' ; exit 1; }

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

# no optimization
# ${SBT_DIR}/bin/sbt clean assembly || { echo 'sbt assembly failed' ; exit 1; }
# medium optimization
SCALAXY_STREAMS_OPTIMIZE=0 OPTIMIZED=true ${SBT_DIR}/bin/sbt clean assembly || { echo 'sbt assembly failed' ; exit 1; }
# crazy optimization -- this is unsafe and some functionality might not work
# SCALAXY_STREAMS_OPTIMIZE=1 OPTIMIZED=true ${SBT_DIR}/bin/sbt clean assembly || { echo 'sbt assembly failed' ; exit 1; }

cd ${START_DIR}

