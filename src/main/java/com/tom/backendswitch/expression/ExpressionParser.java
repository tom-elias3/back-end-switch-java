package com.tom.backendswitch.expression;

import java.util.Map;

public class ExpressionParser {

    public static Expression parse(String expression, Map<String, String> context) {
        String expressionTrim = expression.trim();

        if (isWrappedInParentheses(expressionTrim)) {
            return parse(expressionTrim.substring(1, expressionTrim.length() - 1), context);
        }

        // look for AND expression and split //
        int idx = findTopLevelLogicalOp(expressionTrim, "AND");
        if (idx != -1) {
            return new AndExpression(
                parse(expressionTrim.substring(0, idx), context),
                parse(expressionTrim.substring(idx + 3), context)
            );
        }

        // look for OR expression and split //
        idx = findTopLevelLogicalOp(expressionTrim, "OR");
        if (idx != -1) {
            return new OrExpression(
                parse(expressionTrim.substring(0, idx), context),
                parse(expressionTrim.substring(idx + 2), context)
            );
        }

        // look for NOT expression
        if (expressionTrim.startsWith("NOT ")) {
            return new NotExpression(parse(expressionTrim.substring(4), context));
        }

        // Order matters: check multi-char operators before single-char ones
        for (String op : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
            idx = expressionTrim.indexOf(op);
            if (idx != -1) {
                return createComparison(op,
                    new ValueExpression(resolveFromContext(expressionTrim.substring(0, idx).trim(), context)),
                    new ValueExpression(resolveFromContext(expressionTrim.substring(idx + op.length()).trim(), context))
                );
            }
        }

        return new ValueExpression(resolveFromContext(expressionTrim, context));
    }

    private static boolean isWrappedInParentheses(String expression) {
        if (expression.isEmpty() || expression.charAt(0) != '(') return false;
        int depth = 0;

        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) == '(') depth++;
            else if (expression.charAt(i) == ')') depth--;
            if (depth == 0) return i == expression.length() - 1;
        }
        return false;
    }

    private static int findTopLevelLogicalOp(String expression, String op) {
        int depth = 0;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && expression.startsWith(op, i)) {
                boolean validBefore = i == 0 || expression.charAt(i - 1) == ' ';
                boolean validAfter = i + op.length() >= expression.length() || expression.charAt(i + op.length()) == ' ';
                if (validBefore && validAfter) return i;
            }
        }
        return -1;
    }

    private static String resolveFromContext(String token, Map<String, String> context) {
        if (token.startsWith("{") && token.endsWith("}")) {
            String key = token.substring(1, token.length() - 1);

            if(!context.containsKey(key)) throw new RuntimeException("Could not find key in context: " + token);
            return context.get(key);
        }
        return token;
    }

    private static Expression createComparison(String op, Expression left, Expression right) {
        return switch (op) {
            case "<=" -> new LessThanOrEqualsExpression(left, right);
            case ">=" -> new GreaterThanOrEqualsExpression(left, right);
            case "==" -> new EqualsExpression(left, right);
            case "!=" -> new NotEqualsExpression(left, right);
            case "<"  -> new LessThanExpression(left, right);
            case ">"  -> new GreaterThanExpression(left, right);

            default   -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
    }
}
