Rotating CCG Parser
=========

This is the implementation of incremental CCG parser described in "CCG Parsing Algorithm with Incremental Tree Rotation" by Stanojević and Steedman NAACL 2019.

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

    unzip model.zip
    
And finally, use it to parse file input.sentences and store trees in new file output.trees:

    ./scripts/run.sh edin.ccg.MainParse \
               --model_dirs model \
               --beam-parsing 1 \
               --input_file input.sentences \
               --output_file output.trees

On the first run it may take longer to start because it will download be downloading ELMo models, but afterwards it should be relatively fast. If you want a really fast (but less accurate) CCG parser you should go for EasyCCG instead.

Output
------
Since this is a transition based parser constrained by a grammar, it may enter into deadends during the greedy search for the correct parse. In that case it connects all the elements on the stack under right branching binary nodes labelled GLUE.
C&C `generate` evaluation program will not be able to handle that kind of derivations. To do evaluation with `generate` first a small transformation is needed to replace GLUE nodes with some type-changing rules. Script located in `./scripts/parsing_tools/candc_evaluate.sh` does that.
If you want to do evaluation with Hockenmaier style dependencies then GLUE nodes present no problem and you can run `./scripts/run.sh edin.ccg.MainEvaluate` with appropriate parameters instead.

References
-------------

    @InProceedings{NAACL2019:CCG,
      author    = "Milo\v{s} Stanojevi\'{c} and Mark Steedman",
      title     = "CCG Parsing Algorithm with Online Tree Rotation",
      booktitle = "Proceedings of the 2019 Conference of the North American Chapter of the Association for Computational Linguistics, Volume 1 (Long Papers)",
      year      = "2019",
      publisher = "Association for Computational Linguistics",
      location  = "Minneapolis, Minnesota"
    }
