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
        var solver = new Solver(main, preprocess);

        try {
            result = solver.getResult();
            if (result == null) {
                return super.analyze();
            }
            dump(result);
            return result;
        } catch (Exception e) {
            return super.analyze();
        }

        // TODO
        // You need to use `preprocess` like in PointerAnalysisTrivial
        // when you enter one method to collect infomation given by
        // Benchmark.alloc(id) and Benchmark.test(id, var)
        //
        // As for when and how you enter one method,
        // it's your analysis assignment to accomplish

        //return super.analyze();
        // return super.analyze();
    }


    public static boolean exceedsTimeLimit() {
        return new Date().getTime() - startTime.getTime() > 1000 * 60 * 0.95;
    }

}
