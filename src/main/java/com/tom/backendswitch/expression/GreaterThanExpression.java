package com.tom.backendswitch.expression;

public class GreaterThanExpression extends Expression {

    public GreaterThanExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate() {
        try {
            return Double.parseDouble(left.getValue()) > Double.parseDouble(right.getValue());
        } catch(NumberFormatException formatException) {
            throw new RuntimeException("Could not parse one of: " + left.getValue() + " " + right.getValue());
        }
    }
}
