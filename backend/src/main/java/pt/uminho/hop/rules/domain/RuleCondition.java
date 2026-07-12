package pt.uminho.hop.rules.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "rule_condition")
public class RuleCondition {

    public enum Operator { EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private MonitorRule rule;

    @Column(name = "field_path", nullable = false, length = 200)
    private String fieldPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Operator operator;

    @Column(name = "expected_value", nullable = false, length = 500)
    private String expectedValue;

    public UUID getId() { return id; }
    public MonitorRule getRule() { return rule; }
    public void setRule(MonitorRule rule) { this.rule = rule; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
}
