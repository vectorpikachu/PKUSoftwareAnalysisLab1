package pku;

import pascal.taie.ir.exp.InvokeSpecial;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

import java.util.*;
import java.util.stream.Collectors;

public class Anderson {
    public HashMap<Var, HashSet<Var>> points_to; // 我们分析得到的指向集合的指向
    public HashMap<Var, HashSet<New>> points_locs; // 指针集合
    public JClass jclass;
    public PreprocessResult preprocess;
    public HashMap<Var, New> realLocs;
    public HashSet<JMethod> methodWorklist;
    public HashSet<JMethod> allMethods;

    public Anderson(JClass jclass, PreprocessResult preprocess) {
        this.jclass = jclass;
        this.preprocess = preprocess;
        points_to = new HashMap<>();
        realLocs = new HashMap<>();
        points_locs = new HashMap<>();
        methodWorklist = new HashSet<>();
        allMethods = new HashSet<>();
    }

    public PointerAnalysisResult getResult() {
        // methodWorklist.addAll(methods);
        var mainMethod = jclass.getDeclaredMethods()
                .stream().filter(method -> Objects.equals(method.getName(), "main"))
                .toList();
        var methods = new HashSet<JMethod>(mainMethod);
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
        System.out.println(points_locs);
        System.out.println(realLocs);
        System.out.println(preprocess.obj_ids);
        // System.out.println(points_to);
        // 分析points_to里面的东西, 输出到result
        var result = new PointerAnalysisResult();
        preprocess.test_pts.forEach((test_id, pt) -> {
            //System.out.println(pt);
            var ptResult = new TreeSet<Integer>();
            if (points_locs.containsKey(pt)) {
                points_locs.get(pt).forEach(loc -> {
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
                    realLocs.put(((New) stmt).getLValue(), (New) stmt);
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

                if (realLocs.containsKey(rvalue)) {
                    // p := temp${id}, 这是一个 p = {地址}的指向
                    addObjectCreationCConstraint((Var) lvalue, realLocs.get(rvalue));
                }
                else {
                    // p := q, 这里的q也可能是 temp${id} <- 来自函数调用
                    addCopyConstraint((Var) lvalue, (Var) rvalue);
                }
            } else if (stmt instanceof Invoke) {
                // temp$0 = invoke func.
                handleInvoke((Invoke) stmt);
            }
            System.out.println(points_to);
            System.out.println(points_locs);
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
            //System.out.println(points_to);
            changed = false; // 观察这次迭代是不是有集合被改变.
            for (var varPointsTo : points_to.entrySet()) {
                var nowVar = varPointsTo.getKey();
                var nowPointsTo = varPointsTo.getValue();
                for (var pointee : nowPointsTo) {
                    if (points_locs.containsKey(pointee)) {
                        changed |= points_locs.computeIfAbsent(nowVar, k -> new HashSet<>())
                                .addAll(points_locs.get(pointee));
                        //System.out.println(points_locs);
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
        var invokeUses = invokeExp.getUses(); // 这里的第一个, 如果有的话就是o.f()的o
        System.out.println("invokeUses = " + invokeUses);

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
            points_to.computeIfAbsent(receiver, k -> new HashSet<>())
                    .addAll(methodIR.getReturnVars());
        }

        for (int i = 0; i < invokeArgs.size(); i++) {
            var realArg = invokeArgs.get(i);
            var paramArg = methodIR.getParam(i);
            points_to.computeIfAbsent(paramArg, k -> new HashSet<>())
                    .add(realArg);
        }
        if (!isStatic) {
            // 不是静态方法, temp$0 = o.f(a, b)这样的
            var methodIRThis = methodIR.getThis(); // 这里的this = o
            var objs = invokeUses.stream().toList();
            var objVar = objs.isEmpty() ? null : objs.get(0);
            System.out.println(objVar);
            //应该filter objVar的指向使得它满足 method 的声明

            points_to.computeIfAbsent(methodIRThis, k -> new HashSet<>())
                    .add((Var) objVar);
            System.out.println(points_to);
        }
    }

}
