package pku;

import pascal.taie.World;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.proginfo.ExceptionEntry;
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
    public SimpleGraph<Exp> pointerFlowGraph; // Pointer Flow Graph
    public HashSet<JMethod> reachableMethods; // 可达的方法集合
    public SimpleGraph<CFGNode> callGraph;
    public JMethod entryMethod;
    public HashMap<Exp, HashSet<New>> pointsLocs; // 指向的地址
    public PreprocessResult preprocessResult;
    public HashMap<Var, Literal> varLiteral;

    public Solver(JMethod entryMethod, PreprocessResult preprocessResult) {
        workList = new HashMap<>();
        pointerFlowGraph = new SimpleGraph<>();
        reachableMethods = new HashSet<>();
        callGraph = new SimpleGraph<>();
        this.entryMethod = entryMethod;
        pointsLocs = new HashMap<>();
        this.preprocessResult = preprocessResult;
        varLiteral = new HashMap<>();
    }

    public PointerAnalysisResult getResult() {
        addReachable(entryMethod);
        for (var method : entryMethod.getDeclaringClass().getDeclaredMethods()) {
            if (method != entryMethod && method.isStaticInitializer()) {
                addReachable(method);
            }
        }

        while (!workList.isEmpty()) {
            //if (PointerAnalysis.exceedsTimeLimit()) {
            //    return null;
            //}
            if (PointerAnalysis.exceedsTimeLimit()) {
                return null;
            }
            Iterator<Map.Entry<Exp, HashSet<New>>> iterator = workList.entrySet().iterator();
            if (!iterator.hasNext()) {
                throw new NoSuchElementException();
            }
            Map.Entry<Exp, HashSet<New>> entry = iterator.next();
            var fstPointer = entry.getKey();
            var fstDeltaLocs = entry.getValue();
            iterator.remove(); // 删除当前键值对
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
                        var lidx = lvalue.getIndex();
                        var literalIndex = varLiteral.get(lidx);
                        addEdge(rvalue,
                                ArrayAccessFactory.getInstance(loc.getLValue(),
                                        lvalue.getIndex(), literalIndex));
                    }

                    for (var loadArray : ((Var) fstPointer).getLoadArrays()) {
                        // x = a[i]
                        var rvalue = loadArray.getArrayAccess();
                        var lvalue = loadArray.getLValue();
                        var ridx = rvalue.getIndex();
                        var literalIndex = varLiteral.get(ridx);
                        addEdge(ArrayAccessFactory.getInstance(loc.getLValue(), rvalue.getIndex(), literalIndex),
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
            reachableMethods.add(method);
            var methodIR = method.getIR();
            preprocessResult.analysis(methodIR);
            var methodStmts = methodIR.getStmts();

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
                    } else if (stmt instanceof ArrayStmt<?,?>) {
                        if (stmt instanceof LoadArray) {
                            var rvalue = ((LoadArray) stmt).getArrayAccess();
                            var robj = rvalue.getBase();
                            var ridx = rvalue.getIndex();
                            var literalIndex = varLiteral.get(ridx);
                            addEdge(ArrayAccessFactory.getInstance(robj, rvalue.getIndex(), literalIndex),
                                    ((LoadArray) stmt).getLValue());
                        } else if (stmt instanceof StoreArray) {
                            var lvalue = ((StoreArray) stmt).getArrayAccess();
                            var lobj = lvalue.getBase();
                            var lidx = lvalue.getIndex();
                            var literalIndex = varLiteral.get(lidx);
                            addEdge(((StoreArray) stmt).getRValue(),
                                    ArrayAccessFactory.getInstance(lobj, lvalue.getIndex(), literalIndex));
                        }
                    } else if (stmt instanceof AssignLiteral) {
                        var lvalue = ((AssignLiteral) stmt).getLValue();
                        var rvalue = ((AssignLiteral) stmt).getRValue();
                        varLiteral.computeIfAbsent(lvalue, __ -> (Literal) rvalue);
                    }
                } else if (stmt instanceof Invoke) {
                    // 如果是其他种类的call, 也需要一定的处理
                    var invokeExp = ((Invoke) stmt).getInvokeExp();
                    if (invokeExp instanceof InvokeStatic) {
                        processInvokeStatic((Invoke) stmt);
                    }
                    // 否则就是 InvokeInstanceExp
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
        for (var invoke : x.getInvokes()) {
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
                for (int i = 0; i < invokeArgs.size(); i++) {
                    addEdge(invokeArgs.get(i), params.get(i));
                }
                if (!methodIR.getReturnVars().isEmpty() && invoke.getLValue() != null) {
                    addEdge(methodIR.getReturnVars().get(0), invoke.getResult());
                }
            }
        }
    }

    public void processInvokeStatic(Invoke invoke) {
        var method = invoke.getMethodRef().resolve();
        var className = method.getDeclaringClass().getName();
        var methodName = method.getName();
        if (className.equals("benchmark.internal.Benchmark")
                || className.equals("benchmark.internal.BenchmarkN")) {
            if (methodName.equals("test") || methodName.equals("alloc")) {
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
