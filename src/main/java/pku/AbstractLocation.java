package pku;

import pascal.taie.ir.stmt.New;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 根据Jonathan Aldrich的Lecture Notes: Pointer Analysis
 * AbstractLocation: His analysis associates each variable p with
 * an abstract location named after the variable.
 * upd: 最新的方法是Marc Shapiro and Susan Horwitz的
 * Fast and accurate flow-insensitive points-to analysis.
 */
public class AbstractLocation {
    public ArrayList<AbstractLocation> parents;
    public static int maxOutDegree = 3;
    public int category; // 当前集合的种类
    public HashSet<New> realNewStmts;

    public AbstractLocation(int category) {
        this.parents = new ArrayList<>();
        // this.parents.set(0, this); // 一开始, 都指向自己
        realNewStmts = new HashSet<>();
        this.category = category;
    }

    public void addNewStmt(New stmt) {
        realNewStmts.add(stmt);
    }

    /**
     * 找到当前集合的根
     * @return 当前集合的根
     */

    /*
    public AbstractLocation find() {
        if (parent != this) {
            parent = parent.find(); // 路径压缩
        }
        return parent;
    }*/

    /**
     * 合并集合x的指向
     * @param x 要合并指向的集合
     */
    public static void conditionalUnion(AbstractLocation x) {
        if (x.parents.size() <= maxOutDegree) return;
        var group = new HashMap<Integer, ArrayList<AbstractLocation> >();
        var tempParents = new ArrayList<AbstractLocation>();
        for (var pt : x.parents) {
            group.get(pt.category).add(pt);
        }
        group.forEach((category, absLocs)->{
            if (absLocs.size()>1) {
                AbstractLocation tempLoc = new AbstractLocation(category);
                absLocs.forEach(absLoc->{
                    tempLoc.parents.addAll(absLoc.parents);
                    tempLoc.realNewStmts.addAll(absLoc.realNewStmts);
                });
                tempParents.add(tempLoc);
                conditionalUnion(tempLoc);
            }
            else {
                tempParents.add(absLocs.get(0));
            }
        });
        x.parents = tempParents;
    }
}
