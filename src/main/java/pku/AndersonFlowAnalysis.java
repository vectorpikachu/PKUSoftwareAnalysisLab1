package pku;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dataflow.analysis.AnalysisDriver;
import pascal.taie.analysis.dataflow.fact.MapFact;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.misc.IRDumper;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * 实现流敏感的分析, 之前是流非敏感的
 * 这是一个失败的常识
 * 没有Debug出来, 请先不要使用它
 */
public class AndersonFlowAnalysis {

    private static final Logger logger = LogManager.getLogger(IRDumper.class);

    public PreprocessResult preprocess; // 预处理结果

    public JMethod method; // 当前分析的方法

    public CFGBuilder cfgBuilder; // 这个方法的控制流图构建工具

    public CFG<Stmt> cfg; // 这个方法的控制流图

    public final List<JMethod> callStack = new ArrayList<>(); // 当前分析的方法调用栈, Context 展开的层次

    public HashMap<Stmt, HashMap<Exp, SetFact<Stmt>>> allPointsTo; // 所有指针指向的集合

    public HashSet<Stmt> toVisit;

    public PointerAnalysisResult result;

    public static String[] basicTypes = {"int", "boolean", "char", "byte", "short", "long", "float", "double"};

    /**
     * 当前开始分析函数
     * @param method 当前分析的方法
     * @param preprocess 预处理结果
     */
    public AndersonFlowAnalysis(JMethod method, PreprocessResult preprocess,
                                CFGBuilder cfgBuilder, List<JMethod> prevCallStack) {
        this.method = method;
        this.preprocess = preprocess;
        this.cfgBuilder = cfgBuilder;
        this.cfg = cfgBuilder.analyze(method.getIR());
        logger.info(cfg);
        this.callStack.addAll(prevCallStack);
        this.allPointsTo = new HashMap<>(); // 初始都指向空
        this.toVisit = new HashSet<>();
        var ir = method.getIR();
        preprocess.analysis(ir);
        logger.info("Start Analyzing method {}", method);
    }

    /**
     * 获取分析结果
     * @return 流敏感的分析结果
     */
    public PointerAnalysisResult getResult() {
        if (PointerAnalysis.exceedsTimeLimit()) {
            logger.warn("Exceeds time limit, stop analysis");
            return new PointerAnalysisResult();
        }
        if(callStack.size() > 3) {
            return null; // 防止递归的过于深
        }
        // 分析控制流图
        var entryStmt = cfg.getEntry();
        allPointsTo.computeIfAbsent(entryStmt, __ -> new HashMap<>()); // OUT_entry = {}

        toVisit.addAll(cfg.getSuccsOf(entryStmt));
        while (toVisit.size() > 0) {
            var stmt = toVisit.iterator().next();
            toVisit.remove(stmt);
            var preds = cfg.getPredsOf(stmt);
            var in = allPointsTo.get(stmt);
            var out = new HashMap<Exp, SetFact<Stmt>>();
            for (var pred : preds) {
                var predOut = allPointsTo.get(pred);
                if (predOut != null) {
                    predOut.forEach((k, v) -> {
                        out.computeIfAbsent(k, __ -> new SetFact<Stmt>(new HashSet<>())).union(v);
                    });
                }
            }
            var newOut = transfer(stmt, out);
            if (!newOut.equals(in)) {
                allPointsTo.put(stmt, newOut);
                toVisit.addAll(cfg.getSuccsOf(stmt));
            }
        }

        return result;
    }

    /**
     * 转移函数
     * @param stmt 当前分析的语句
     * @param in 当前语句的入口
     * @return 当前语句的出口
     */
    public HashMap<Exp, SetFact<Stmt>> transfer(Stmt stmt, HashMap<Exp, SetFact<Stmt>> in) {
        var out = new HashMap<Exp, SetFact<Stmt>>();
        if (stmt instanceof AssignStmt<?,?>) {
            if (stmt instanceof New) {
                out.computeIfAbsent(((New) stmt).getLValue(), __ -> new SetFact<Stmt>(new HashSet<>())).add(stmt);
            } else if (stmt instanceof FieldStmt<?,?>) {
                // Do Nothing
            } else if (stmt instanceof ArrayStmt<?,?>) {
                // Do Nothing
            }

            var lvalue = ((AssignStmt<?, ?>) stmt).getLValue();
            var rvalue = ((AssignStmt<?, ?>) stmt).getRValue();

            if (isBasicType(lvalue.getType()) || isBasicType(rvalue.getType())) {
                return in;
            }

            // 把右值的指向赋给左值
            if (rvalue instanceof Var) {
                var rvalueExp = (Var) rvalue;
                var rvaluePointsTo = in.get(rvalueExp);
                if (rvaluePointsTo != null) {
                    out.put(lvalue, rvaluePointsTo); // 直接赋值
                }
            } else if (rvalue instanceof FieldAccess) {
                out.put(lvalue, in.get(rvalue));
            } else if (rvalue instanceof ArrayAccess) {
                out.put(lvalue, in.get(rvalue));
            }

        } else if (stmt instanceof Invoke) {
            // 调用函数
            var invokeExp = ((Invoke) stmt).getInvokeExp();
            if (invokeExp instanceof InvokeSpecial) {
                // 一些奇怪的比如 init, super等等
                return in;
            }

            var receiver = ((Invoke) stmt).getResult(); // 左边接受调用

            var invokeMethodRef = invokeExp.getMethodRef(); // 获取调用的方法引用
            var className = invokeMethodRef.getDeclaringClass().getName();
            if (className.startsWith("benchmark.internal.Benchmark")
                    || className.startsWith("benchmark.internal.BenchmarkN")) {
                return in;
            }
            // 新建一个分析器
            var invokeMethod = invokeMethodRef.resolve();
            var newAnalysis = new AndersonFlowAnalysis(invokeMethod, preprocess, cfgBuilder, callStack);
            // 给参数赋值
            var entryStmt = newAnalysis.cfg.getEntry();
            var args = invokeExp.getArgs();
            for (int i = 0; i < args.size(); i++) {
                var arg = args.get(i);
                if (isBasicType(arg.getType())) {
                    continue;
                }
                var argPointsTo = in.get(arg);
                if (argPointsTo != null) {
                    newAnalysis.allPointsTo.put(entryStmt, new HashMap<>());
                    newAnalysis.allPointsTo.get(entryStmt).put(arg, argPointsTo);
                }
            }
            newAnalysis.callStack.add(this.method);
            var newResult = newAnalysis.getResult();
            // 把结果合并到当前的结果中
            newResult.forEach((test_id, ptResult) -> {
                result.put(test_id, ptResult);
            });
            // 把返回值的指向加入当前的指向
            var invokeMethodRet = newAnalysis.cfg.getExit();
            var retPt = newAnalysis.allPointsTo.get(invokeMethodRet);
            System.out.println(invokeMethodRet);
        }
        return out;
    }

    public boolean isBasicType(Type tp) {
        for (var basicType : basicTypes) {
            if (tp.getName().startsWith(basicType)) {
                return true;
            }
        }
        return false;
    }

}
