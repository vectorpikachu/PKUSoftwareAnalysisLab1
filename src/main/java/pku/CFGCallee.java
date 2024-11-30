package pku;

import pascal.taie.language.classes.JMethod;

public class CFGCallee implements CFGNode {
    public JMethod callee;

    public CFGCallee(JMethod callee) {
        this.callee = callee;
    }

    public JMethod getCallee() {
        return callee;
    }

    public void setCallee(JMethod callee) {
        this.callee = callee;
    }
}
