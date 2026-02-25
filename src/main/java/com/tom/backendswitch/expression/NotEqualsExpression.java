package com.tom.backendswitch.expression;

public class NotEqualsExpression extends Expression {

    public NotEqualsExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate() {
        return !left.getValue().equals(right.getValue());
    }
}