package pku;


import java.util.HashMap;

import pascal.taie.ir.IR;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.InvokeStatic;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Stmt;

import java.util.Map;

public class PreprocessResult {

    public final Map<New, Integer> obj_ids;
    public final Map<Integer, Var> test_pts;
    public final Map<Integer, Stmt> test_stmts;

    public PreprocessResult(){
        obj_ids = new HashMap<New, Integer>();
        test_pts = new HashMap<Integer,Var>();
        test_stmts = new HashMap<>();
    }

    /**
     * Benchmark.alloc(id);
     * X x = new X;// stmt
     * @param stmt statement that allocates a new object
     * @param id id of the object allocated
     */
    public void alloc(New stmt, int id)
    {
        obj_ids.put(stmt, id);
    }
    /**
     * Benchmark.test(id, var)
     * @param id id of the testing
     * @param v the pointer/variable
     */
    public void test(int id, Var v)
    {
        test_pts.put(id, v);
    }
    /**
     *
     * @param stmt statement that allocates a new object
     * @return id of the object allocated
     */
    public int getObjIdAt(New stmt)
    {
        return obj_ids.get(stmt);
    }
    /**
     * @param id
     * @return the pointer/variable in Benchmark.test(id, var);
     */
    public Var getTestPt(int id)
    {
        return test_pts.get(id);
    }

    /**
     * analysis of a JMethod, the result storing in this
     * @param ir ir of a JMethod
     */
    public void analysis(IR ir) {
        var stmts = ir.getStmts();
        Integer id = 0;
        for (var stmt : stmts) {

            if(stmt instanceof Invoke)
            {
                var exp = ((Invoke) stmt).getInvokeExp();
                if(exp instanceof InvokeStatic)
                {
                    var methodRef = ((InvokeStatic)exp).getMethodRef();
                    var className = methodRef.getDeclaringClass().getName();
                    var methodName = methodRef.getName();
                    if(className.equals("benchmark.internal.Benchmark")
                    || className.equals("benchmark.internal.BenchmarkN"))
                    {
                        if(methodName.equals("alloc"))
                        {
                            var lit = exp.getArg(0).getConstValue();
                            assert lit instanceof IntLiteral;
                            id = ((IntLiteral)lit).getNumber();
                        }
                        else if(methodName.equals("test"))
                        {
                            var lit = exp.getArg(0).getConstValue();
                            assert lit instanceof IntLiteral;
                            var test_id = ((IntLiteral)lit).getNumber();
                            var pt = exp.getArg(1);
                            this.test(test_id, pt);
                            test_stmts.put(test_id, stmt);
                        }
                    }

                }
            }
            else if(stmt instanceof New)
            {
                if(id!=0) // ignore unlabeled `new` stmts
                    this.alloc((New)stmt, id);
            }
        }
    }
}
