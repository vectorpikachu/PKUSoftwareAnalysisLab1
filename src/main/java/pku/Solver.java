package pku;

import pascal.taie.ir.exp.*;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.graph.Edge;
import pascal.taie.util.graph.Graph;
import pascal.taie.util.graph.SimpleGraph;

import java.util.*;

public class Solver {
    public HashMap<Exp, HashSet<New>> workList; // Exp is a pointer, Hashset<New> is the locs.
    public SimpleGraph<Exp> pointerFlowGraph; // Pointer Flow Graph
    public HashSet<JMethod> reachableMethods; // 可达的方法集合
    public SimpleGraph<CFGNode> callGraph;
    public JMethod entryMethod;
    public HashMap<Exp, HashSet<New>> pointsLocs; // 指向的地址
    public PreprocessResult preprocessResult;
    public HashSet<Stmt> reachableStmts;
    public HashSet<LoadField> reachableLoadField;
    public HashSet<StoreField> reachableStoreField;
    public HashSet<Invoke> reachableInvoke;

    public Solver(JMethod entryMethod, PreprocessResult preprocessResult) {
        workList = new HashMap<>();
        pointerFlowGraph = new SimpleGraph<>();
        reachableMethods = new HashSet<>();
        callGraph = new SimpleGraph<>();
        this.entryMethod = entryMethod;
        pointsLocs = new HashMap<>();
        this.preprocessResult = preprocessResult;
        reachableStmts = new HashSet<>();
        reachableLoadField = new HashSet<>();
        reachableStoreField = new HashSet<>();
        reachableInvoke = new HashSet<>();
    }

    public PointerAnalysisResult getResult() {
        addReachable(entryMethod);

        while (!workList.isEmpty()) {
            //if (PointerAnalysis.exceedsTimeLimit()) {
            //    return null;
            //}
            System.out.println("workList:" + workList + "\n");
            System.out.println("reachableMethods:" + reachableMethods + "\n");

            Iterator<Map.Entry<Exp, HashSet<New>>> iterator = workList.entrySet().iterator();
            if (!iterator.hasNext()) {
                throw new NoSuchElementException();
            }
            Map.Entry<Exp, HashSet<New>> entry = iterator.next();
            var fstPointer = entry.getKey();
            var fstDeltaLocs = entry.getValue();
            iterator.remove(); // 删除当前键值对
            System.out.println("now pointer:" + fstPointer + "\n");
            if (pointsLocs.get(fstPointer) != null) {
                fstDeltaLocs.removeAll(pointsLocs.get(fstPointer));
            }
            propagate(fstPointer, fstDeltaLocs);
            if (fstPointer instanceof Var) {
                for (var loc : fstDeltaLocs) {
                    for (var storeField : reachableStoreField) {
                        var lvalue = storeField.getFieldAccess();
                        if (lvalue instanceof StaticFieldAccess) {
                            continue;
                        } else if (lvalue instanceof InstanceFieldAccess) {
                            // x.f = y
                            var lbase = ((InstanceFieldAccess) lvalue).getBase();
                            if (!lbase.equals(fstPointer)) {
                                continue;
                            }
                            var rvalue = storeField.getRValue();
                            addEdge(rvalue, InstanceFieldAccessFactory.
                                    getInstance(lvalue.getFieldRef(), loc.getLValue()));
                        }
                    }

                    for (var loadField : reachableLoadField) {
                        var rvalue = loadField.getFieldAccess();
                        if (rvalue instanceof StaticFieldAccess) {
                            continue;
                        } else if (rvalue instanceof InstanceFieldAccess) {
                            // y = x.f
                            var rbase = ((InstanceFieldAccess) rvalue).getBase();
                            if (!rbase.equals(fstPointer)) {
                                continue;
                            }
                            var lvalue = loadField.getLValue();
                            addEdge(InstanceFieldAccessFactory.
                                    getInstance(rvalue.getFieldRef(), loc.getLValue()), lvalue);
                        }
                    }

                    processCall((Var) fstPointer, loc);
                }
            }
        }

        PointerAnalysisResult result = new PointerAnalysisResult();
        preprocessResult.test_pts.forEach((test_id, pt) -> {
            var ptResult = new TreeSet<Integer>();

            if (pointsLocs.containsKey(pt)) {
                pointsLocs.get(pt).forEach(loc -> {
                    var intLoc = preprocessResult.obj_ids.get(loc);
                    if (intLoc != null) {
                        ptResult.add(intLoc);
                    }
                });
            }
            result.put(test_id, ptResult);
        });
        return result;
    }

