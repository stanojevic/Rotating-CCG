
### to use this module you need to run the following installation commands
# sudo pip install cython
# sudo pip install jnius

JAVA_MEMORY_MB=109
DYNET_MEMORY_MB=309
PROJECT_LOCATION = "/home/milos/Projects/CCG-translator"

from glob import glob
import jnius_config
jnius_config.add_options('-Xrs', "-Xmx%dM"%JAVA_MEMORY_MB)
main_jar = glob(PROJECT_LOCATION+'/target/scala-*/*.jar')[0]
dep_jars = glob(PROJECT_LOCATION+'/lib/*.jar')
jnius_config.set_classpath('.', main_jar, *dep_jars)
from jnius import autoclass

class Supertagger:

    def _to_java_list(self, ls):
        ArrayList = autoclass("java.util.ArrayList")
        jl = ArrayList()
        for l in ls :
            jl.add(l)
        return jl

    def _to_python_list(self, ls):
        pl = []
        for i in range(ls.size()):
            x = ls.get(i)
            if type(x).__name__ == "java.util.ArrayList":
                pl.append(self._to_python_list(x))
            else:
                pl.append(x)
        return pl

    def __init__(self, model_dirs):
        modelClass = autoclass("edin.supertagger.Interactive")
        if(isinstance(model_dirs, basestring)):
            real_model_dirs = [model_dirs]
        else:
            real_model_dirs = model_dirs
        self.tagger = modelClass(self._to_java_list(real_model_dirs), DYNET_MEMORY_MB)

    def bestK(self, words, aux_tags, k):
        result = self.tagger.bestK(self._to_java_list(words),self._to_java_list(aux_tags), k)
        resultPy = self._to_python_list(result)
        for xs in resultPy:
            for x in xs:
                x[1] = float(x[1])
        return resultPy



if __name__ == "__main__":
    # tagger = Supertagger(model_dirs = [PROJECT_LOCATION+"/tmp/models/aux_tagging"])
    tagger = Supertagger(PROJECT_LOCATION+"/tmp/models/aux_tagging")
    from pprint import pprint
    pprint(tagger.bestK(["w2", "rerf"], ["asdf", "dlkajsd"], 5))

