package pku;

import pascal.taie.ir.stmt.New;

import java.util.HashSet;

/**
 * 根据Jonathan Aldrich的Lecture Notes: Pointer Analysis
 * AbstractLocation: His analysis associates each variable p with
 * an abstract location named after the variable.
 */
public class AbstractLocation {
    public AbstractLocation parent;

    public HashSet<New> realNewStmts;

    public AbstractLocation() {
        this.parent = this; // 一开始, 都指向自己
        realNewStmts = new HashSet<>();
    }

    public void addNewStmt(New stmt) {
        realNewStmts.add(stmt);
    }

    /**
     * 找到当前集合的根
     * @return 当前集合的根
     */
    public AbstractLocation find() {
        if (parent != this) {
            parent = parent.find(); // 路径压缩
        }
        return parent;
    }

    /**
     * 合并两个集合, 优先把左边的合并到右边
     * @param x 左边的集合
     * @param y 右边的集合
     */
    public static void union(AbstractLocation x, AbstractLocation y) {
        AbstractLocation xRoot = x.find();
        AbstractLocation yRoot = y.find();
        if (xRoot != yRoot) {
            yRoot.realNewStmts.addAll(xRoot.realNewStmts);
            xRoot.parent = yRoot; // Merge the two sets
        }
    }
}