    public void addReachable(JMethod method) {
        if (!reachableMethods.contains(method)) {
            reachableMethods.add(method);
            var methodIR = method.getIR();
            preprocessResult.analysis(methodIR);
            var methodStmts = methodIR.getStmts();
            reachableStmts.addAll(methodStmts);

            for (var stmt : methodStmts) {
                if (stmt instanceof AssignStmt<?,?>) {
                    if (stmt instanceof New) {
                        var lvalue = ((New) stmt).getLValue();
                        workList.computeIfAbsent(lvalue, __ -> new HashSet<>())
                                .add((New) stmt);
                    } else if (stmt instanceof Copy) {
                        var lvalue = ((Copy) stmt).getLValue();
                        var rvalue = ((Copy) stmt).getRValue();
                        addEdge(rvalue, lvalue); // x = y, 加入 y -> x
                    } else if (stmt instanceof FieldStmt<?,?>) {
                        if (stmt instanceof LoadField) {
                            reachableLoadField.add((LoadField) stmt);
                        } else if (stmt instanceof StoreField) {
                            reachableStoreField.add((StoreField) stmt);
                        }
                    }
                } else if (stmt instanceof Invoke) {
                    reachableInvoke.add((Invoke) stmt);
                }
            }
        }
    }

    public void addEdge(Exp src, Exp dst) {
        if (!pointerFlowGraph.hasEdge(src, dst)) {
            pointerFlowGraph.addEdge(src, dst);
            if (pointsLocs.get(src) != null) {
                workList.computeIfAbsent(dst, __ -> new HashSet<>())
                        .addAll(pointsLocs.get(src));
            }
        }
    }

    public void propagate(Exp pointer, HashSet<New> pts) {
        if (!pts.isEmpty()) {
            pointsLocs.computeIfAbsent(pointer, __ -> new HashSet<>())
                    .addAll(pts);
            for (var succ : pointerFlowGraph.getSuccsOf(pointer)) {
                workList.computeIfAbsent(succ, __ -> new HashSet<>())
                        .addAll(pts);
            }
        }
    }

    public void processCall(Var x, New loc) {
        var thisReachableInvoke = new HashSet<Invoke>();

        for (var invoke : reachableInvoke) {
            var invokeExp = invoke.getInvokeExp();
            if (!(invokeExp instanceof InvokeInstanceExp)) {
                continue;
            }
            if (((InvokeInstanceExp) invokeExp).getBase() == null) {
                continue;
            }
            thisReachableInvoke.add(invoke);
        }

        System.out.println(x + "/" + loc + "/" + thisReachableInvoke + "\n");

        for (var invoke : thisReachableInvoke) {
            var method = invoke.getMethodRef().resolve();
            var methodIR = method.getIR();
            workList.computeIfAbsent(methodIR.getThis(), __ -> new HashSet<>())
                    .add(loc);
            var callSite = CFGNodeFactory.getInstance(invoke);
            var callee = CFGNodeFactory.getInstance(method);

            if (!callGraph.hasEdge(callSite, callee)) {
                callGraph.addEdge(callSite, callee);
                addReachable(method);
                var invokeArgs = invoke.getInvokeExp().getArgs();
                var params = methodIR.getParams();
                System.out.println("Get here1.\n" + invokeArgs.size());
                for (int i = 0; i < invokeArgs.size(); i++) {
                    addEdge(invokeArgs.get(i), params.get(i));
                }
                if (!methodIR.getReturnVars().isEmpty()) {
                    System.out.println(methodIR.getReturnVars().get(0) + "->" + x + "\n");
                    addEdge(methodIR.getReturnVars().get(0), x);
                }
            }
        }
    }
}
