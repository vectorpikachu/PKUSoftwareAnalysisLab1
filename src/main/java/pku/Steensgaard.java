package pku;

import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

import java.util.HashMap;
import java.util.Random;
import java.util.TreeSet;

public class Steensgaard {
    public HashMap<Var, AbstractLocation> points_to;
    public JClass jclass;
    public PreprocessResult preprocess;

    public Steensgaard(JClass jclass, PreprocessResult preprocess) {
        this.jclass = jclass;
        points_to = new HashMap<>();
        this.preprocess = preprocess;
    }

    public AbstractLocation getLocation(Var var) {
        if (!points_to.containsKey(var)) {
            Random random = new Random();
            var category = random.nextInt(AbstractLocation.maxOutDegree);
            points_to.put(var, new AbstractLocation(category));
        }
        return points_to.get(var);
    }

    public PointerAnalysisResult getResult() {
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
            var stmts = ir.getStmts();
            for (var stmt : stmts) {
                if (stmt instanceof AssignStmt<?,?>) {
                    if (stmt instanceof New) {
                        var newVar = ((New) stmt).getLValue();
                        AbstractLocation newLocation = getLocation(newVar);
                        newLocation.addNewStmt((New) stmt);
                        continue;
                    }
                    var lvalue = ((AssignStmt<?, ?>) stmt).getLValue();
                    var rvalue = ((AssignStmt<?, ?>) stmt).getRValue();
                    String lvalue_type = lvalue.getType().toString();
                    String rvalue_type = rvalue.getType().toString();
                    if (!lvalue_type.startsWith("benchmark.objects")
                            || !rvalue_type.startsWith("benchmark.objects")) {
                        continue;
                    }
                    // 剩下的是形如 p := q 这样的语句
                    System.out.println(lvalue + "=" + rvalue + ":");
                    AbstractLocation lLocation = getLocation((Var) lvalue);
                    AbstractLocation rLocation = getLocation((Var) rvalue);
                    if (((Var) rvalue).getName().startsWith("temp$")) {
                        // 这是一个p := {q} p 应该直接指向 q
                        lLocation.parents.add(rLocation);
                        AbstractLocation.conditionalUnion(lLocation);
                        continue;
                    }
                    System.out.println(rLocation.realNewStmts);
                    // p 指向 q
                    lLocation.parents.addAll(rLocation.parents);
                    AbstractLocation.conditionalUnion(lLocation);
                    for (var parent : lLocation.parents) {
                        System.out.println(parent.realNewStmts);
                    }
                }
            }
        }
        var result = new PointerAnalysisResult();
        preprocess.test_pts.forEach((test_id, pt)->{
            AbstractLocation ptLocation = getLocation(pt);
            var ptResult = new TreeSet<Integer>();
            for (var parent : ptLocation.parents) {
                for (var stmt : parent.realNewStmts) {
                    ptResult.add(preprocess.obj_ids.get(stmt));
                }
            }
            result.put(test_id, ptResult);
        });
        return result;
    }
}
