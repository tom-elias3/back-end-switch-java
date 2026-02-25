package com.tom.backendswitch.expression;

public abstract class Expression {

    protected Expression left;
    protected Expression right;

    public abstract boolean evaluate();

    public String getValue() {
        return null;
    }
}
