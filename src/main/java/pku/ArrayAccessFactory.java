package pku;

import pascal.taie.ir.exp.*;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;

import java.util.HashMap;
import java.util.Map;

public class ArrayAccessFactory {
    private static final Map<Key, ArrayAccess> cache = new HashMap<>();

    private static final Map<KeyIndex, Var> cacheIndex = new HashMap<>();
    /**
     * 直接不考虑 index 里具体是什么
     * @param base 数组名
     * @param index 数组下标 , 一般是一个temp${id}
     * @return ArrayAccess 类型的
     */
    public static ArrayAccess getInstance(Var base, Var index, Literal literal) {
        Number literalIdx = -1;
        if (literal instanceof NumberLiteral) {
            literalIdx = ((NumberLiteral) literal).getNumber();
            if (literalIdx.longValue() > 5) literalIdx = -1;
        }
        KeyIndex keyIndex = new KeyIndex(index.getMethod(), "i", index.getType(), literalIdx);
        var thisIndex = cacheIndex.computeIfAbsent(keyIndex, k -> new Var(
                index.getMethod(), "i", index.getType(), index.getIndex(), literal
        ));
        Key key = new Key(base, thisIndex);
        return cache.computeIfAbsent(key, k -> new ArrayAccess(base, thisIndex));
    }

    private record Key(Var base, Var index) {}

    private record KeyIndex(JMethod method, String name, Type type, Number literalIdx) {}
}
