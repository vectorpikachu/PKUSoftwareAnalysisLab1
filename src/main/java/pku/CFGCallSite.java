package pku;

import pascal.taie.ir.stmt.Stmt;

public class CFGCallSite implements CFGNode {
    public Stmt callSite;

    CFGCallSite(Stmt callSite) {
        this.callSite = callSite;
    }

    public Stmt getCallSite() {
        return this.callSite;
    }

    public void setCallSite(Stmt callSite) {
        this.callSite = callSite;
    }

}
