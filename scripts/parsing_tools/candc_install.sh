#!/bin/bash

wget http://www.cl.cam.ac.uk/~sc609/resources/candc-downloads/candc-1.00.tgz
tar xfvz candc-1.00.tgz
rm candc-1.00.tgz
cd candc-1.00

sed -i '1i#include <string.h>' src/include/pool.h
sed -i '1i#include <string.h>' src/include/affix.h
sed -i '1i#include <string.h>' src/include/prob.h
sed -i '1i#include <string.h>' src/include/utils/aux_strings.h

# I don't know if the line bellow is correct; needed only for compiling with the newest gcc
sed -i "s/return hash == this->hash \&\& str == this->str/return hash == this->hash \&\& str == this->str.str()/" src/include/hashtable/word.h

mkdir -p bin

# make -f Makefile.unix all bin/generate
make -f Makefile.unix all bin/generate

cd src/scripts/ccg
wget https://raw.githubusercontent.com/mikelewis0/easyccg/master/training/eval_scripts/evaluate2
wget https://raw.githubusercontent.com/mikelewis0/easyccg/master/training/eval_scripts/get_deps_from_auto
wget https://raw.githubusercontent.com/mikelewis0/easyccg/master/training/eval_scripts/get_grs_from_auto
chmod +x evaluate2 get_deps_from_auto get_grs_from_auto
cd -

echo export CANDC=$PWD >> ~/.bashrc
echo export CANDC=$PWD >> ~/.zshrc

cd ..

echo RUN \"source ~/.bashrc\" TO REFRESH SHELL VARIABLES
