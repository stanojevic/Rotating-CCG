#!/bin/bash

INSTALL_DIR=$PWD

CORES=6

wget --no-check-certificate https://www.python.org/ftp/python/3.6.4/Python-3.6.4.tgz

tar xfvz Python-3.6.4.tgz

rm *.tgz

cd Python*

./configure --prefix=${INSTALL_DIR} --with-threads --enable-shared
make -j ${CORES}
make install altinstall

cd ..

rm -rf Python*

echo export LD_LIBRARY_PATH=${INSTALL_DIR}/lib:\$LD_LIBRARY_PATH >> ~/.zshrc
echo export LD_LIBRARY_PATH=${INSTALL_DIR}/lib:\$LD_LIBRARY_PATH >> ~/.bashrc

echo export PATH=${INSTALL_DIR}/bin:\$PATH >> ~/.zshrc
echo export PATH=${INSTALL_DIR}/bin:\$PATH >> ~/.bashrc

source ${HOME}/.bashrc

python3 -m ensurepip

pip3 install numpy
pip3 install cython

pip3 install allennlp
pip3 install thrift

echo DOWNLOADING ELMo EMBEDDINGS
echo IF IT WORKS IT WILL PRINT SOME NUMBERS ON THE SCREEN IN A MINUTE
python3 -c "from allennlp.modules.elmo import Elmo ; options_file = 'https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_options.json' ; weight_file = 'https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_weights.hdf5' ; elmo = Elmo(options_file, weight_file, 2, dropout=0);sentences = [['First', 'sentence', '.'], ['Another', '.']] ; character_ids = batch_to_ids(sentences) ; embeddings = elmo(character_ids) ; print(embeddings)"
echo DONE
