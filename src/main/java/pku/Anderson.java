package pku;

import pascal.taie.ir.exp.*;
import pascal.taie.ir.proginfo.ExceptionEntry;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Anderson {
    public HashMap<Exp, HashSet<Exp>> varPointsTo; // 我们分析得到的指向集合的指向, 类似于Move(to -> from)的一张图
    // 如果是LoadFld 的话这个指向关系保存在varPointsTo里面, 记为 y -> x.f
    public HashMap<Exp, HashSet<Exp>> fldPointsTo; // 域的指向集合的关系, 就是我们现在记录 x.f -> b for store(x.f, b)
    // 然后在更新varPointsLocs的时候更新
    public HashMap<Exp, HashSet<New>> varPointsLocs; // 指针集合
    public HashMap<Exp, HashSet<New>> fldPointsLocs; // 记录fld的指向
    public JClass jclass;
    public PreprocessResult preprocess;
    public HashMap<Exp, New> realLocs;
    public HashSet<JMethod> methodWorklist;
    public HashSet<JMethod> allMethods;
    private Exp pt;

    public Anderson(JClass jclass, PreprocessResult preprocess) {
        this.jclass = jclass;
        this.preprocess = preprocess;
        varPointsTo = new HashMap<>();
        realLocs = new HashMap<>();
        varPointsLocs = new HashMap<>();
        methodWorklist = new HashSet<>();
        allMethods = new HashSet<>();
        fldPointsTo = new HashMap<>();
        fldPointsLocs = new HashMap<>();
    }

    /*
     * Field: a1.<benchmark.objects.A: benchmark.objects.B f> = b1;
     */

    public PointerAnalysisResult getResult() {
        // methodWorklist.addAll(methods);
        var mainMethod = jclass.getDeclaredMethods()
                .stream().filter(method -> Objects.equals(method.getName(), "main"))
                .toList();
        var methods = new HashSet<>(mainMethod);
        allMethods.addAll(methods);
        do {
            for (var method : methods) {
                if (method.isAbstract()) {
                    continue;
                }
                System.out.println(method.getName());
                var ir = method.getIR(); // Get intermediate representation
                preprocess.analysis(ir);
                // 方法里的语句的container属性包含了method.
                processStatements(ir.getStmts());
            }

            methods.clear();
            methods.addAll(methodWorklist);
            allMethods.addAll(methodWorklist);
            methodWorklist.clear();
        } while(!methods.isEmpty());

        // Propagate constraints
        propagateConstraints();
        System.out.println(varPointsLocs);
        System.out.println(realLocs);
        System.out.println(preprocess.obj_ids);
        // System.out.println(varPointsTo);
        // 分析varPointsTo里面的东西, 输出到result
        var result = new PointerAnalysisResult();
        preprocess.test_pts.forEach((test_id, pt) -> {
            //System.out.println(pt);
            var ptResult = new TreeSet<Integer>();
            if (varPointsLocs.containsKey(pt)) {
                varPointsLocs.get(pt).forEach(loc -> {
                    ptResult.add(preprocess.obj_ids.get(loc));
                    System.out.println(pt + ":" + ptResult);
                });
            }
            //System.out.println(ptResult);
            result.put(test_id, ptResult);
        });
        return result;
    }

    public void processStatements(List<Stmt> stmts) {
        String[] valueTypes = {"int", "float"};
        for (var stmt : stmts) {
            System.out.println("Original stmt:" + stmt);
            if (stmt instanceof AssignStmt<?,?>) {
                if (stmt instanceof New) {
                    // 对于 temp$0 = new B();
                    addObjectCreationCConstraint((New) stmt);
                    continue;
                }
                if (stmt instanceof FieldStmt<?,?>) {
                    // 处理FieldStmt, 只要出现FieldAccess就会来到这个Stmt.
                    handleField((FieldStmt<?, ?>) stmt);
                    continue;
                }
                if (stmt instanceof ArrayStmt<?, ?>) {
                    // a[...] = x;
                    // x = a [...]
                    handleArray((ArrayStmt<?, ?>) stmt);
                    continue;
                }
                var lvalue = ((AssignStmt<?, ?>) stmt).getLValue();
                var rvalue = ((AssignStmt<?, ?>) stmt).getRValue();
                String lvalue_type = lvalue.getType().toString();
                String rvalue_type = rvalue.getType().toString();

                boolean shouldContinue = false;
                for (String prefix : valueTypes) {
                    if (lvalue_type.startsWith(prefix)
                        || rvalue_type.startsWith(prefix)) {
                        shouldContinue = true;
                        break; // 一旦匹配，不需要继续检查
                    }
                }
                if (shouldContinue) {
                    continue; // 跳过当前循环迭代
                }
                // p := q, 这里的q也可能是 temp${id} <- 来自函数调用
                addCopyConstraint(lvalue, rvalue);
            } else if (stmt instanceof Invoke) {
                // temp$0 = invoke func.
                handleInvoke((Invoke) stmt);
            }
            System.out.println(varPointsTo);
            System.out.println(varPointsLocs);
        }
    }

    public void addObjectCreationCConstraint(New stmt) {
        var lvalue = stmt.getLValue();
        // varPointsTo.put(lvalue, new HashSet<>());
        // temp${id}指向自己
        varPointsTo.computeIfAbsent(lvalue, k -> new HashSet<>()).add(lvalue);
        varPointsLocs.computeIfAbsent(lvalue, k -> new HashSet<>()).add(stmt);
    }
    public void addCopyConstraint(Exp p, Exp q) {
        // 如果我们的目的是为了保存 Assgn(to <- from)关系的话, 这一步其实应该放到propagate里面做
        // 但是在这里可以提前做掉.
        varPointsLocs.computeIfAbsent(p, k -> new HashSet<>()).addAll(
                varPointsLocs.getOrDefault(q, new HashSet<>())
        );
        // 应该是 a |-> b 也就是把 b 的所有指向 加入 a 中
        varPointsTo.computeIfAbsent(p, k -> new HashSet<>()).add(q); // 再加入q
    }
    public void addFldStoreConstraint(Exp p, Exp q) {
        // 保存 StoreFld
        fldPointsTo.computeIfAbsent(p, k -> new HashSet<>()).add(q);
        // 可以先行处理一部分
        var lobj = ((InstanceFieldAccess) p).getBase();
        System.out.println(lobj.getStoreFields());
        varPointsLocs.computeIfAbsent(lobj, k -> new HashSet<>());
        varPointsLocs.get(lobj).forEach(ptLoc -> {
            var fieldPt = InstanceFieldAccessFactory.getInstance(((InstanceFieldAccess) p).getFieldRef(), ptLoc.getLValue());
            fldPointsLocs.computeIfAbsent(fieldPt, k -> new HashSet<>()).addAll(
                    varPointsLocs.getOrDefault(q, new HashSet<>())
            );
        });
    }
    public void addFldLoadConstraint(Exp p, Exp q) {
        // 首先保存 x -> y.f
        varPointsTo.computeIfAbsent(p, k -> new HashSet<>()).add(q);
        // 可以先行处理一部分
        var robj = ((InstanceFieldAccess) q).getBase();
        varPointsLocs.computeIfAbsent(robj, k -> new HashSet<>());
        varPointsLocs.get(robj).forEach(ptLoc -> {
            var fieldPt = InstanceFieldAccessFactory.getInstance(((FieldAccess) q).getFieldRef(), ptLoc.getLValue());
            varPointsLocs.computeIfAbsent(p, k -> new HashSet<>()).addAll(
                    fldPointsLocs.getOrDefault(fieldPt, new HashSet<>())
            );
        });
    }

    public void propagateConstraints() {
        boolean changed;
        do {
            //System.out.println(varPointsTo);
            changed = false; // 观察这次迭代是不是有集合被改变.
            for (var varPt : varPointsTo.entrySet()) {
                var nowVar = varPt.getKey();
                var nowPointsTo = varPt.getValue();
                for (var pointee : nowPointsTo) {
                    if (varPointsLocs.containsKey(pointee)) {
                        changed |= varPointsLocs.computeIfAbsent(nowVar, k -> new HashSet<>())
                                .addAll(varPointsLocs.get(pointee));
                        //System.out.println(varPointsLocs);
                    } else if (pointee instanceof FieldAccess) {
                        var obj = ((InstanceFieldAccess) pointee).getBase();
                        varPointsLocs.computeIfAbsent(obj, k -> new HashSet<>());
                        for (var ptLoc : varPointsLocs.get(obj)) {
                            var fieldPt = InstanceFieldAccessFactory.getInstance(((InstanceFieldAccess) pointee).getFieldRef(), ptLoc.getLValue());
                            changed |= varPointsLocs.computeIfAbsent(nowVar, k -> new HashSet<>()).addAll(
                                    fldPointsLocs.getOrDefault(fieldPt, new HashSet<>())
                            );
                        }
                    }
                }
            }

            // 接下来更新 fldPointsLocs
            for (var fldPt : fldPointsTo.entrySet()) {
                var nowFld = fldPt.getKey();
                var obj = ((InstanceFieldAccess) nowFld).getBase();
                var nowFldPointsTo = fldPt.getValue();
                // fldPointsTo只有可能指向 一个 Var
                for (var pointee : nowFldPointsTo) {
                    varPointsLocs.computeIfAbsent(obj, k -> new HashSet<>());
                    for (var ptLoc : varPointsLocs.get(obj)) {
                        var fieldPt = InstanceFieldAccessFactory.getInstance(((FieldAccess) nowFld).getFieldRef(), ptLoc.getLValue());
                        changed |= fldPointsLocs.computeIfAbsent(fieldPt, k -> new HashSet<>())
                                .addAll(varPointsLocs.getOrDefault(pointee, new HashSet<>()));
                    }
                }
            }
        } while (changed);
    }

    public void handleInvoke(Invoke stmt) {
        // 按照 0-CFA 算法来实现
        /*
          f() {... x = y.g(a, b); ...}
          ∀y ∈ f#y, ∀m ∈ targets(y, g),
          f#x ⊇ m#ret
          m#this ⊇ filter(f#y, declared(m)), 在这里就应该是Uses里面的
          m#a ⊇ f#a
          m#b ⊇ f#b
          这里的targets根据调用对象和方法名确定被调用方法
          declared是m的声明
          filter保留符合特定类型的对象
         */
        // 不用特意区分每个方法的不同变量, 因为Var包裹的话就算名字相同也是不同的变量
        var invokeExp = stmt.getInvokeExp(); // 右边的调用表达式
        if (invokeExp instanceof InvokeSpecial) {
            // 一些奇怪的比如 init, super等等
            return;
        }

        var receiver = stmt.getResult(); // 左边接受调用

        var invokeMethodRef = invokeExp.getMethodRef(); // 获取调用的方法引用
        var className = invokeMethodRef.getDeclaringClass().getName();
        if (className.startsWith("benchmark.internal.Benchmark")
            || className.startsWith("benchmark.internal.BenchmarkN")) {
            return;
        }

        System.out.println(stmt);
        if (receiver != null) {
            System.out.println("receiver = " + receiver);
        }

        var invokeArgs = invokeExp.getArgs(); // 获取调用的参数

        var resolvedMethod = invokeMethodRef.resolve(); // 返回the concrete class member pointed by this reference
        var methodIR = resolvedMethod.getIR();

        // 把这段函数也要加入进来
        if (!allMethods.contains(resolvedMethod)) {
            methodWorklist.add(resolvedMethod);
            System.out.println(methodWorklist);
        }

        // invokeMethodRef.getDeclaringClass(); // 返回the declaring class of the reference.
        var isStatic = invokeMethodRef.isStatic(); // 静态的话就不用加o.f()这样
        System.out.println(invokeMethodRef);

        System.out.println("receriver " + receiver + " ret " + methodIR.getReturnVars());

        if (receiver != null) {
            varPointsTo.computeIfAbsent(receiver, k -> new HashSet<>())
                    .addAll(methodIR.getReturnVars());
        }

        for (int i = 0; i < invokeArgs.size(); i++) {
            var realArg = invokeArgs.get(i);
            var paramArg = methodIR.getParam(i);
            varPointsTo.computeIfAbsent(paramArg, k -> new HashSet<>())
                    .add(realArg);
        }
        if (!isStatic) {
            // 不是静态方法, temp$0 = o.f(a, b)这样的
            var methodIRThis = methodIR.getThis(); // 这里的this = o
            var objVar = ((InvokeInstanceExp) invokeExp).getBase();
            System.out.println(objVar);
            //应该filter objVar的指向使得它满足 method 的声明

            varPointsTo.computeIfAbsent(methodIRThis, k -> new HashSet<>())
                    .add(objVar);
            System.out.println(varPointsTo);
        }
    }

    // 处理 FieldStmt
    public void handleField(FieldStmt<?,?> stmt) {
        // 把每一个 Field 都拆开成单独的 变量
        if (stmt instanceof StoreField) {
            // 是一个o.f = a; 这样的语句
            // \forall x \in o, x.f \supset a
            var lvalue = stmt.getFieldAccess();
            var rvalue = stmt.getRValue();
            addFldStoreConstraint(lvalue, rvalue);
        } else if (stmt instanceof LoadField) {
            // 是一个 x = o.f; 这样的结构
            var lvalue = stmt.getLValue();
            var rvalue = stmt.getFieldAccess();
            addFldLoadConstraint(lvalue, rvalue);
        }
    }

    public void handleArray(ArrayStmt<?, ?> stmt) {
        // 直接把a[i]视为一个Var
        // 一个数组的下标实际上是不知道的?
        if (stmt instanceof LoadArray) {
            // x = a[...]
            var lvalue = stmt.getLValue();
            var rvalue = stmt.getArrayAccess();
            var robj = rvalue.getBase();
            var rindex = rvalue.getIndex();
            varPointsTo.computeIfAbsent(robj, k -> new HashSet<>()).forEach(pt -> {
                var arrayPt = ArrayAccessFactory.getInstance((Var) pt, rindex);
                addCopyConstraint(lvalue, arrayPt);
            });
        } else if (stmt instanceof StoreArray) {
            // a[...] = x;
            var lvalue = stmt.getArrayAccess();
            var rvalue = stmt.getRValue();
            var lobj = lvalue.getBase();
            var lindex = lvalue.getIndex();
            varPointsTo.computeIfAbsent(lobj, k -> new HashSet<>()).forEach(pt -> {
                var arrayPt = ArrayAccessFactory.getInstance((Var) pt, lindex);
                addCopyConstraint(arrayPt, rvalue);
            });
        }
    }
}
