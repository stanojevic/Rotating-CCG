package edin.supertagger;

import edin.nn.DynetSetup$;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.immutable.List$;
import scala.collection.JavaConverters$;

import java.util.ArrayList;
import java.util.List;

public class Interactive {

    private Ensamble tagger = null ;

    public Interactive(
            List<String> modelDirs,
            int dynet_mem
    ) {
        scala.collection.immutable.List<Object> e = List$.MODULE$.empty() ;
        DynetSetup$.MODULE$.init_dynet(dynet_mem+"M", 1);

        tagger = new Ensamble(convertStrLst(modelDirs));
    }

    public List<List<List<String>>> bestK(List<String> words, List<String> aux_tags, int k){
        scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, Object>>> rs = tagger.predictKBestTagSequenceWithScores(convertStrLst(words), convertStrLst(aux_tags), k);
        List<List<List<String>>> res = new ArrayList<>();

        Iterator<scala.collection.immutable.List<Tuple2<String, Object>>> it = rs.iterator();

        while(!it.isEmpty()){
            scala.collection.immutable.List<Tuple2<String, Object>> r = it.next();
            List<List<String>> nbest = new ArrayList<>();

            Iterator<Tuple2<String, Object>> it2 = r.iterator();
            while(!it2.isEmpty()){
                Tuple2<String, Object> tuple2 = it2.next();
                List<String> entry = new ArrayList<>(2);
                entry.add(tuple2._1);
                entry.add(tuple2._2.toString());
                nbest.add(entry);
            }

            res.add(nbest);
        }

        return res ;
    }

    private scala.collection.immutable.List<String> convertStrLst(List<String> l){
        return JavaConverters$.MODULE$.asScalaBuffer(l).toList();
    }

    private scala.collection.immutable.List<Object> convert(List<Object> l){
        return JavaConverters$.MODULE$.asScalaBuffer(l).toList();
    }

    private List<Object> convertBack(scala.collection.immutable.List<Object> l){
        return JavaConverters$.MODULE$.bufferAsJavaList(l.toBuffer());
    }

}

