package pku;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.exception.ThrowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.misc.IRDumper;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.ir.IRPrinter;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.util.graph.Graph;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class PointerAnalysis extends PointerAnalysisTrivial
{
    public static final String ID = "pku-pta";

    public static Date startTime = new Date(); // 指针分析的开始时间

    private static final Logger logger = LogManager.getLogger(IRDumper.class);

    public PointerAnalysis(AnalysisConfig config) {
        super(config);
        logger.info(config.toDetailedString());
    }

    @Override
    public PointerAnalysisResult analyze() {
        var result = new PointerAnalysisResult();
        var preprocess = new PreprocessResult();
        var world = World.get();
        var main = world.getMainMethod();
        var jclass = main.getDeclaringClass();

        startTime = new Date();
        logger.info("Start Pointer Analysis at {}", startTime);
        logger.info("Main Method: {}", main);
        logger.info("Main Class: {}", jclass.getName());

        var methods = jclass.getDeclaredMethods();
        for (var method : methods) {
            var ir = method.getIR();
            IRPrinter.print(ir, System.out);
        }



        //var steensgaard = new Steensgaard(jclass, preprocess);
        //result = steensgaard.getResult();

        /*
        var newOptions = new HashMap<String, Object>();
        newOptions.put("exception", null);
        newOptions.put("dump", "cfg_dump");

        AnalysisConfig config = new AnalysisConfig(
                "pku.CFGBuilder",
                "pku.CFGBuilder",
                "cfg",
                null,
                new AnalysisOptions(newOptions)
        );
        logger.info(config.toDetailedString());
        cfgBuilder = new CFGBuilder(config);
        logger.info(config.toDetailedString());

        var andersonFlowAnalysis = new AndersonFlowAnalysis(main, preprocess, cfgBuilder, new ArrayList<>());
        result = andersonFlowAnalysis.getResult();
        */

        var anderson = new Anderson(main, jclass, preprocess);
        result = anderson.getResult();
        if (result == null) {
            return super.analyze();
        }
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


    public static boolean exceedsTimeLimit() {
        return new Date().getTime() - startTime.getTime() > 1000 * 60 * 0.95;
    }

}
