package pt.uminho.hop.services.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "service")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Criticality criticality;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "expected_interval_minutes")
    private Integer expectedIntervalMinutes;

    @Column(name = "tolerance_minutes")
    private Integer toleranceMinutes;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public enum Environment { SIMULATION, DEVELOPMENT, STAGING, PRODUCTION }

    public enum Criticality { LOW, MEDIUM, HIGH, CRITICAL }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public Criticality getCriticality() { return criticality; }
    public void setCriticality(Criticality criticality) { this.criticality = criticality; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Integer getExpectedIntervalMinutes() { return expectedIntervalMinutes; }
    public void setExpectedIntervalMinutes(Integer v) { this.expectedIntervalMinutes = v; }
    public Integer getToleranceMinutes() { return toleranceMinutes; }
    public void setToleranceMinutes(Integer v) { this.toleranceMinutes = v; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
