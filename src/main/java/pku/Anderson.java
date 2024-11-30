package pku;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.misc.IRDumper;
import pascal.taie.analysis.pta.plugin.util.InvokeHandler;
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
    private static final Logger logger = LogManager.getLogger(IRDumper.class);

    public HashMap<Exp, HashSet<Exp>> varPointsTo; // 我们分析得到的指向集合的指向, 类似于Move(to -> from)的一张图
    // 如果是LoadFld 的话这个指向关系保存在varPointsTo里面, 记为 y -> x.f
    public HashMap<Exp, HashSet<Exp>> fldPointsTo; // 域的指向集合的关系, 就是我们现在记录 x.f -> b for store(x.f, b)
    public HashMap<Exp, HashSet<Exp>> arrayPointsTo; // 数组的指向集合的关系, 就是我们现在记录 a[i] -> b for store(a[i], b)
    // 然后在更新varPointsLocs的时候更新
    public HashMap<Exp, HashSet<New>> varPointsLocs; // 指针集合
    public HashMap<Exp, HashSet<New>> fldPointsLocs; // 记录fld的指向
    public HashMap<Exp, HashSet<New>> arrayPointsLocs; // 记录数组的指向, 把数组记录为类似于域一样的
    public HashMap<JMethod, HashSet<Throw>> methodExceptions; // 记录当前方法产生的异常
    public JClass jclass;
    public PreprocessResult preprocess;
    public HashMap<Exp, New> realLocs;
    public HashSet<JMethod> methodWorklist;
    public HashSet<JMethod> allMethods;
    public JMethod mainMethod;

    public Anderson(JMethod main, JClass jclass, PreprocessResult preprocess) {
        this.jclass = jclass;
        this.preprocess = preprocess;
        this.mainMethod = main;
        varPointsTo = new HashMap<>();
        realLocs = new HashMap<>();
        varPointsLocs = new HashMap<>();
        methodWorklist = new HashSet<>();
        allMethods = new HashSet<>();
        fldPointsTo = new HashMap<>();
        fldPointsLocs = new HashMap<>();
        methodExceptions = new HashMap<>();
        arrayPointsLocs = new HashMap<>();
        arrayPointsTo = new HashMap<>();
    }

    /*
     * Field: a1.<benchmark.objects.A: benchmark.objects.B f> = b1;
     */

    public PointerAnalysisResult getResult() {
        // methodWorklist.addAll(methods);
        var methods = new HashSet<JMethod>();
        methods.add(mainMethod);
        allMethods.addAll(methods);
        do {
            for (var method : methods) {
                if (method.isAbstract()) {
                    continue;
                }
                System.out.println("\n\n\n");
                System.out.println(method.getName());
                System.out.println("\n\n\n");
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
        boolean isTimeLimit = propagateConstraints();
        if (!isTimeLimit) {
            return null;
        }
        System.out.println(varPointsLocs);
        System.out.println(realLocs);
        System.out.println(preprocess.obj_ids);
        System.out.println(preprocess.test_pts);
        // System.out.println(varPointsTo);
        // 分析varPointsTo里面的东西, 输出到result
        var result = new PointerAnalysisResult();
        preprocess.test_pts.forEach((test_id, pt) -> {
            //System.out.println(pt);
            var ptResult = new TreeSet<Integer>();
            if (varPointsLocs.containsKey(pt)) {
                varPointsLocs.get(pt).forEach(loc -> {
                    logger.info(pt + ":" + loc + ":" + preprocess.obj_ids.get(loc));
                    var intLoc = preprocess.obj_ids.get(loc);
                    if (intLoc != null) {
                        ptResult.add(intLoc);
                    }
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
                if (stmt instanceof Cast) {
                    // 强制类型转换
                    // A b = (A) a; 直接类似于 b = a
                    logger.info(stmt);
                    var lvalue = ((Cast) stmt).getLValue();
                    var rvalue = ((Cast) stmt).getRValue().getValue();
                    logger.info(lvalue + "=" + rvalue);
                    addCopyConstraint(lvalue, rvalue);
                    continue;
                }
                if (stmt instanceof Copy) {
                    // 赋值语句
                    var lvalue = ((Copy) stmt).getLValue();
                    var rvalue = ((Copy) stmt).getRValue();
                    addCopyConstraint(lvalue, rvalue);
                    continue;
                }
            } else if (stmt instanceof Invoke) {
                // temp$0 = invoke func.
                handleInvoke((Invoke) stmt);
            } else if (stmt instanceof Throw) {
                handleThrow((Throw) stmt);
            } else if (stmt instanceof Catch) {
                handleCatch((Catch) stmt);
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
    public void addArrayStoreConstraint(Exp p, Exp q) {
        // 保存 StoreArray
        var lobj = ((ArrayAccess) p).getBase();
        var lindex = ((ArrayAccess) p).getIndex();
        var lInstance = ArrayAccessFactory.getInstance((Var) lobj, (Var) lindex);
        arrayPointsTo.computeIfAbsent(lInstance, k -> new HashSet<>()).add(q);
        varPointsLocs.computeIfAbsent(lobj, k -> new HashSet<>());
        varPointsLocs.get(lobj).forEach(ptLoc -> {
            var arrayPt = ArrayAccessFactory.getInstance((Var) ptLoc.getLValue(), (Var) lindex);
            arrayPointsLocs.computeIfAbsent(arrayPt, k -> new HashSet<>()).addAll(
                    varPointsLocs.getOrDefault(q, new HashSet<>())
            );
        });
    }
    public void addArrayLoadConstraint(Exp p, Exp q) {
        // 保存 LoadArray
        var robj = ((ArrayAccess) q).getBase();
        var rindex = ((ArrayAccess) q).getIndex();
        var rInstance = ArrayAccessFactory.getInstance((Var) robj, (Var) rindex);
        varPointsTo.computeIfAbsent(p, k -> new HashSet<>()).add(rInstance);
        varPointsLocs.computeIfAbsent(robj, k -> new HashSet<>());
        varPointsLocs.get(robj).forEach(ptLoc -> {
            var arrayPt = ArrayAccessFactory.getInstance((Var) ptLoc.getLValue(), (Var) rindex);
            varPointsLocs.computeIfAbsent(p, k -> new HashSet<>()).addAll(
                    arrayPointsLocs.getOrDefault(arrayPt, new HashSet<>())
            );
        });
    }

    public boolean propagateConstraints() {
        boolean changed;
        do {
            if (PointerAnalysis.exceedsTimeLimit()) {
                // 超时了立刻返回
                return false;
            }
            System.out.println(fldPointsLocs);
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
                        logger.info("\n\n\n" + varPointsTo + "\n\n\n");
                        logger.info("\n\n\n" + varPointsLocs + "\n\n\n");
                    } else if (pointee instanceof ArrayAccess) {
                        var obj = ((ArrayAccess) pointee).getBase();
                        var index = ((ArrayAccess) pointee).getIndex();
                        varPointsLocs.computeIfAbsent(obj, k -> new HashSet<>());
                        for (var ptLoc : varPointsLocs.get(obj)) {
                            var arrayPt = ArrayAccessFactory.getInstance((Var) ptLoc.getLValue(), (Var) index);
                            changed |= varPointsLocs.computeIfAbsent(nowVar, k -> new HashSet<>()).addAll(
                                    arrayPointsLocs.getOrDefault(arrayPt, new HashSet<>())
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
            // 接下来更新arrayPointsLocs
            for (var arrayPt : arrayPointsTo.entrySet()) {
                var nowArray = arrayPt.getKey();
                var obj = ((ArrayAccess) nowArray).getBase();
                var index = ((ArrayAccess) nowArray).getIndex();
                var nowArrayPointsTo = arrayPt.getValue();
                for (var pointee : nowArrayPointsTo) {
                    varPointsLocs.computeIfAbsent(obj, k -> new HashSet<>());
                    for (var ptLoc : varPointsLocs.get(obj)) {
                        var arrayPtLoc = ArrayAccessFactory.getInstance((Var) ptLoc.getLValue(), (Var) index);
                        changed |= arrayPointsLocs.computeIfAbsent(arrayPtLoc, k -> new HashSet<>())
                                .addAll(varPointsLocs.getOrDefault(pointee, new HashSet<>()));
                    }
                }
            }
        } while (changed);
        return true; // 正常结束的
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
        /*
        if (invokeExp instanceof InvokeSpecial) {
            // 一些奇怪的比如 init, super等等
            return;
        }*/

        var receiver = stmt.getResult(); // 左边接受调用

        var invokeMethodRef = invokeExp.getMethodRef(); // 获取调用的方法引用
        var className = invokeMethodRef.getDeclaringClass().getName();
        var methodName = invokeMethodRef.getName();
        if (className.equals("benchmark.internal.Benchmark")
            || className.equals("benchmark.internal.BenchmarkN")) {
            if (methodName.equals("test") || methodName.equals("alloc")) {
                System.out.println("进入了alloc和test");
                return;
            }
        }

        System.out.println(stmt);
        if (receiver != null) {
            System.out.println("receiver = " + receiver);
        }

        var invokeArgs = invokeExp.getArgs(); // 获取调用的参数

        // 现在面对invokeinterface, 必须要找到这个方法的具体实现
        if (invokeExp instanceof InvokeInterface) {
            // Java 1.4 中接口一定是属于这个类的
            // 而不能实现静态方法
            var obj = ((InvokeInterface) invokeExp).getBase();
            // 只找到那些满足这个接口的

        }

        var resolvedMethod = invokeMethodRef.resolve(); // 返回the concrete class member pointed by this reference

        var methodIR = resolvedMethod.getIR();

        // 把这段函数也要加入进来
        if (!allMethods.contains(resolvedMethod)) {
            methodWorklist.add(resolvedMethod);
            System.out.println(methodWorklist);
        }

        var isStatic = invokeMethodRef.isStatic(); // 静态的话就不用加o.f()这样
        System.out.println(invokeMethodRef);
        System.out.println(resolvedMethod);
        logger.info(resolvedMethod.toString());

        System.out.println("receiver " + receiver + " ret " + methodIR.getReturnVars());

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
        logger.info("\n\n\n");
        logger.info(stmt.getLValue());
        logger.info(stmt.getRValue());
        logger.info("\n\n\n");
        if (stmt instanceof StoreField) {
            // 是一个o.f = a; 这样的语句
            // \forall x \in o, x.f \supset a
            var lvalue = stmt.getFieldAccess();
            var rvalue = stmt.getRValue();
            if (lvalue instanceof InstanceFieldAccess) {
                addFldStoreConstraint(lvalue, rvalue);
            } else if (lvalue instanceof StaticFieldAccess) {
                addCopyConstraint(lvalue, rvalue);
            }
        } else if (stmt instanceof LoadField) {
            // 是一个 x = o.f; 这样的结构
            var lvalue = stmt.getLValue();
            var rvalue = stmt.getFieldAccess();
            if (rvalue instanceof InstanceFieldAccess) {
                addFldLoadConstraint(lvalue, rvalue);
            } else if (rvalue instanceof StaticFieldAccess) {
                addCopyConstraint(lvalue, rvalue);
            }
        }
        logger.info(fldPointsTo);
        logger.info("\n\n\n");
    }

    public void handleArray(ArrayStmt<?, ?> stmt) {
        // 直接把a[i]视为一个Var
        // 一个数组的下标实际上是不知道的?
        // 首先实现了数组粉碎
        if (stmt instanceof LoadArray) {
            // x = a[...]
            var lvalue = stmt.getLValue();
            var rvalue = stmt.getArrayAccess();
            addArrayLoadConstraint(lvalue, rvalue);
            logger.info("\n\n\n");
            logger.info("LoadArray: {} = {}", lvalue, rvalue);
            logger.info("arrayPointsTo: {}", arrayPointsTo);
            logger.info("arrayPointsLocs: {}", arrayPointsLocs);
            logger.info("varPointsTo: {}", varPointsTo);
            logger.info("varPointsLocs: {}", varPointsLocs);
            logger.info("\n\n\n");
            // 那么就把 a 里面的所有的东西都放进 x里
            // addCopyConstraint(lvalue, robj);
            /*
            varPointsTo.computeIfAbsent(robj, k -> new HashSet<>()).forEach(pt -> {
                var arrayPt = ArrayAccessFactory.getInstance((Var) pt, rindex);
                addCopyConstraint(lvalue, arrayPt);
            });*/
        } else if (stmt instanceof StoreArray) {
            // a[...] = x;
            var lvalue = stmt.getArrayAccess();
            var rvalue = stmt.getRValue();
            addArrayStoreConstraint(lvalue, rvalue);
            logger.info("\n\n\n");
            logger.info("StoreArray: {} = {}", lvalue, rvalue);
            logger.info("arrayPointsTo: {}", arrayPointsTo);
            logger.info("arrayPointsLocs: {}", arrayPointsLocs);
            logger.info("varPointsTo: {}", varPointsTo);
            logger.info("varPointsLocs: {}", varPointsLocs);
            logger.info("\n\n\n");
            // addCopyConstraint(lobj, rvalue);
            /*
            varPointsTo.computeIfAbsent(lobj, k -> new HashSet<>()).forEach(pt -> {
                var arrayPt = ArrayAccessFactory.getInstance((Var) pt, lindex);
                addCopyConstraint(arrayPt, rvalue);
            });*/
        }
    }

    /**
     * 处理Throw语句
     * @param stmt 要处理的语句
     */
    public void handleThrow(Throw stmt) {
        // 我们现在是流非敏感的, 似乎不太能解决这个问题.
        // 暂时先不处理.
    }

    public void handleCatch(Catch stmt) {
        // 先不处理
    }
}
