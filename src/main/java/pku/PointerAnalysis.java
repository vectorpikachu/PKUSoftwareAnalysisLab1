package pku;

import pascal.taie.World;
import pascal.taie.analysis.ProgramAnalysis;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JMethod;

import java.util.HashMap;
import java.util.TreeSet;

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


        var points_to = new HashMap<Var, AbstractLocation>();
        var methods = jclass.getDeclaredMethods();
        for (JMethod method : methods) {
            if (method.isAbstract()) {
                continue;
            }
            var ir = method.getIR();
            preprocess.analysis(ir); // 首先获取需要的信息
            /*
             * 我们应该把 B b = new B(); 看成一种赋值.
             * b := temp$0; --> join(*b, *temp$0);这样.
             */
            System.out.println(preprocess.obj_ids);
            var stmts = ir.getStmts();
            for (var stmt : stmts) {
                if (stmt instanceof AssignStmt<?,?>) {
                    if (stmt instanceof New) {
                        System.out.println(stmt);
                        var newVar = ((New) stmt).getLValue();
                        AbstractLocation newLocation = getLocation(points_to, newVar);
                        newLocation.addNewStmt((New) stmt);
                        continue;
                    }
                    var lvalue = ((AssignStmt<?, ?>) stmt).getLValue();
                    var rvalue = ((AssignStmt<?, ?>) stmt).getRValue();
                    String lvalue_type = lvalue.getType().toString();
                    String rvalue_type = rvalue.getType().toString();
                    if (!lvalue_type.startsWith("benchmark.objects") || !rvalue_type.startsWith("benchmark.objects")) {
                        continue;
                    }
                    // 剩下的是形如 p := q 这样的语句
                    AbstractLocation lLocation = getLocation(points_to, (Var) lvalue);
                    AbstractLocation rLocation = getLocation(points_to, (Var) rvalue);
                    AbstractLocation.union(lLocation, rLocation);
                    System.out.println(lvalue + "=" + rvalue);
                }
            }
        }

        preprocess.test_pts.forEach((test_id, pt)->{
            AbstractLocation ptLocation = getLocation(points_to, pt);
            var ptResult = new TreeSet<Integer>();
            for (var stmt : ptLocation.realNewStmts) {
                ptResult.add(preprocess.obj_ids.get(stmt));
            }
            result.put(test_id, ptResult);
        });

        dump(result);

        // TODO
        // You need to use `preprocess` like in PointerAnalysisTrivial
        // when you enter one method to collect infomation given by
        // Benchmark.alloc(id) and Benchmark.test(id, var)
        //
        // As for when and how you enter one method,
        // it's your analysis assignment to accomplish

        // return super.analyze();
        return result;
    }


    public AbstractLocation getLocation(HashMap<Var, AbstractLocation> points_to, Var var) {
        if (!points_to.containsKey(var)) {
            points_to.put(var, new AbstractLocation());
        }
        return points_to.get(var).find();
    }

}
