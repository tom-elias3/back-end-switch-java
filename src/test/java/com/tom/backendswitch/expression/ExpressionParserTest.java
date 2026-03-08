package com.tom.backendswitch.expression;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionParserTest {

    // --- comparison operators ---

    @Test void equalsTrue()          { assertThat(eval("{x} == hello", str())).isTrue(); }
    @Test void equalsFalse()         { assertThat(eval("{x} == world",  str())).isFalse(); }
    @Test void notEqualsTrue()       { assertThat(eval("{x} != world",  str())).isTrue(); }
    @Test void notEqualsFalse()      { assertThat(eval("{x} != hello",  str())).isFalse(); }
    @Test void lessThanTrue()        { assertThat(eval("{a} < 10",      num())).isTrue(); }
    @Test void lessThanFalse()       { assertThat(eval("{a} < 3",       num())).isFalse(); }
    @Test void lessThanOrEqTrue()    { assertThat(eval("{a} <= 5",      num())).isTrue(); }
    @Test void lessThanOrEqFalse()   { assertThat(eval("{a} <= 4",      num())).isFalse(); }
    @Test void greaterThanTrue()     { assertThat(eval("{a} > 3",       num())).isTrue(); }
    @Test void greaterThanFalse()    { assertThat(eval("{a} > 10",      num())).isFalse(); }
    @Test void greaterThanOrEqTrue() { assertThat(eval("{a} >= 5",      num())).isTrue(); }
    @Test void greaterThanOrEqFalse(){ assertThat(eval("{a} >= 6",      num())).isFalse(); }

    // --- logical operators ---

    @Test void andBothTrue()         { assertThat(eval("({a} == 5) AND ({b} == 3)", num())).isTrue(); }
    @Test void andOneFalse()         { assertThat(eval("({a} == 5) AND ({b} == 9)", num())).isFalse(); }
    @Test void andBothFalse()        { assertThat(eval("({a} == 9) AND ({b} == 9)", num())).isFalse(); }
    @Test void orBothTrue()          { assertThat(eval("({a} == 5) OR ({b} == 3)",  num())).isTrue(); }
    @Test void orOneTrue()           { assertThat(eval("({a} == 5) OR ({b} == 9)",  num())).isTrue(); }
    @Test void orBothFalse()         { assertThat(eval("({a} == 9) OR ({b} == 9)",  num())).isFalse(); }
    @Test void notTrue()             { assertThat(eval("NOT ({a} == 9)",            num())).isTrue(); }
    @Test void notFalse()            { assertThat(eval("NOT ({a} == 5)",            num())).isFalse(); }

    // --- parentheses ---

    @Test
    void outerParenthesesUnwrapped() {
        assertThat(eval("({a} == 5)", num())).isTrue();
    }

    @Test
    void nestedParenthesesUnwrapped() {
        assertThat(eval("(({a} == 5))", num())).isTrue();
    }

    @Test
    void parenthesesControlPrecedence() {
        // Without parentheses AND binds tighter, but here OR is nested so it evaluates first
        // ({a}==9 OR {a}==5) AND {b}==3  → (false OR true) AND true → true
        assertThat(eval("(({a} == 9) OR ({a} == 5)) AND ({b} == 3)", num())).isTrue();
    }

    @Test
    void andInsideParenthesesNotTopLevel() {
        // The AND inside the parens should not be treated as the top-level split
        // Top-level OR should split: (left AND right) OR something
        assertThat(eval("(({a} == 9) AND ({b} == 9)) OR ({a} == 5)", num())).isTrue();
    }

    // --- context resolution ---

    @Test
    void literalValueUsedDirectly() {
        assertThat(eval("hello == hello", Map.of())).isTrue();
    }

    @Test
    void contextVariableResolved() {
        assertThat(eval("{key} == value", Map.of("key", "value"))).isTrue();
    }

    @Test
    void missingContextKeyThrowsRuntimeException() {
        assertThatThrownBy(() -> eval("{missing} == value", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing");
    }

    // --- compound ---

    @Test
    void complexNestedExpression() {
        // (a==5 AND b==3) OR (a==9 AND b==9) → (true AND true) OR (false AND false) → true
        assertThat(eval("(({a} == 5) AND ({b} == 3)) OR (({a} == 9) AND ({b} == 9))", num())).isTrue();
    }

    @Test
    void notWithComparisonExpression() {
        assertThat(eval("NOT ({a} > 10)", num())).isTrue();
    }

    @Test
    void numericComparisonWithDecimal() {
        assertThat(eval("{a} > 4.9", Map.of("a", "5.0"))).isTrue();
    }

    // --- error cases ---

    @Test
    void numericOperatorWithNonNumericValueThrows() {
        assertThatThrownBy(() -> eval("{x} > 3", str()))
                .isInstanceOf(RuntimeException.class);
    }

    // --- helpers ---

    private boolean eval(String expression, Map<String, String> context) {
        return ExpressionParser.parse(expression, context).evaluate();
    }

    /** Context with string values. x=hello, y=world */
    private Map<String, String> str() {
        return Map.of("x", "hello", "y", "world");
    }

    /** Context with numeric values. a=5, b=3 */
    private Map<String, String> num() {
        return Map.of("a", "5", "b", "3");
    }
}
