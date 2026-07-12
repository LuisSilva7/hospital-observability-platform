package pt.uminho.hop.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pt.uminho.hop.rules.domain.RuleCondition;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionMatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private RuleCondition condition(String field, RuleCondition.Operator op, String expected) {
        var c = new RuleCondition();
        c.setFieldPath(field);
        c.setOperator(op);
        c.setExpectedValue(expected);
        return c;
    }

    private JsonNode payload() throws Exception {
        return mapper.readTree("""
                {"level":"ERROR","message":"Failed to send result",
                 "responseTimeMs":3200,"data":{"code":"EHR_TIMEOUT"}}
                """);
    }

    @Test
    void equalsIsCaseInsensitive() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("level", RuleCondition.Operator.EQUALS, "error"), payload())).isTrue();
        assertThat(ConditionMatcher.matches(
                condition("level", RuleCondition.Operator.EQUALS, "WARN"), payload())).isFalse();
    }

    @Test
    void notEqualsMatchesDifferentAndMissing() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("level", RuleCondition.Operator.NOT_EQUALS, "WARN"), payload())).isTrue();
        assertThat(ConditionMatcher.matches(
                condition("naoExiste", RuleCondition.Operator.NOT_EQUALS, "x"), payload())).isTrue();
    }

    @Test
    void containsSearchesSubstring() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("message", RuleCondition.Operator.CONTAINS, "failed"), payload())).isTrue();
        assertThat(ConditionMatcher.matches(
                condition("message", RuleCondition.Operator.CONTAINS, "success"), payload())).isFalse();
    }

    @Test
    void numericComparisons() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("responseTimeMs", RuleCondition.Operator.GREATER_THAN, "3000"), payload())).isTrue();
        assertThat(ConditionMatcher.matches(
                condition("responseTimeMs", RuleCondition.Operator.LESS_THAN, "3000"), payload())).isFalse();
        // não numérico nunca satisfaz > ou <
        assertThat(ConditionMatcher.matches(
                condition("message", RuleCondition.Operator.GREATER_THAN, "10"), payload())).isFalse();
    }

    @Test
    void nestedFieldPath() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("data.code", RuleCondition.Operator.EQUALS, "EHR_TIMEOUT"), payload())).isTrue();
    }

    @Test
    void missingFieldOnlySatisfiesNotEquals() throws Exception {
        assertThat(ConditionMatcher.matches(
                condition("naoExiste", RuleCondition.Operator.EQUALS, "x"), payload())).isFalse();
        assertThat(ConditionMatcher.matches(
                condition("naoExiste", RuleCondition.Operator.CONTAINS, "x"), payload())).isFalse();
    }
}
