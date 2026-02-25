package com.tom.backendswitch.expression;

public class EqualsExpression extends Expression {

    public EqualsExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate() {
        return left.getValue().equals(right.getValue());
    }
}
