package com.tom.backendswitch.expression;

public class ValueExpression extends Expression {

    private final String value;

    public ValueExpression(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean evaluate() {
        throw new UnsupportedOperationException("ValueExpression is a leaf node and cannot be evaluated as boolean");
    }
}
