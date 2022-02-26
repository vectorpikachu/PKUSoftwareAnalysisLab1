/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.ir.exp;

import pascal.taie.World;
import pascal.taie.language.type.ClassType;

import static pascal.taie.language.classes.ClassNames.STRING;

public class StringLiteral implements ReferenceLiteral {

    private final String value;

    private StringLiteral(String value) {
        this.value = value;
    }

    public static StringLiteral get(String value) {
        // TODO: canonicalize?
        return new StringLiteral(value);
    }

    @Override
    public ClassType getType() {
        // TODO: cache String type in a static field? Doing so
        //  requires to reset the field when resetting World.
        return World.get().getTypeSystem().getClassType(STRING);
    }

    public String getString() {
        return value;
    }

    @Override
    public <T> T accept(ExpVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StringLiteral that = (StringLiteral) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}
