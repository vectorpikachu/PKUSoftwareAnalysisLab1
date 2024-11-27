package pku;

import java.util.Map;
import java.util.HashMap;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.FieldRef;

public class InstanceFieldAccessFactory {
    private static final Map<Key, InstanceFieldAccess> cache = new HashMap<>();

    public static InstanceFieldAccess getInstance(FieldRef fieldRef, Var pt) {
        Key key = new Key(fieldRef, pt);
        return cache.computeIfAbsent(key, k -> new InstanceFieldAccess(fieldRef, pt));
    }

    private record Key(FieldRef fieldRef, Var pt) { }
}
