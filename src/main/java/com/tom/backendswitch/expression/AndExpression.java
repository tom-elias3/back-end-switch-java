package com.tom.backendswitch.expression;

public class AndExpression extends Expression {

    public AndExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate() {
        return left.evaluate() && right.evaluate();
    }
}
