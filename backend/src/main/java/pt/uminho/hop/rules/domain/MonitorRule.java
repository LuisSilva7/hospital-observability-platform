package pt.uminho.hop.rules.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "monitor_rule")
public class MonitorRule {

    public enum Type { EVENT_MATCH, NO_ACTIVITY, COUNT_THRESHOLD, ANOMALY }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "window_minutes")
    private Integer windowMinutes;

    private Integer threshold;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 10;

    @Column(name = "last_triggered_at")
    private OffsetDateTime lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RuleCondition> conditions = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Integer getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(Integer windowMinutes) { this.windowMinutes = windowMinutes; }
    public Integer getThreshold() { return threshold; }
    public void setThreshold(Integer threshold) { this.threshold = threshold; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    public OffsetDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(OffsetDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<RuleCondition> getConditions() { return conditions; }
}
