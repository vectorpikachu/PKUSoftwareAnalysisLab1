package pku;

import pascal.taie.World;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IRPrinter;

public class PointerAnalysis extends PointerAnalysisTrivial
{
    public static final String ID = "pku-pta";

    public PointerAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public PointerAnalysisResult analyze() {
        var result = new PointerAnalysisResult();
        var preprocess = new PreprocessResult();
        var world = World.get();
        var main = world.getMainMethod();
        var jclass = main.getDeclaringClass();

        //var steensgaard = new Steensgaard(jclass, preprocess);
        //result = steensgaard.getResult();

        var anderson = new Anderson(jclass, preprocess);
        result = anderson.getResult();

        dump(result);

        // TODO
        // You need to use `preprocess` like in PointerAnalysisTrivial
        // when you enter one method to collect infomation given by
        // Benchmark.alloc(id) and Benchmark.test(id, var)
        //
        // As for when and how you enter one method,
        // it's your analysis assignment to accomplish

        //return super.analyze();
        return result;
    }




}
