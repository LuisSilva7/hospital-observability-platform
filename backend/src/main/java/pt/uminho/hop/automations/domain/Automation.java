package pt.uminho.hop.automations.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "automation")
public class Automation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "automation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orderIndex ASC")
    private List<AutomationAction> actions = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<AutomationAction> getActions() { return actions; }
}
