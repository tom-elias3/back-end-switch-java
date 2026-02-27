package com.tom.backendswitch.expression;

public class NotExpression extends Expression {

    public NotExpression(Expression operand) {
        this.left = operand;
    }

    @Override
    public boolean evaluate() {
        return !left.evaluate();
    }
}
