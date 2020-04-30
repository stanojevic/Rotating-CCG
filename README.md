Rotating CCG Parser
=========

This is the implementation of the incremental CCG parsing model described in "Max-Margin Incremental CCG Parsing" by Stanojević and Steedman ACL 2020.

If you have any problems using it shout at:

Miloš Stanojević        \
m.stanojevic@ed.ac.uk   \
University of Edinburgh 

Installation
---------------

Before you can use this parser you need to make sure that some prerequisites are installed on your machine.

Basic requirements:
- a recent version of some c++ compiler with c++11 and pthreads support (for example gcc4.8.1+)
- cmake
- JDK 8 (Scala requires JDK >=8 but DyNet at the moment has problem with JDK >=9) you MUST set JAVA_HOME variable
- git client
- mercurial client

If you want to use the pretrained model that uses ELMo you will also need to make sure you have:
- python3
- pip3 install allennlp
- pip3 install thrift

If that is all in place, you can run the following command to install the rest of the dependencies:

     ./scripts/install_dependencies.sh

It will take some time before its finished installing all the other dependencies (Scala, SBT, SWIG, Eigen and DyNet) and store them in directories `dependencies` and `lib`.

Now you need to compile all the files by running the following command:

    ./dependencies/sbt/bin/sbt assembly
    
Unzip the pretrained model with:

    unzip models.zip

There are three models inside:
- discriminative locally-normalized incremental parser (the baseline)
- discriminative locally-normalized non-incremental parser
- BSO-LaSO-All -- unnormalized max-margin trained model that worked best in our incremental experiments
    
And finally, use it to parse file input.sentences and store trees in new file output.trees:

    ./scripts/run.sh edin.ccg.MainParse \
               --input_file input.sentences \
               --model_dirs $MODEL_DIR \
               --beam-type simple \
               --beam-mid-parsing $BEAM_SIZE \
               --beam-out-parsing $BEAM_SIZE \
               --output_file output.trees

On the first run it may take longer to start because it will be downloading ELMo models, but afterwards it should get faster depending on the beam size.

CRF-approximation
-----------------

The extended code for approximate CRF is available in `code_with_approx_crf.zip`.

Output
------
Since this is a transition based parser constrained by a grammar, it may enter into deadends during the greedy search for the correct parse. In that case it connects all the elements on the stack under right branching binary nodes labelled GLUE.
C&C `generate` evaluation program will not be able to handle that kind of derivations. To do evaluation with `generate` first a small transformation is needed to replace GLUE nodes with some type-changing rules. Script located in `./scripts/parsing_tools/candc_evaluate.sh` does that.
If you want to do evaluation with Hockenmaier style dependencies then GLUE nodes present no problem and you can run `./scripts/run.sh edin.ccg.MainEvaluate` with appropriate parameters instead.

References
-------------

    @inproceedings{ACL2020:CCG,
      title="{Max-Margin Incremental CCG Parsing}",
        booktitle = "Proceedings of the 58th Annual Meeting of the Association for Computational Linguistics (Volume 1: Long Papers)",
        author    = "Milo\v{s} Stanojevi\'{c} and Mark Steedman",
        year = "2020",
        address = "Seattle, Washington",
        publisher = "Association for Computational Linguistics",
    }
