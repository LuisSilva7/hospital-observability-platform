package pt.uminho.hop.rules;

import com.fasterxml.jackson.databind.JsonNode;
import pt.uminho.hop.rules.domain.RuleCondition;

/**
 * Avalia uma condição (fieldPath + operador + valor esperado) contra o payload
 * JSON de um log. fieldPath suporta caminhos aninhados com "." (ex.: "data.code").
 * Comparações GREATER_THAN/LESS_THAN são numéricas quando ambos os lados o são.
 */
public final class ConditionMatcher {

    private ConditionMatcher() {}

    public static boolean matches(RuleCondition condition, JsonNode payload) {
        JsonNode value = resolve(payload, condition.getFieldPath());
        if (value == null || value.isNull() || value.isMissingNode()) {
            // campo ausente só satisfaz NOT_EQUALS
            return condition.getOperator() == RuleCondition.Operator.NOT_EQUALS;
        }
        String actual = value.asText();
        String expected = condition.getExpectedValue();

        return switch (condition.getOperator()) {
            case EQUALS -> actual.equalsIgnoreCase(expected);
            case NOT_EQUALS -> !actual.equalsIgnoreCase(expected);
            case CONTAINS -> actual.toLowerCase().contains(expected.toLowerCase());
            case GREATER_THAN -> compareNumeric(actual, expected) > 0;
            case LESS_THAN -> compareNumeric(actual, expected) < 0;
        };
    }

    private static JsonNode resolve(JsonNode payload, String fieldPath) {
        JsonNode current = payload;
        for (String part : fieldPath.split("\\.")) {
            if (current == null) return null;
            current = current.get(part);
        }
        return current;
    }

    /** Devolve 0 em caso de valores não numéricos (nunca satisfaz > ou <). */
    private static int compareNumeric(String actual, String expected) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(expected));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
