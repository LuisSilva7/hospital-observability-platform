package pt.uminho.hop.rules.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rule_evaluation")
public class RuleEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private OffsetDateTime triggeredAt = OffsetDateTime.now();

    @Column(name = "log_event_id")
    private UUID logEventId;

    private String details;

    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public UUID getLogEventId() { return logEventId; }
    public void setLogEventId(UUID logEventId) { this.logEventId = logEventId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
