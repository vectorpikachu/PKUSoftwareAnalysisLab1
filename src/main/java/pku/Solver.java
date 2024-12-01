package pku;

import pascal.taie.World;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.graph.Edge;
import pascal.taie.util.graph.Graph;
import pascal.taie.util.graph.SimpleGraph;

import java.util.*;

public class Solver {
    public HashMap<Exp, HashSet<New>> workList; // Exp is a pointer, Hashset<New> is the locs.
    public HashSet<edge> workEdgeList; // 需要处理的callGraph的边
    public SimpleGraph<Exp> pointerFlowGraph; // Pointer Flow Graph
    public HashSet<JMethod> reachableMethods; // 可达的方法集合
    public SimpleGraph<CFGNode> callGraph;
    public JMethod entryMethod;
    public HashMap<Exp, HashSet<New>> pointsLocs; // 指向的地址
    public PreprocessResult preprocessResult;
    public HashSet<Stmt> reachableStmts;
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
        reachableInvoke = new HashSet<>();
    }

    public PointerAnalysisResult getResult() {
        addReachable(entryMethod);

        while (!workList.isEmpty()) {
            //if (PointerAnalysis.exceedsTimeLimit()) {
            //    return null;
            //}
            if (PointerAnalysis.exceedsTimeLimit()) {
                return null;
            }
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
                    for (var storeField : ((Var) fstPointer).getStoreFields()) {
                        var lvalue = storeField.getFieldAccess();
                        if (lvalue instanceof StaticFieldAccess) {
                            continue;
                        } else if (lvalue instanceof InstanceFieldAccess) {
                            // x.f = y
                            var rvalue = storeField.getRValue();
                            addEdge(rvalue, InstanceFieldAccessFactory.
                                    getInstance(lvalue.getFieldRef(), loc.getLValue()));
                        }
                    }

                    for (var loadField : ((Var) fstPointer).getLoadFields()) {
                        var rvalue = loadField.getFieldAccess();
                        if (rvalue instanceof StaticFieldAccess) {
                            continue;
                        } else if (rvalue instanceof InstanceFieldAccess) {
                            // y = x.f
                            var lvalue = loadField.getLValue();
                            addEdge(InstanceFieldAccessFactory.
                                    getInstance(rvalue.getFieldRef(), loc.getLValue()), lvalue);
                        }
                    }

                    for (var storeArray : ((Var) fstPointer).getStoreArrays()) {
                        // a[i] = x
                        var lvalue = storeArray.getArrayAccess();
                        var rvalue = storeArray.getRValue();
                        addEdge(rvalue,
                                ArrayAccessFactory.getInstance(lvalue.getIndex(), loc.getLValue()));
                    }

                    for (var loadArray : ((Var) fstPointer).getLoadArrays()) {
                        // x = a[i]
                        var rvalue = loadArray.getArrayAccess();
                        var lvalue = loadArray.getLValue();
                        addEdge(ArrayAccessFactory.getInstance(rvalue.getIndex(), loc.getLValue()),
                                lvalue);
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
            if (ptResult.isEmpty()) {
                ptResult.addAll(preprocessResult.obj_ids.values());
            }
            result.put(test_id, ptResult);
        });
        return result;
    }

    public void addReachable(JMethod method) {
        if (!reachableMethods.contains(method)) {
            System.out.println("Get here.");
            System.out.println(method + "\n");
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
                        System.out.println("Get here Copy." + stmt + "\n");
                        var lvalue = ((Copy) stmt).getLValue();
                        var rvalue = ((Copy) stmt).getRValue();
                        addEdge(rvalue, lvalue); // x = y, 加入 y -> x
                    } else if (stmt instanceof Cast) {
                        var lvalue = ((Cast) stmt).getLValue();
                        var rvalue = ((Cast) stmt).getRValue();
                        var rvalueReal = rvalue.getValue();
                        addEdge(rvalueReal, lvalue);
                    } else if (stmt instanceof FieldStmt<?,?>) {
                        if (stmt instanceof LoadField) {
                            var rvalue = ((LoadField) stmt).getFieldAccess();
                            if (rvalue instanceof StaticFieldAccess) {
                                addEdge(rvalue, ((LoadField) stmt).getLValue());
                                continue;
                            }
                        } else if (stmt instanceof StoreField) {
                            var lvalue = ((StoreField) stmt).getFieldAccess();
                            if (lvalue instanceof StaticFieldAccess) {
                                addEdge(((StoreField) stmt).getRValue(), lvalue);
                                continue;
                            }
                        }
                    }
                } else if (stmt instanceof Invoke) {
                    // 如果是其他种类的call, 也需要一定的处理
                    System.out.println("Get here3." + stmt + "\n");
                    var invokeExp = ((Invoke) stmt).getInvokeExp();
                    if (invokeExp instanceof InvokeStatic) {
                        System.out.println("Get here4." + stmt + "\n");
                        processInvokeStatic((Invoke) stmt);
                    }
                    // 否则就是 InvokeInstanceExp
                    reachableInvoke.add((Invoke) stmt);
                }
            }
        }
    }

    public void addEdge(Exp src, Exp dst) {
        if (!pointerFlowGraph.hasEdge(src, dst)) {
            System.out.println("Add edge:" + src + "->" + dst + "\n");
            System.out.println("pointsLocs:" + pointsLocs + "\n");
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
        for (var invoke : x.getInvokes()) {
            System.out.println("Try to process call:" + invoke + "\n");
            System.out.println(invoke.getMethodRef().getDeclaringClass());
            System.out.println(invoke.getMethodRef().getSubsignature());
            System.out.println(invoke.getMethodRef().isPolymorphicSignature());
            Type type = loc.getLValue().getType();
            var methodRef = invoke.getMethodRef();
            JMethod method = null;
            if (invoke.isInterface() || invoke.isVirtual()) {
                method = World.get().getClassHierarchy()
                        .dispatch(type, methodRef);
            } else if (invoke.isSpecial()) {
                method = World.get().getClassHierarchy()
                        .dispatch(methodRef.getDeclaringClass(), methodRef);
            } else if (invoke.isStatic()) {
                method = methodRef.resolveNullable();
            } else {
                method = invoke.getMethodRef().resolve();
            }
            if (method == null) {
                continue;
            }
            System.out.println("Get Method:" + method + method.getSignature());
            try {
                method.getIR();
            } catch (Exception e) {
                continue;
            }
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
                if (!methodIR.getReturnVars().isEmpty() && invoke.getLValue() != null) {
                    System.out.println(methodIR.getReturnVars().get(0) + "->" + x + "\n");
                    addEdge(methodIR.getReturnVars().get(0), invoke.getResult());
                }
            }
        }
    }

    public void processInvokeStatic(Invoke invoke) {

        System.out.println("Get here2.\n" + invoke + "\n");
        var method = invoke.getMethodRef().resolve();
        var className = method.getDeclaringClass().getName();
        var methodName = method.getName();
        if (className.equals("benchmark.internal.Benchmark")
                || className.equals("benchmark.internal.BenchmarkN")) {
            if (methodName.equals("test") || methodName.equals("alloc")) {
                System.out.println("进入了alloc和test");
                return;
            }
        }

        var methodIR = method.getIR();
        var callSite = CFGNodeFactory.getInstance(invoke);
        var callee = CFGNodeFactory.getInstance(method);

        if (!callGraph.hasEdge(callSite, callee)) {
            callGraph.addEdge(callSite, callee);
            addReachable(method);
            var invokeArgs = invoke.getInvokeExp().getArgs();
            var params = methodIR.getParams();
            for (int i = 0; i < invokeArgs.size(); i++) {
                addEdge(invokeArgs.get(i), params.get(i));
            }
            if (!methodIR.getReturnVars().isEmpty() && invoke.getLValue() != null) {
                addEdge(methodIR.getReturnVars().get(0), invoke.getLValue());
            }
        }
    }

    public record edge(Stmt callSite, JMethod callee) {}
}
