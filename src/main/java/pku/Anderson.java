package pku;

import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JClass;

import java.util.*;

public class Anderson {
    public HashMap<Var, HashSet<Var>> points_to; // 我们分析得到的指向集合的指向
    public HashMap<Var, HashSet<New>> points_locs; // 指针集合
    public JClass jclass;
    public PreprocessResult preprocess;
    public HashMap<Var, New> realLocs;

    public Anderson(JClass jclass, PreprocessResult preprocess) {
        this.jclass = jclass;
        this.preprocess = preprocess;
        points_to = new HashMap<>();
        realLocs = new HashMap<>();
        points_locs = new HashMap<>();
    }

    public PointerAnalysisResult getResult() {
        var methods = jclass.getDeclaredMethods();
        for (var method : methods) {
            if (method.isAbstract()) {
                continue;
            }
            var ir = method.getIR(); // Get intermediate representation
            preprocess.analysis(ir);
            processStatements(ir.getStmts());
        }
        // Propagate constraints
        propagateConstraints();
        // System.out.println(points_to);
        // 分析points_to里面的东西, 输出到result
        var result = new PointerAnalysisResult();
        preprocess.test_pts.forEach((test_id, pt) -> {
            System.out.println(pt);
            var ptResult = new TreeSet<Integer>();
            points_locs.get(pt).forEach(loc -> {
                ptResult.add(preprocess.obj_ids.get(loc));
            });
            System.out.println(ptResult);
            result.put(test_id, ptResult);
        });
        return result;
    }

    public void processStatements(List<Stmt> stmts) {
        for (var stmt : stmts) {
            if (stmt instanceof AssignStmt<?,?>) {
                if (stmt instanceof New) {
                    // 对于 temp$0 = new B();
                    realLocs.put(((New) stmt).getLValue(), (New) stmt);
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
                System.out.println(lvalue + "=" + rvalue + ":");
                if (((Var) rvalue).getName().startsWith("temp$")) {
                    // p := temp${id}, 这是一个 p = {地址}的指向
                    addObjectCreationCConstraint((Var) lvalue, realLocs.get(rvalue));
                }
                else {
                    // p := q
                    addCopyConstraint((Var) lvalue, (Var) rvalue);
                }
            }
        }
    }

    public void addObjectCreationCConstraint(Var p, New q) {
        // points_to.computeIfAbsent(p, k -> new HashSet<>()).add(q);
        points_locs.computeIfAbsent(p, k -> new HashSet<>()).add(q);
    }
    public void addCopyConstraint(Var p, Var q) {
        points_locs.computeIfAbsent(p, k -> new HashSet<>()).addAll(
                points_locs.getOrDefault(q, new HashSet<>())
        );
        points_to.computeIfAbsent(p, k -> new HashSet<>()).add(q); // 再加入q
    }

    public void propagateConstraints() {
        boolean changed;
        do {
            System.out.println(points_to);
            changed = false; // 观察这次迭代是不是有集合被改变.
            for (var varPointsTo : points_to.entrySet()) {
                var nowVar = varPointsTo.getKey();
                var nowPointsTo = varPointsTo.getValue();
                for (var pointee : nowPointsTo) {
                    if (points_locs.containsKey(pointee)) {
                        changed |= points_locs.get(nowVar).addAll(points_locs.get(pointee));
                        System.out.println(points_locs);
                    }
                }
            }
        } while (changed);
    }
}
