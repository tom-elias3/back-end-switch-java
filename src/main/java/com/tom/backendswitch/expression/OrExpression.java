package com.tom.backendswitch.expression;

public class OrExpression extends Expression {

    public OrExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate() {
        return left.evaluate() || right.evaluate();
    }
}
