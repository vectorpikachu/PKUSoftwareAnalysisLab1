package pku;

import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.HashMap;
import java.util.Map;

public class ArrayAccessFactory {
    private static final Map<Key, ArrayAccess> cache = new HashMap<>();

    private static final Map<KeyIndex, Var> indexCache = new HashMap<>();

    /**
     * 直接不考虑 index 里具体是什么
     * @param base 数组名
     * @param index 数组下标 , 一般是一个temp${id}
     * @return ArrayAccess 类型的
     */
    public static ArrayAccess getInstance(Var base, Var index) {
        KeyIndex keyIndex = new KeyIndex(index.getMethod(), "i", index.getType(), 5);
        var thisIndex = indexCache.computeIfAbsent(keyIndex, k -> new Var(index.getMethod(), "i", index.getType(), 5));
        Key key = new Key(base, thisIndex);
        return cache.computeIfAbsent(key, k -> new ArrayAccess(base, thisIndex));
    }

    private record Key(Var base, Var index) {}

    private record KeyIndex(JMethod method, String name, Type type, int ind) {}
}
