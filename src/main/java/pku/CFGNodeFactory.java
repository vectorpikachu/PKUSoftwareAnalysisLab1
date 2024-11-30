package pku;

import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import soot.xml.Key;

import java.util.HashMap;
import java.util.Map;

public class CFGNodeFactory {
    private static final Map<KeyCallSite, CFGNode> cacheCallSite = new HashMap<>();

    private static final Map<KeyCallee, CFGNode> cacheCallee = new HashMap<>();

    public static CFGNode getInstance(JMethod method) {
        KeyCallee keyCallee = new KeyCallee(method);
        return cacheCallee.computeIfAbsent(keyCallee, __ -> new CFGCallee(method));
    }

    public static CFGNode getInstance(Stmt stmt) {
        KeyCallSite keyCallSite = new KeyCallSite(stmt);
        return cacheCallSite.computeIfAbsent(keyCallSite, __ -> new CFGCallSite(stmt));
    }

    private record KeyCallSite(Stmt stmt) {}

    private record KeyCallee(JMethod method) {}
}
